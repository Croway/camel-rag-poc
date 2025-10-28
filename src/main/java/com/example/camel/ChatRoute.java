package com.example.camel;

import org.apache.camel.ai.CamelLangchain4jAttributes;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.infinispan.InfinispanConstants;
import org.apache.camel.component.infinispan.InfinispanOperation;
import org.apache.camel.component.infinispan.remote.embeddingstore.InfinispanRemoteEmbedding;
import org.apache.camel.component.infinispan.remote.embeddingstore.InfinispanVectorQueryBuilder;
import org.apache.camel.spi.DataType;

import java.util.List;

public class ChatRoute extends RouteBuilder {

    // System message specifically for chat interactions
    private static final String CHAT_SYSTEM_MESSAGE = """
            You are a helpful AI assistant that answers questions based on the provided context.
            Use the context from the document embeddings to provide accurate and relevant answers.
            If you cannot find the answer in the provided context, say so clearly.
            Be concise and helpful in your responses.
            """;

    @Override
    public void configure() throws Exception {
        // HTTP endpoint for chat interactions
        from("platform-http:/chat?httpMethodRestrict=POST")
                .routeId("chat-route")
                .log("Received chat request: ${body}")

                // Convert incoming request to String
                .convertBodyTo(String.class)

                // Store the user's question
                .setHeader("userQuestion", simple("${body}"))

                // Generate embeddings for the question to find relevant context
                .to("langchain4j-embeddings:embeddingModel")
                // IMPORTANT: Move the embedding from body to header
                .setHeader(CamelLangchain4jAttributes.CAMEL_LANGCHAIN4J_EMBEDDING_VECTOR,
                        header(CamelLangchain4jAttributes.CAMEL_LANGCHAIN4J_EMBEDDING))
                // Set the operation BEFORE the transform
                .setHeader(InfinispanConstants.OPERATION, constant(InfinispanOperation.QUERY))
                .transform(new DataType("infinispan:embeddings"))
                // Search for similar embeddings in the vector store
                // This retrieves relevant document chunks based on semantic similarity
                .to("infinispan:vectorCache?cacheContainer=#cacheManager&embeddingStoreDimension=" + Constants.VECTOR_DIMENSION)
                .process(exchange -> {
                    List<Object[]> results = exchange.getIn().getBody(List.class);

                    if (results != null && !results.isEmpty()) {
                        // Extract text from all retrieved embeddings and combine them
                        StringBuilder context = new StringBuilder();

                        for (Object[] result : results) {
                            InfinispanRemoteEmbedding embedding = (InfinispanRemoteEmbedding) result[0];
                            Float score = (Float) result[1];

                            if (embedding.getText() != null) {
                                context.append(embedding.getText()).append(" ");
                            }
                        }

                        // Store the combined context
                        exchange.getIn().setHeader("retrievedContext", context.toString().trim());
                    } else {
                        exchange.getIn().setHeader("retrievedContext", "No relevant context found.");
                    }
                })

                // Store retrieved context and restore the original question
                .setBody(header("userQuestion"))

                // Combine the question with retrieved context for the agent
                // This provides the agent with relevant background information
                .setBody(simple("Context from documents: ${header.retrievedContext} User question: ${header.userQuestion}"))
                // Set the system message for chat interactions
                .setHeader("CamelLangChain4jAgentSystemMessage", constant(CHAT_SYSTEM_MESSAGE))

                // Send to the agent for processing
                .to("langchain4j-agent:chat?agent=#myAgent")

                .removeHeaders("*")
                .log("Agent response: ${body}")
                .setHeader("Content-Type", constant("text/plain; charset=utf-8"))
                .setHeader("Access-Control-Allow-Origin", constant("*"));

        // CORS preflight support
        from("platform-http:/chat?httpMethodRestrict=OPTIONS")
                .routeId("chat-options-route")
                .setHeader("Access-Control-Allow-Origin", constant("*"))
                .setHeader("Access-Control-Allow-Methods", constant("POST, OPTIONS"))
                .setHeader("Access-Control-Allow-Headers", constant("Content-Type"))
                .setBody(constant(""));
    }
}
