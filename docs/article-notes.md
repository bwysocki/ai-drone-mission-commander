# Milestone 9 — Embeddings and Vector Store

Conversation memory preserves a dialogue. It does not provide a searchable library
of operational procedures. This milestone adds that library and explores retrieval
before connecting it to the agent.

An embedding represents text as a numerical vector. Documents and queries use the
same embedding model so that their vectors can be compared in the same space.
A query such as “What should I do when energy is running low?” can retrieve battery
guidance even without repeating the document's wording. This is a capability to
check with the real model; our automated tests use predictable synthetic vectors.

## Build the index explicitly

The six short Markdown files cover battery, weather, GPS, mission preparation,
fault escalation and inspection. KnowledgeCatalog loads each complete file as one
Spring AI Document with a stable filename ID and descriptive metadata:

```java
new Document(fileName, text, Map.of(
        "source", "knowledge/" + fileName,
        "title", title,
        "type", type.name(),
        "topic", topic.name()));
```

Spring Boot configures the EmbeddingModel using:

```properties
spring.ai.openai.embedding.model=text-embedding-3-small
spring.ai.openai.embedding.encoding-format=float
```

KnowledgeSearchService builds the actual Spring AI store:

```java
var documents = catalog.documents();
var candidate = SimpleVectorStore.builder(embeddingModel).build();
candidate.add(documents);
index = candidate;
```

Building happens only through POST /api/knowledge/index. The service publishes the
new index after every embedding succeeds, so a failed rebuild keeps the previous
index usable. No partially loaded index becomes visible. This store is in memory
and is lost when the application restarts.

## Retrieve documents without generating an answer

The central API is similaritySearch:

```java
var request = SearchRequest.builder()
        .query(query)
        .topK(topK)
        .similarityThreshold(threshold);
var filters = new FilterExpressionBuilder();
request.filterExpression(filters.and(
        filters.eq("type", "SAFETY"),
        filters.eq("topic", "BATTERY")).build());
var documents = current.similaritySearch(request.build());
```

In the endpoint both filters are optional; when present together they use AND.
Filtering is a metadata constraint, while ranking compares embedding vectors.
The result exposes original text, metadata and a cosine similarity score. It is
not an assistant answer, a confidence probability, or mission approval.

## Demonstrate this milestone in Swagger UI

Use the normal application profile with valid OpenAI credentials and expand
**Knowledge search**:

1. Execute GET /api/knowledge/documents. Show the six documents and the metadata
   on battery-policy.md. This call needs no embedding request.
2. Before the first index build, execute POST /api/knowledge/search with
   {"query":"What should I do when energy is running low?"}. Explain the 409:
   there is no index yet.
3. Execute POST /api/knowledge/index and show documentCount: 6.
4. Execute POST /api/knowledge/search with this body:

```json
{
  "query": "What should I do when energy is running low?",
  "topK": 3,
  "similarityThreshold": 0.0
}
```

Inspect the returned text, source and score. Battery guidance should be relevant;
exact scores and ordering depend on the real embedding model. Try a second query:
“The drone can no longer determine its position.” Compare the GPS procedure.

5. Repeat the energy query with type SAFETY and topic BATTERY. Only matching
   metadata may appear. Change type to PROCEDURE while keeping topic BATTERY:
   the result is empty because no document has that combination.
6. Remove the filters and increase similarityThreshold. Explain that fewer than
   topK results, including zero results, is valid.

Indexing and searching use the paid embedding API, but no chat completion. Spring
AI may additionally embed “Hello World” on first use to discover vector dimensions.
That extra call was visible in the integration test. Listing documents and starting
the application make no embedding calls.

## What I learned

SimpleVectorStore makes the retrieval mechanics visible without a database: create
Documents, embed them, then embed the question and compare vectors. Metadata adds
precise restrictions alongside semantic ranking. Changing the embedding model
requires rebuilding the index; vectors from different models must not be mixed.

Tests use the real SimpleVectorStore with synthetic embeddings to verify ranking,
thresholds, filters and atomic replacement. A local HTTP provider stub verifies the
real OpenAI embedding client and REST path with dummy credentials, including a
provider failure during rebuilding. It checks that retrieval never calls the chat
endpoint. These tests verify integration mechanics, not live semantic quality.

Each file remains a single document. ETL and splitting come next; RAG will later
place retrieved text into the agent's prompt. Deterministic mission safety remains
Java's responsibility.
