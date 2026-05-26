package org.flossware.jplatform.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Configuration for cluster membership and coordination.
 * Specifies cluster name, network settings, and seed nodes for discovery.
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * ClusterConfig config = ClusterConfig.builder()
 *     .clusterName("jplatform-production")
 *     .bindAddress("192.168.1.10")
 *     .bindPort(5701)
 *     .addSeedNode("192.168.1.10:5701")
 *     .addSeedNode("192.168.1.11:5701")
 *     .stateBackend(StateBackend.HAZELCAST)
 *     .build();
 * }</pre>
 *
 * @see ClusterManager
 */
public class ClusterConfig {
    private final String clusterName;
    private final String bindAddress;
    private final int bindPort;
    private final List<String> seedNodes;
    private final StateBackend stateBackend;

    private ClusterConfig(Builder builder) {
        this.clusterName = builder.clusterName;
        this.bindAddress = builder.bindAddress;
        this.bindPort = builder.bindPort;
        this.seedNodes = builder.seedNodes != null ?
                List.copyOf(builder.seedNodes) : Collections.emptyList();
        this.stateBackend = builder.stateBackend;
    }

    /**
     * Returns the cluster name.
     *
     * @return the cluster name
     */
    public String getClusterName() {
        return clusterName;
    }

    /**
     * Returns the bind address for this node.
     *
     * @return the bind address
     */
    public String getBindAddress() {
        return bindAddress;
    }

    /**
     * Returns the bind port for this node.
     *
     * @return the bind port
     */
    public int getBindPort() {
        return bindPort;
    }

    /**
     * Returns the seed nodes for cluster discovery.
     *
     * @return an unmodifiable list of seed node addresses
     */
    public List<String> getSeedNodes() {
        return Collections.unmodifiableList(seedNodes);
    }

    /**
     * Returns the state backend implementation.
     *
     * @return the state backend
     */
    public StateBackend getStateBackend() {
        return stateBackend;
    }

    /**
     * Creates a new builder for constructing cluster configurations.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * State backend implementations for distributed storage.
     */
    public enum StateBackend {
        /** Hazelcast In-Memory Data Grid */
        HAZELCAST,
        /** Redis distributed cache */
        REDIS,
        /** In-memory (single node only) */
        MEMORY
    }

    /**
     * Builder for ClusterConfig.
     */
    public static class Builder {
        private String clusterName = "jplatform-cluster";
        private String bindAddress = "localhost";
        private int bindPort = 5701;
        private List<String> seedNodes = new ArrayList<>();
        private StateBackend stateBackend = StateBackend.HAZELCAST;

        /**
         * Sets the cluster name.
         *
         * @param clusterName the cluster name
         * @return this builder
         */
        public Builder clusterName(String clusterName) {
            if (clusterName == null || clusterName.trim().isEmpty()) {
                throw new IllegalArgumentException("clusterName cannot be null or empty");
            }
            this.clusterName = clusterName;
            return this;
        }

        /**
         * Sets the bind address for this node.
         *
         * @param bindAddress the bind address
         * @return this builder
         */
        public Builder bindAddress(String bindAddress) {
            if (bindAddress == null || bindAddress.trim().isEmpty()) {
                throw new IllegalArgumentException("bindAddress cannot be null or empty");
            }
            this.bindAddress = bindAddress;
            return this;
        }

        /**
         * Sets the bind port for this node.
         *
         * @param bindPort the bind port
         * @return this builder
         * @throws IllegalArgumentException if bindPort is not in valid range (1-65535)
         */
        public Builder bindPort(int bindPort) {
            if (bindPort < 1 || bindPort > 65535) {
                throw new IllegalArgumentException(
                    "bindPort must be in range 1-65535, got: " + bindPort);
            }
            this.bindPort = bindPort;
            return this;
        }

        /**
         * Adds a seed node for cluster discovery.
         *
         * @param seedNode the seed node address (host:port)
         * @return this builder
         */
        public Builder addSeedNode(String seedNode) {
            if (seedNode == null || seedNode.trim().isEmpty()) {
                throw new IllegalArgumentException("seedNode cannot be null or empty");
            }
            if (this.seedNodes == null) {
                this.seedNodes = new ArrayList<>();
            }
            this.seedNodes.add(seedNode);
            return this;
        }

        /**
         * Sets all seed nodes, replacing any previously added.
         *
         * @param seedNodes the seed node addresses
         * @return this builder
         */
        public Builder seedNodes(List<String> seedNodes) {
            if (seedNodes == null) {
                throw new IllegalArgumentException("seedNodes list cannot be null");
            }
            this.seedNodes = new ArrayList<>();
            for (String seedNode : seedNodes) {
                addSeedNode(seedNode);  // Reuse validation
            }
            return this;
        }

        /**
         * Sets the state backend implementation.
         *
         * @param stateBackend the state backend
         * @return this builder
         */
        public Builder stateBackend(StateBackend stateBackend) {
            if (stateBackend == null) {
                throw new IllegalArgumentException("stateBackend cannot be null");
            }
            this.stateBackend = stateBackend;
            return this;
        }

        /**
         * Builds the ClusterConfig instance.
         *
         * @return a new ClusterConfig
         */
        public ClusterConfig build() {
            return new ClusterConfig(this);
        }
    }
}
