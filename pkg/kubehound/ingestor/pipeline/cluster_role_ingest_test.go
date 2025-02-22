package pipeline

import (
	"context"
	"testing"

	"github.com/DataDog/KubeHound/pkg/collector"
	mockcollect "github.com/DataDog/KubeHound/pkg/collector/mockcollector"
	"github.com/DataDog/KubeHound/pkg/config"
	"github.com/DataDog/KubeHound/pkg/globals/types"
	"github.com/DataDog/KubeHound/pkg/kubehound/models/store"
	"github.com/DataDog/KubeHound/pkg/kubehound/storage/cache/cachekey"
	cache "github.com/DataDog/KubeHound/pkg/kubehound/storage/cache/mocks"
	storedb "github.com/DataDog/KubeHound/pkg/kubehound/storage/storedb/mocks"
	"github.com/DataDog/KubeHound/pkg/kubehound/store/collections"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/mock"
)

func TestClusterRoleIngest_Pipeline(t *testing.T) {
	cri := &ClusterRoleIngest{}

	ctx := context.Background()
	fakeRole, err := loadTestObject[types.ClusterRoleType]("testdata/clusterrole.json")
	assert.NoError(t, err)

	client := mockcollect.NewCollectorClient(t)
	client.EXPECT().StreamClusterRoles(ctx, cri).
		RunAndReturn(func(ctx context.Context, i collector.ClusterRoleIngestor) error {
			// Fake the stream of a single cluster role from the collector client
			err := i.IngestClusterRole(ctx, fakeRole)
			if err != nil {
				return err
			}

			return i.Complete(ctx)
		})

	// Cache setup
	c := cache.NewCacheProvider(t)
	cw := cache.NewAsyncWriter(t)
	cw.EXPECT().Queue(ctx, cachekey.Role("test-reader", ""), mock.AnythingOfType("store.Role")).Return(nil).Once()
	cw.EXPECT().Flush(ctx).Return(nil)
	cw.EXPECT().Close(ctx).Return(nil)
	c.EXPECT().BulkWriter(ctx).Return(cw, nil)

	// Store setup
	sdb := storedb.NewProvider(t)
	sw := storedb.NewAsyncWriter(t)
	roles := collections.Role{}
	storeId := store.ObjectID()
	sw.EXPECT().Queue(ctx, mock.AnythingOfType("*store.Role")).
		RunAndReturn(func(ctx context.Context, i any) error {
			i.(*store.Role).Id = storeId
			return nil
		}).Once()
	sw.EXPECT().Flush(ctx).Return(nil)
	sw.EXPECT().Close(ctx).Return(nil)
	sdb.EXPECT().BulkWriter(ctx, roles, mock.Anything).Return(sw, nil)

	deps := &Dependencies{
		Collector: client,
		Cache:     c,
		StoreDB:   sdb,
		Config: &config.KubehoundConfig{
			Builder: config.BuilderConfig{
				Edge: config.EdgeBuilderConfig{},
			},
		},
	}

	// Initialize
	err = cri.Initialize(ctx, deps)
	assert.NoError(t, err)

	// Run
	err = cri.Run(ctx)
	assert.NoError(t, err)

	// Close
	err = cri.Close(ctx)
	assert.NoError(t, err)
}
