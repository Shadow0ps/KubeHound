:remote connect tinkerpop.server conf/remote.yaml session
:remote console
:remote config timeout none

//
// Graph schema and index definition for the KubeHound graph mode
// See details of the janus graph APIs here https://docs.janusgraph.org/schema/
//

graph.tx().rollback()
mgmt = graph.openManagement();

System.out.println("[KUBEHOUND] Creating graph schema and indexes");

// Create our vertex labels
container = mgmt.makeVertexLabel('Container').make();
identity = mgmt.makeVertexLabel('Identity').make();
node = mgmt.makeVertexLabel('Node').make();
pod = mgmt.makeVertexLabel('Pod').make();
permissionSet = mgmt.makeVertexLabel('PermissionSet').make();
volume = mgmt.makeVertexLabel('Volume').make();
endpoint = mgmt.makeVertexLabel('Endpoint').make();

// Create our edge labels and connections
permissionDiscover = mgmt.makeEdgeLabel('PERMISSION_DISCOVER').multiplicity(MULTI).make();
mgmt.addConnection(permissionDiscover, identity, permissionSet);

volumeDiscover = mgmt.makeEdgeLabel('VOLUME_DISCOVER').multiplicity(MULTI).make();
mgmt.addConnection(volumeDiscover, container, volume);

volumeAccess = mgmt.makeEdgeLabel('VOLUME_ACCESS').multiplicity(MULTI).make();
mgmt.addConnection(volumeAccess, node, volume);

hostWrite = mgmt.makeEdgeLabel('EXPLOIT_HOST_WRITE').multiplicity(MULTI).make();
mgmt.addConnection(hostWrite, volume, node);

hostRead = mgmt.makeEdgeLabel('EXPLOIT_HOST_READ').multiplicity(MULTI).make();
mgmt.addConnection(hostRead, volume, node);

hostTraverse = mgmt.makeEdgeLabel('EXPLOIT_HOST_TRAVERSE').multiplicity(MULTI).make();
mgmt.addConnection(hostTraverse, volume, volume);

sharedPs = mgmt.makeEdgeLabel('SHARE_PS_NAMESPACE').multiplicity(MULTI).make();
mgmt.addConnection(sharedPs, container, container);

containerAttach = mgmt.makeEdgeLabel('CONTAINER_ATTACH').multiplicity(ONE2MANY).make();
mgmt.addConnection(containerAttach, pod, container);

idAssume = mgmt.makeEdgeLabel('IDENTITY_ASSUME').multiplicity(MANY2ONE).make();
mgmt.addConnection(idAssume, container, identity);
mgmt.addConnection(idAssume, node, identity);

idImpersonate = mgmt.makeEdgeLabel('IDENTITY_IMPERSONATE').multiplicity(MANY2ONE).make();
mgmt.addConnection(idImpersonate, permissionSet, identity);

roleBind = mgmt.makeEdgeLabel('ROLE_BIND').multiplicity(MANY2ONE).make();
mgmt.addConnection(roleBind, permissionSet, permissionSet);

podAttach = mgmt.makeEdgeLabel('POD_ATTACH').multiplicity(ONE2MANY).make();
mgmt.addConnection(podAttach, node, pod);


podCreate = mgmt.makeEdgeLabel('POD_CREATE').multiplicity(MULTI).make();
mgmt.addConnection(podCreate, permissionSet, node);
mgmt.addConnection(podCreate, permissionSet, permissionSet); // self-referencing for large cluster optimizations

podPatch = mgmt.makeEdgeLabel('POD_PATCH').multiplicity(MULTI).make();
mgmt.addConnection(podPatch, permissionSet, pod);
mgmt.addConnection(podPatch, permissionSet, permissionSet); // self-referencing for large cluster optimizations

podExec = mgmt.makeEdgeLabel('POD_EXEC').multiplicity(MULTI).make();
mgmt.addConnection(podExec, permissionSet, pod);
mgmt.addConnection(podExec, permissionSet, permissionSet); // self-referencing for large cluster optimizations

tokenSteal = mgmt.makeEdgeLabel('TOKEN_STEAL').multiplicity(MULTI).make();
mgmt.addConnection(tokenSteal, volume, identity);

tokenBruteforce = mgmt.makeEdgeLabel('TOKEN_BRUTEFORCE').multiplicity(MULTI).make();
mgmt.addConnection(tokenBruteforce, permissionSet, identity);

tokenList = mgmt.makeEdgeLabel('TOKEN_LIST').multiplicity(MULTI).make();
mgmt.addConnection(tokenList, permissionSet, identity);

tokenVarLog = mgmt.makeEdgeLabel('TOKEN_VAR_LOG_SYMLINK').multiplicity(ONE2MANY).make();
mgmt.addConnection(tokenVarLog, container, volume);

nsenter = mgmt.makeEdgeLabel('CE_NSENTER').multiplicity(MANY2ONE).make();
mgmt.addConnection(nsenter, container, node);

moduleLoad = mgmt.makeEdgeLabel('CE_MODULE_LOAD').multiplicity(MANY2ONE).make();
mgmt.addConnection(moduleLoad, container, node);

umhCorePattern = mgmt.makeEdgeLabel('CE_UMH_CORE_PATTERN').multiplicity(MANY2ONE).make();
mgmt.addConnection(umhCorePattern, container, node);

privMount = mgmt.makeEdgeLabel('CE_PRIV_MOUNT').multiplicity(MANY2ONE).make();
mgmt.addConnection(privMount, container, node);

sysPtrace = mgmt.makeEdgeLabel('CE_SYS_PTRACE').multiplicity(MANY2ONE).make();
mgmt.addConnection(sysPtrace, container, node);

endpointExploit = mgmt.makeEdgeLabel('ENDPOINT_EXPLOIT').multiplicity(MULTI).make();
mgmt.addConnection(endpointExploit, endpoint, container);

// All properties we will index on
cls = mgmt.makePropertyKey('class').dataType(String.class).cardinality(Cardinality.SINGLE).make();
storeID = mgmt.makePropertyKey('storeID').dataType(String.class).cardinality(Cardinality.SINGLE).make();
app = mgmt.makePropertyKey('app').dataType(String.class).cardinality(Cardinality.SINGLE).make();
team = mgmt.makePropertyKey('team').dataType(String.class).cardinality(Cardinality.SINGLE).make();
service = mgmt.makePropertyKey('service').dataType(String.class).cardinality(Cardinality.SINGLE).make();
name = mgmt.makePropertyKey('name').dataType(String.class).cardinality(Cardinality.SINGLE).make();
namespace = mgmt.makePropertyKey('namespace').dataType(String.class).cardinality(Cardinality.SINGLE).make();
type = mgmt.makePropertyKey('type').dataType(String.class).cardinality(Cardinality.SINGLE).make();
critical = mgmt.makePropertyKey('critical').dataType(Boolean.class).cardinality(Cardinality.SINGLE).make();
port = mgmt.makePropertyKey('port').dataType(Integer.class).cardinality(Cardinality.SINGLE).make();
portName = mgmt.makePropertyKey('portName').dataType(String.class).cardinality(Cardinality.SINGLE).make();
serviceEndpoint = mgmt.makePropertyKey('serviceEndpoint').dataType(String.class).cardinality(Cardinality.SINGLE).make();
serviceDns = mgmt.makePropertyKey('serviceDns').dataType(String.class).cardinality(Cardinality.SINGLE).make();
exposure = mgmt.makePropertyKey('exposure').dataType(Integer.class).cardinality(Cardinality.SINGLE).make();

// All properties that we want to be able to search on
isNamespaced = mgmt.makePropertyKey('isNamespaced').dataType(Boolean.class).cardinality(Cardinality.SINGLE).make();
compromised = mgmt.makePropertyKey('compromised').dataType(Integer.class).cardinality(Cardinality.SINGLE).make();
sourcePath = mgmt.makePropertyKey('sourcePath').dataType(String.class).cardinality(Cardinality.SINGLE).make();
mountPath = mgmt.makePropertyKey('mountPath').dataType(String.class).cardinality(Cardinality.SINGLE).make();
readonly = mgmt.makePropertyKey('readonly').dataType(Boolean.class).cardinality(Cardinality.SINGLE).make();
nodeName = mgmt.makePropertyKey('node').dataType(String.class).cardinality(Cardinality.SINGLE).make();
sharedPs = mgmt.makePropertyKey('shareProcessNamespace').dataType(Boolean.class).cardinality(Cardinality.SINGLE).make();
serviceAccount = mgmt.makePropertyKey('serviceAccount').dataType(String.class).cardinality(Cardinality.SINGLE).make();
image = mgmt.makePropertyKey('image').dataType(String.class).cardinality(Cardinality.SINGLE).make();
podName = mgmt.makePropertyKey('pod').dataType(String.class).cardinality(Cardinality.SINGLE).make();
hostNetwork = mgmt.makePropertyKey('hostNetwork').dataType(Boolean.class).cardinality(Cardinality.SINGLE).make();
hostPid = mgmt.makePropertyKey('hostPid').dataType(Boolean.class).cardinality(Cardinality.SINGLE).make();
hostIpc = mgmt.makePropertyKey('hostIpc').dataType(Boolean.class).cardinality(Cardinality.SINGLE).make();
privesc = mgmt.makePropertyKey('privesc').dataType(Boolean.class).cardinality(Cardinality.SINGLE).make();
privileged = mgmt.makePropertyKey('privileged').dataType(Boolean.class).cardinality(Cardinality.SINGLE).make();
runAsUser = mgmt.makePropertyKey('runAsUser').dataType(Long.class).cardinality(Cardinality.SINGLE).make();
rules = mgmt.makePropertyKey('rules').dataType(String.class).cardinality(Cardinality.LIST).make();
command = mgmt.makePropertyKey('command').dataType(String.class).cardinality(Cardinality.LIST).make();
args = mgmt.makePropertyKey('args').dataType(String.class).cardinality(Cardinality.LIST).make();
capabilities = mgmt.makePropertyKey('capabilities').dataType(String.class).cardinality(Cardinality.LIST).make();
ports = mgmt.makePropertyKey('ports').dataType(String.class).cardinality(Cardinality.LIST).make();
identityName = mgmt.makePropertyKey('identity').dataType(String.class).cardinality(Cardinality.SINGLE).make();
addressType = mgmt.makePropertyKey('addressType').dataType(String.class).cardinality(Cardinality.SINGLE).make();
addresses = mgmt.makePropertyKey('addresses').dataType(String.class).cardinality(Cardinality.LIST).make();
protocol = mgmt.makePropertyKey('protocol').dataType(String.class).cardinality(Cardinality.SINGLE).make();
role = mgmt.makePropertyKey('role').dataType(String.class).cardinality(Cardinality.SINGLE).make();
roleBinding = mgmt.makePropertyKey('roleBinding').dataType(String.class).cardinality(Cardinality.SINGLE).make();


// Define properties for each vertex 
mgmt.addProperties(container, cls, storeID, app, team, service, isNamespaced, namespace, name, image, privileged, privesc, hostPid, 
    hostIpc, hostNetwork, runAsUser, podName, nodeName, compromised, command, args, capabilities, ports);
mgmt.addProperties(identity, cls, storeID, app, team, service, name, isNamespaced, namespace, type, critical);
mgmt.addProperties(node, cls, storeID, app, team, service, name, isNamespaced, namespace, compromised, critical);
mgmt.addProperties(pod, cls, storeID, app, team, service, name, isNamespaced, namespace, sharedPs, serviceAccount, nodeName, compromised, critical);
mgmt.addProperties(permissionSet, cls, storeID, app, team, service, name, isNamespaced, namespace, role, roleBinding, rules, critical);
mgmt.addProperties(volume, cls, storeID, app, team, service, name, isNamespaced, namespace, type, sourcePath, mountPath, readonly);
mgmt.addProperties(endpoint, cls, storeID, app, team, service, name, isNamespaced, namespace, serviceEndpoint, serviceDns, addressType, 
    addresses, port, portName, protocol, exposure, compromised);


// Create the indexes on vertex properties
// NOTE: labels cannot be indexed so we create the class property to mirror the vertex label and allow indexing
mgmt.buildIndex('byClass', Vertex.class).addKey(cls).buildCompositeIndex();
mgmt.buildIndex('byStoreIDUnique', Vertex.class).addKey(storeID).unique().buildCompositeIndex();
mgmt.buildIndex('byApp', Vertex.class).addKey(app).buildCompositeIndex();
mgmt.buildIndex('byTeam', Vertex.class).addKey(team).buildCompositeIndex();
mgmt.buildIndex('byService', Vertex.class).addKey(service).buildCompositeIndex();
mgmt.buildIndex('byName', Vertex.class).addKey(name).buildCompositeIndex();
mgmt.buildIndex('byNamespace', Vertex.class).addKey(namespace).buildCompositeIndex();
mgmt.buildIndex('byType', Vertex.class).addKey(type).buildCompositeIndex();
mgmt.buildIndex('byCritical', Vertex.class).addKey(critical).buildCompositeIndex();
mgmt.buildIndex('byPort', Vertex.class).addKey(port).buildCompositeIndex();
mgmt.buildIndex('byPortName', Vertex.class).addKey(portName).buildCompositeIndex();
mgmt.buildIndex('byServiceEndpoint', Vertex.class).addKey(serviceEndpoint).buildCompositeIndex();
mgmt.buildIndex('byServiceDns', Vertex.class).addKey(serviceDns).buildCompositeIndex();
mgmt.buildIndex('byExposure', Vertex.class).addKey(exposure).buildCompositeIndex();


mgmt.commit();

// Wait for indexes to become available
ManagementSystem.awaitGraphIndexStatus(graph, 'byClass').status(SchemaStatus.ENABLED).call();
ManagementSystem.awaitGraphIndexStatus(graph, 'byStoreIDUnique').status(SchemaStatus.ENABLED).call();
ManagementSystem.awaitGraphIndexStatus(graph, 'byApp').status(SchemaStatus.ENABLED).call();
ManagementSystem.awaitGraphIndexStatus(graph, 'byTeam').status(SchemaStatus.ENABLED).call();
ManagementSystem.awaitGraphIndexStatus(graph, 'byService').status(SchemaStatus.ENABLED).call();
ManagementSystem.awaitGraphIndexStatus(graph, 'byName').status(SchemaStatus.ENABLED).call();
ManagementSystem.awaitGraphIndexStatus(graph, 'byNamespace').status(SchemaStatus.ENABLED).call();
ManagementSystem.awaitGraphIndexStatus(graph, 'byType').status(SchemaStatus.ENABLED).call();
ManagementSystem.awaitGraphIndexStatus(graph, 'byCritical').status(SchemaStatus.ENABLED).call();

System.out.println("[KUBEHOUND] graph schema and indexes ready");
mgmt.close();

// Close the open connection
:remote close