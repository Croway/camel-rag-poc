package com.example.camel;

import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.DataType;

public class CamelEmbeddingsRoute extends RouteBuilder {

    @Override
    public void configure() throws Exception {
        from("file:/Users/fmariani/Repositories/croway/camel?noop=true&recursive=true&include=.*\\.(md|adoc)$")
                .autoStartup(false)
                .log("Processing file: ${header.CamelFileAbsolutePath} - ${header.CamelFileLength}")
                .convertBodyTo(String.class)
                .process(sanitizeInput())
                .setHeader("CamelLangChain4jAgentSystemMessage", constant(Constants.SYSTEM_MESSAGE))
                .to("langchain4j-agent:test?agent=#myAgent")
                .split(body().tokenize("===CHUNK_BOUNDARY==="))
                    .to("langchain4j-embeddings:embeddingModel")
                    .transform(new DataType("infinispan:embeddings"))
                    .to("infinispan:vectorCache?cacheContainer=#cacheManager&embeddingStoreDimension=" + Constants.VECTOR_DIMENSION)
                    .log("Stored embedding with ID: ${header.CamelInfinispanKey}");
    }

    private static Processor sanitizeInput() {
        return exchange -> {
            String output = exchange.getIn().getBody(String.class)
                    .replace("{{", "\\{\\{")
                    .replace("}}", "\\}\\}");

            exchange.getIn().setBody(output);
        };
    }
}
