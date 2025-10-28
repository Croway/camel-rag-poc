# Chat Route Usage

## Overview
The ChatRoute provides an HTTP endpoint for interactive chat with the configured `myAgent` using embeddings for context-aware responses.

## Endpoint
- **URL**: `http://localhost:8080/chat`
- **Method**: POST
- **Content-Type**: text/plain
- **Response**: text/plain

## How It Works

1. **User sends a question** via HTTP POST to `/chat`
2. **Question is embedded** using the configured `AllMiniLmL6V2EmbeddingModel`
3. **Similar content is retrieved** from the Infinispan vector store based on semantic similarity
4. **Agent receives** the question along with relevant context from the embeddings
5. **Agent responds** using the configured chat model (Ollama with granite4:tiny-h)

## Usage Examples

### Using curl
```bash
curl -X POST http://localhost:8080/chat \
  -H "Content-Type: text/plain" \
  -d "What is Apache Camel?"
```

### Using HTTPie
```bash
echo "How do I configure routes?" | http POST http://localhost:8080/chat
```

### Using JavaScript/Fetch
```javascript
fetch('http://localhost:8080/chat', {
  method: 'POST',
  headers: {
    'Content-Type': 'text/plain',
  },
  body: 'Explain the embedding process'
})
.then(response => response.text())
.then(data => console.log(data));
```

### Using Python
```python
import requests

response = requests.post(
    'http://localhost:8080/chat',
    headers={'Content-Type': 'text/plain'},
    data='What are the main components?'
)

print(response.text)
```

## Configuration

The route uses:
- **Agent**: `myAgent` (configured in MainApp.java)
  - Model: Ollama granite4:tiny-h
  - Base URL: http://127.0.0.1:11434
  - Timeout: 300 seconds

- **Embeddings**: `embeddingModel`
  - Model: AllMiniLmL6V2EmbeddingModel
  - Dimension: 384

- **Vector Store**: Infinispan
  - Cache: vectorCache
  - Stores document embeddings for semantic search

## Running the Application

1. Ensure Ollama is running with the granite4:tiny-h model:
   ```bash
   ollama run granite4:tiny-h
   ```

2. Ensure Infinispan is configured and running

3. Start the Camel application:
   ```bash
   mvn clean compile exec:java -Dexec.mainClass="com.example.camel.MainApp"
   ```

4. The HTTP endpoint will be available at: `http://localhost:8080/chat`

## CORS Support

The route includes CORS headers to allow cross-origin requests from web applications:
- `Access-Control-Allow-Origin: *`
- Supports POST and OPTIONS methods

## Notes

- The route performs semantic search on the vector store to provide relevant context
- Answers are based on the documents that were previously processed and embedded
- If no relevant context is found, the agent will respond based on its general knowledge
- The system message instructs the agent to be helpful and indicate when it cannot find information in the provided context
