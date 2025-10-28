package com.example.camel;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import org.apache.camel.component.infinispan.remote.InfinispanRemoteComponent;
import org.apache.camel.component.infinispan.remote.InfinispanRemoteConfiguration;
import org.apache.camel.component.langchain4j.agent.api.Agent;
import org.apache.camel.component.langchain4j.agent.api.AgentConfiguration;
import org.apache.camel.component.langchain4j.agent.api.AgentWithoutMemory;
import org.apache.camel.main.Main;
import org.apache.camel.spi.ComponentCustomizer;

import java.time.Duration;

public class MainApp {

    public static void main(String[] args) throws Exception {
        Main main = new Main(MainApp.class);

        // Register LangChain4j embedding model
        EmbeddingModel embeddingModel = new AllMiniLmL6V2EmbeddingModel();
        main.bind("embeddingModel", embeddingModel);

//        ChatModel chatModel = OpenAiChatModel.builder()
//                .modelName("r1-qwen-14b-w4a16")
//                .apiKey("c63f5776a3c2227efb37403665155c83")
//                .baseUrl("https://deepseek-r1-qwen-14b-w4a16-maas-apicast-production.apps.prod.rhoai.rh-aiservices-bu.com:443/v1")
//                .timeout(Duration.ofSeconds(300))
//                .build();
        ChatModel chatModel = OllamaChatModel.builder()
                .modelName("granite4:tiny-h")
                .baseUrl("http://127.0.0.1:11434")
                .timeout(Duration.ofSeconds(300))
                .build();
        AgentConfiguration agentConfiguration = new AgentConfiguration()
                .withChatModel(chatModel);
        Agent agent = new AgentWithoutMemory(agentConfiguration);
        main.bind("myAgent", agent);

        // Register Infinispan configuration
        InfinispanConfiguration infinispanConfig = new InfinispanConfiguration(embeddingModel);
        main.bind("cacheManager", infinispanConfig.remoteCacheManager());

        // Register ComponentCustomizer for InfinispanRemoteComponent
        main.bind("infinispanComponentCustomizer", infinispanComponentCustomizer(infinispanConfig));

        main.run(args);
    }

    private static ComponentCustomizer infinispanComponentCustomizer(
            InfinispanConfiguration infinispanConfig) {
        return ComponentCustomizer.forType(
                InfinispanRemoteComponent.class,
                component -> {
                    InfinispanRemoteConfiguration configuration = component.getConfiguration();
                    configuration.setCacheContainer(infinispanConfig.remoteCacheManager());
                    configuration.setEmbeddingStoreDimension(infinispanConfig.getEmbeddingDimension());
                });
    }
}
