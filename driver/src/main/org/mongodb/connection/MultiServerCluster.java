/*
 * Copyright (c) 2008 MongoDB, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.mongodb.connection;

import org.mongodb.diagnostics.Loggers;
import org.mongodb.event.ClusterListener;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Logger;

import static java.lang.String.format;
import static org.mongodb.assertions.Assertions.isTrue;
import static org.mongodb.connection.ClusterConnectionMode.MULTIPLE;
import static org.mongodb.connection.ClusterType.SHARDED;
import static org.mongodb.connection.ClusterType.UNKNOWN;
import static org.mongodb.connection.ServerConnectionState.CONNECTING;
import static org.mongodb.connection.ServerType.SHARD_ROUTER;
import static org.mongodb.connection.ServerType.STANDALONE;

/**
 * This class needs to be final because we are leaking a reference to "this" from the constructor
 */
final class MultiServerCluster extends BaseCluster {
    private static final Logger LOGGER = Loggers.getLogger("cluster");

    private ClusterType clusterType;
    private String replicaSetName;
    private int latestSetVersion = Integer.MIN_VALUE;
    private final ConcurrentMap<ServerAddress, ServerTuple> addressToServerTupleMap =
        new ConcurrentHashMap<ServerAddress, ServerTuple>();

    private static final class ServerTuple {
        private final ClusterableServer server;
        private ServerDescription description;

        private ServerTuple(final ClusterableServer server, final ServerDescription description) {
            this.server = server;
            this.description = description;
        }
    }

    public MultiServerCluster(final String clusterId, final ClusterSettings settings, final ClusterableServerFactory serverFactory,
                              final ClusterListener clusterListener) {
        super(clusterId, settings, serverFactory, clusterListener);
        isTrue("connection mode is multiple", settings.getMode() == ClusterConnectionMode.MULTIPLE);
        clusterType = settings.getRequiredClusterType();
        replicaSetName = settings.getRequiredReplicaSetName();

        LOGGER.info(format("Cluster created with settings %s", settings.getShortDescription()));

        // synchronizing this code because addServer registers a callback which is re-entrant to this instance.
        // In other words, we are leaking a reference to "this" from the constructor.
        synchronized (this) {
            for (final ServerAddress serverAddress : settings.getHosts()) {
                addServer(serverAddress);
            }
            updateDescription();
        }
        fireChangeEvent();
    }

    @Override
    public void close() {
        if (!isClosed()) {
            synchronized (this) {
                for (final ServerTuple serverTuple : addressToServerTupleMap.values()) {
                    serverTuple.server.close();
                }
            }
            super.close();
        }
    }

    @Override
    protected ClusterableServer getServer(final ServerAddress serverAddress) {
        isTrue("is open", !isClosed());

        ServerTuple serverTuple = addressToServerTupleMap.get(serverAddress);
        if (serverTuple == null) {
            return null;
        }
        return serverTuple.server;
    }


    private final class DefaultServerStateListener implements ChangeListener<ServerDescription> {
        @Override
        public void stateChanged(final ChangeEvent<ServerDescription> event) {
            onChange(event);
        }
    }

    private void onChange(final ChangeEvent<ServerDescription> event) {
        if (isClosed()) {
            return;
        }

        synchronized (this) {
            ServerDescription newDescription = event.getNewValue();
            ServerTuple serverTuple = addressToServerTupleMap.get(newDescription.getAddress());
            if (serverTuple == null) {
                return;
            }

            serverTuple.description = newDescription;

            if (event.getNewValue().isOk()) {
                if (clusterType == UNKNOWN) {
                    clusterType = newDescription.getClusterType();
                    LOGGER.info(format("Discovered cluster type of %s", clusterType));
                }

                switch (clusterType) {
                    case REPLICA_SET:
                        handleReplicaSetMemberChanged(newDescription);
                        break;
                    case SHARDED:
                        handleShardRouterChanged(newDescription);
                        break;
                    case STANDALONE:
                        handleStandAloneChanged(newDescription);
                        break;
                    default:
                        break;
                }
            }
            updateDescription();
        }
        fireChangeEvent();
    }

    private void handleReplicaSetMemberChanged(final ServerDescription newDescription) {
        if (!newDescription.isReplicaSetMember()) {
            LOGGER.severe(format("Expecting replica set member, but found a %s.  Removing %s from client view of cluster.",
                                 newDescription.getType(), newDescription.getAddress()));
            removeServer(newDescription.getAddress());
            return;
        }

        if (replicaSetName == null) {
            replicaSetName = newDescription.getSetName();
        }

        if (replicaSetName != null && !replicaSetName.equals(newDescription.getSetName())) {
            LOGGER.severe(format("Expecting replica set member from set '%s', but found one from set '%s'.  "
                                 + "Removing %s from client view of cluster.",
                                 replicaSetName, newDescription.getSetName(), newDescription.getAddress()));
            removeServer(newDescription.getAddress());
            return;
        }

        if (newDescription.getSetVersion() == null || newDescription.getSetVersion() > latestSetVersion) {
            if (newDescription.getSetVersion() != null) {
                latestSetVersion = newDescription.getSetVersion();
            }

            ensureServers(newDescription);
        }

        if (newDescription.isPrimary()) {
            invalidateOldPrimaries(newDescription.getAddress());
        }
    }

    private void handleShardRouterChanged(final ServerDescription newDescription) {
        if (newDescription.getClusterType() != SHARDED) {
            LOGGER.severe(format("Expecting a %s, but found a %s.  Removing %s from client view of cluster.",
                                 SHARD_ROUTER, newDescription.getType(), newDescription.getAddress()));
            removeServer(newDescription.getAddress());
        }
    }

    private void handleStandAloneChanged(final ServerDescription newDescription) {
        if (getSettings().getHosts().size() > 1) {
            LOGGER.severe(format("Expecting a single %s, but found more than one.  Removing %s from client view of cluster.",
                                 STANDALONE, newDescription.getAddress()));
            clusterType = UNKNOWN;
            removeServer(newDescription.getAddress());
        }
    }

    private void addServer(final ServerAddress serverAddress) {
        if (!addressToServerTupleMap.containsKey(serverAddress)) {
            LOGGER.info(format("Adding discovered server %s to client view of cluster", serverAddress));
            ClusterableServer server = createServer(serverAddress, new DefaultServerStateListener());
            addressToServerTupleMap.put(serverAddress, new ServerTuple(server, getConnectingServerDescription(serverAddress)));
        }
    }

    private void removeServer(final ServerAddress serverAddress) {
        addressToServerTupleMap.remove(serverAddress).server.close();
    }

    private void invalidateOldPrimaries(final ServerAddress newPrimary) {
        LOGGER.info(format("Replica set primary has changed to %s", newPrimary));
        for (final ServerTuple serverTuple : addressToServerTupleMap.values()) {
            if (!serverTuple.description.getAddress().equals(newPrimary) && serverTuple.description.isPrimary()) {
                LOGGER.info(format("Rediscovering type of existing primary %s", serverTuple.description.getAddress()));
                serverTuple.server.invalidate();
                serverTuple.description = getConnectingServerDescription(serverTuple.description.getAddress());
            }
        }
    }

    private ServerDescription getConnectingServerDescription(final ServerAddress serverAddress) {
        return ServerDescription.builder().state(CONNECTING).address(serverAddress).build();
    }

    private void updateDescription() {
        List<ServerDescription> newServerDescriptionList = getNewServerDescriptionList();
        updateDescription(new ClusterDescription(MULTIPLE, clusterType, newServerDescriptionList));
    }

    private List<ServerDescription> getNewServerDescriptionList() {
        List<ServerDescription> serverDescriptions = new ArrayList<ServerDescription>();
        for (final ServerTuple cur : addressToServerTupleMap.values()) {
            serverDescriptions.add(cur.description);
        }
        return serverDescriptions;
    }

    private void ensureServers(final ServerDescription description) {
        addNewHosts(description.getHosts());
        addNewHosts(description.getPassives());
        removeExtras(description);
    }

    private void addNewHosts(final Set<String> hosts) {
        for (final String cur : hosts) {
            addServer(new ServerAddress(cur));
        }
    }

    private void removeExtras(final ServerDescription serverDescription) {
        Set<ServerAddress> allServerAddresses = getAllServerAddresses(serverDescription);
        for (final ServerTuple cur : addressToServerTupleMap.values()) {
            if (!allServerAddresses.contains(cur.description.getAddress())) {
                LOGGER.info(format("Server %s is no longer a member of the replica set.  Removing from client view of cluster.",
                                   cur.description.getAddress()));
                removeServer(cur.description.getAddress());
            }
        }
    }

    private Set<ServerAddress> getAllServerAddresses(final ServerDescription serverDescription) {
        Set<ServerAddress> retVal = new HashSet<ServerAddress>();
        addHostsToSet(serverDescription.getHosts(), retVal);
        addHostsToSet(serverDescription.getPassives(), retVal);
        addHostsToSet(serverDescription.getArbiters(), retVal);
        return retVal;
    }

    private void addHostsToSet(final Set<String> hosts, final Set<ServerAddress> retVal) {
        for (final String host : hosts) {
            retVal.add(new ServerAddress(host));
        }
    }
}
