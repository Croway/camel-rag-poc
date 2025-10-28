package com.example.camel;

import dev.langchain4j.model.embedding.EmbeddingModel;
import org.apache.camel.component.infinispan.remote.InfinispanRemoteConfiguration;
import org.apache.camel.component.infinispan.remote.embeddingstore.EmbeddingStoreUtil;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.client.hotrod.configuration.ConfigurationBuilder;
import org.infinispan.commons.configuration.BasicConfiguration;
import org.infinispan.commons.configuration.StringConfiguration;

/**
 * Configuration for connecting to remote Infinispan server.
 * Uses camel-infinispan component with built-in embeddings support.
 */
public class InfinispanConfiguration {

    private static final String CACHE_NAME = "vectorCache";
    private static final String HOST = "localhost";
    private static final int PORT = 11222;
    private static final String USERNAME = "admin";
    private static final String PASSWORD = "password";

    private final EmbeddingModel embeddingModel;
    private RemoteCacheManager cacheManager;

    public InfinispanConfiguration(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public ConfigurationBuilder configurationBuilder() {
        ConfigurationBuilder builder = new ConfigurationBuilder();
        builder.forceReturnValues(true);
        builder.addServer()
                .host(HOST)
                .port(PORT);

        builder.security()
                .authentication()
                .username(USERNAME)
                .password(PASSWORD)
                .serverName("infinispan")
                .saslMechanism("DIGEST-MD5")
                .realm("default");

        return builder;
    }

    public RemoteCacheManager remoteCacheManager() {
        if (cacheManager != null) {
            return cacheManager;
        }

        InfinispanRemoteConfiguration infinispanConfig = new InfinispanRemoteConfiguration();
        infinispanConfig.setEmbeddingStoreDimension(embeddingModel.dimension());

        ConfigurationBuilder builder = configurationBuilder();

        // Configure marshaller for embeddings support
        EmbeddingStoreUtil.configureMarshaller(infinispanConfig, builder);

        cacheManager = new RemoteCacheManager(builder.build());

        // Create the cache if it doesn't exist
        createCacheIfNotExists();

        return cacheManager;
    }

    public String getCacheName() {
        return CACHE_NAME;
    }

    public int getEmbeddingDimension() {
        return embeddingModel.dimension();
    }

    private void createCacheIfNotExists() {
        String cacheConfig = """
                <distributed-cache name="%s">
                  <persistence>
                    <file-store>
                      <data path="data/%s"/>
                      <index path="index/%s"/>
                    </file-store>
                  </persistence>
                  <indexing storage="local-heap">
                    <indexed-entities>
                      <indexed-entity>%s%d</indexed-entity>
                    </indexed-entities>
                  </indexing>
                </distributed-cache>
                """.formatted(CACHE_NAME, CACHE_NAME, CACHE_NAME,
                             EmbeddingStoreUtil.DEFAULT_TYPE_NAME_PREFIX, embeddingModel.dimension());

        try {
            cacheManager.administration().getOrCreateCache(CACHE_NAME, new StringConfiguration(cacheConfig));
            System.out.println("Cache '" + CACHE_NAME + "' created/verified with vector dimension: "
                             + embeddingModel.dimension() + " (persistence enabled)");
        } catch (Exception e) {
            System.err.println("Failed to create cache: " + e.getMessage());
            throw new RuntimeException("Failed to create Infinispan cache", e);
        }
    }
}
