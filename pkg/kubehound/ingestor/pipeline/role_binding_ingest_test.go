package pipeline

import (
	"context"
	"testing"

	"github.com/DataDog/KubeHound/pkg/collector"
	mockcollect "github.com/DataDog/KubeHound/pkg/collector/mockcollector"
	"github.com/DataDog/KubeHound/pkg/config"
	"github.com/DataDog/KubeHound/pkg/globals/types"
	"github.com/DataDog/KubeHound/pkg/kubehound/models/converter"
	"github.com/DataDog/KubeHound/pkg/kubehound/models/store"
	"github.com/DataDog/KubeHound/pkg/kubehound/storage/cache"
	"github.com/DataDog/KubeHound/pkg/kubehound/storage/cache/cachekey"
	mockcache "github.com/DataDog/KubeHound/pkg/kubehound/storage/cache/mocks"
	graphdb "github.com/DataDog/KubeHound/pkg/kubehound/storage/graphdb/mocks"
	storedb "github.com/DataDog/KubeHound/pkg/kubehound/storage/storedb/mocks"
	"github.com/DataDog/KubeHound/pkg/kubehound/store/collections"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/mock"
)

func TestRoleBindingIngest_Pipeline(t *testing.T) {
	ri := &RoleBindingIngest{}

	ctx := context.Background()
	fakeRb, err := loadTestObject[types.RoleBindingType]("testdata/rolebinding.json")
	assert.NoError(t, err)

	fakeRole, err := loadTestObject[types.RoleType]("testdata/role.json")
	assert.NoError(t, err)
	oFakeRole, err := converter.NewStore().Role(ctx, fakeRole)
	assert.NoError(t, err)

	client := mockcollect.NewCollectorClient(t)
	client.EXPECT().StreamRoleBindings(ctx, ri).
		RunAndReturn(func(ctx context.Context, i collector.RoleBindingIngestor) error {
			// Fake the stream of a single role binding from the collector client
			err := i.IngestRoleBinding(ctx, fakeRb)
			if err != nil {
				return err
			}

			return i.Complete(ctx)
		})

	// Cache setup
	c := mockcache.NewCacheProvider(t)

	c.EXPECT().Get(ctx, cachekey.Identity("app-monitors", "test-app")).Return(&cache.CacheResult{
		Value: nil,
		Err:   cache.ErrNoEntry,
	}).Once()

	c.EXPECT().Get(ctx, cachekey.Role("test-reader", "test-app")).Return(&cache.CacheResult{
		Value: *oFakeRole,
		Err:   nil,
	}).Twice()

	// Store setup -  rolebindings
	sdb := storedb.NewProvider(t)
	rsw := storedb.NewAsyncWriter(t)
	crbs := collections.RoleBinding{}
	rsw.EXPECT().Queue(ctx, mock.AnythingOfType("*store.RoleBinding")).Return(nil).Once()
	rsw.EXPECT().Flush(ctx).Return(nil)
	rsw.EXPECT().Close(ctx).Return(nil)
	sdb.EXPECT().BulkWriter(ctx, crbs, mock.Anything).Return(rsw, nil)

	// Store setup -  identities
	isw := storedb.NewAsyncWriter(t)
	csw := mockcache.NewAsyncWriter(t)
	csw.EXPECT().Queue(ctx, mock.AnythingOfType("*cachekey.identityCacheKey"), mock.AnythingOfType("string")).Return(nil)
	csw.EXPECT().Flush(ctx).Return(nil)
	csw.EXPECT().Close(ctx).Return(nil)

	// Store setup -  permissionsets
	pssw := storedb.NewAsyncWriter(t)
	psbs := collections.PermissionSet{}
	psStoreId := store.ObjectID()
	pssw.EXPECT().Queue(ctx, mock.AnythingOfType("*store.PermissionSet")).
		RunAndReturn(func(ctx context.Context, i any) error {
			i.(*store.PermissionSet).Id = psStoreId
			return nil
		}).Once()
	pssw.EXPECT().Flush(ctx).Return(nil)
	pssw.EXPECT().Close(ctx).Return(nil)
	sdb.EXPECT().BulkWriter(ctx, psbs, mock.Anything).Return(pssw, nil)

	identities := collections.Identity{}
	storeId := store.ObjectID()
	isw.EXPECT().Queue(ctx, mock.AnythingOfType("*store.Identity")).
		RunAndReturn(func(ctx context.Context, i any) error {
			i.(*store.Identity).Id = storeId
			return nil
		}).Once()
	isw.EXPECT().Flush(ctx).Return(nil)
	isw.EXPECT().Close(ctx).Return(nil)
	sdb.EXPECT().BulkWriter(ctx, identities, mock.Anything).Return(isw, nil)
	c.EXPECT().BulkWriter(ctx, mock.AnythingOfType("cache.WriterOption")).Return(csw, nil)

	// Graph setup
	vtxInsert := map[string]any{
		"isNamespaced": true,
		"critical":     false,
		"name":         "app-monitors",
		"namespace":    "test-app",
		"storeID":      storeId.Hex(),
		"type":         "ServiceAccount",
		"team":         "test-team",
		"app":          "test-app",
		"service":      "test-service",
	}
	gdb := graphdb.NewProvider(t)
	gw := graphdb.NewAsyncVertexWriter(t)
	gw.EXPECT().Queue(ctx, vtxInsert).Return(nil).Once()
	gw.EXPECT().Flush(ctx).Return(nil)
	gw.EXPECT().Close(ctx).Return(nil)
	gdb.EXPECT().VertexWriter(ctx, mock.AnythingOfType("*vertex.Identity"), c, mock.AnythingOfType("graphdb.WriterOption")).Return(gw, nil)

	psVtxInsert := map[string]any{
		"isNamespaced": true,
		"critical":     false,
		"name":         "test-reader::app-monitors-read",
		"role":         "test-reader",
		"roleBinding":  "app-monitors-read",
		"namespace":    "test-app",
		"storeID":      psStoreId.Hex(),
		"team":         "test-team",
		"app":          "test-app",
		"service":      "test-service",
		"rules":        []interface{}{"API()::R(pods)::N()::V(get,list)", "API()::R(configmaps)::N()::V(get)", "API(apps)::R(statefulsets)::N()::V(get,list)"},
	}

	psgw := graphdb.NewAsyncVertexWriter(t)
	psgw.EXPECT().Queue(ctx, psVtxInsert).Return(nil).Once()
	psgw.EXPECT().Flush(ctx).Return(nil)
	psgw.EXPECT().Close(ctx).Return(nil)
	gdb.EXPECT().VertexWriter(ctx, mock.AnythingOfType("*vertex.PermissionSet"), c, mock.AnythingOfType("graphdb.WriterOption")).Return(psgw, nil)

	deps := &Dependencies{
		Collector: client,
		Cache:     c,
		GraphDB:   gdb,
		StoreDB:   sdb,
		Config: &config.KubehoundConfig{
			Builder: config.BuilderConfig{
				Edge: config.EdgeBuilderConfig{},
			},
		},
	}

	// Initialize
	err = ri.Initialize(ctx, deps)
	assert.NoError(t, err)

	// Run
	err = ri.Run(ctx)
	assert.NoError(t, err)

	// Close
	err = ri.Close(ctx)
	assert.NoError(t, err)
}
