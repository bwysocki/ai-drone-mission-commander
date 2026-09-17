# Milestone 10 — Document ETL

Milestone 9 stored one vector per complete procedure. This milestone makes the
preparation of knowledge explicit: read source files, transform their text, split
it into searchable chunks, enrich metadata and write the chunks to a vector store.
The model still does not generate an answer from the retrieved text; that is RAG
in milestone 11.

## Extract: read the source documents

KnowledgeCatalog now uses Spring AI's TextReader rather than reading text directly:

```java
var reader = new TextReader(resources.apply(source));
reader.setCharset(StandardCharsets.UTF_8);
String text = reader.get().getFirst().getText();
```

Each of the six files has an explicit, trusted catalog entry for title, type and
topic. The catalog gives it a portable source path and stable filename ID, instead
of keeping a machine-specific resource URI or a reader-generated random ID.
Files are read again when ingestion is requested. An empty or missing source fails
the operation before a replacement index can become visible.

## Transform: split without losing the source

KnowledgePipeline implements Spring AI DocumentTransformer. It normalizes CRLF/CR
line endings, trims outer whitespace and delegates splitting to TokenTextSplitter:

```java
private final TokenTextSplitter splitter = TokenTextSplitter.builder()
        .withChunkSize(80)
        .withMinChunkSizeChars(0)
        .withMinChunkLengthToEmbed(0)
        .withKeepSeparator(true)
        .build();
```

The small target makes chunking visible with these short sample procedures. It is
not a recommended universal chunk size. Splitting near punctuation affects actual
chunk sizes; the short-tail setting avoids dropping the final piece of a procedure.
The target counts text tokens, not the additional metadata sent by the embedding
model. This implementation does not introduce overlap between adjacent chunks.

Each chunk inherits source, title, type and topic and adds:

- documentId: the original filename.
- chunkIndex: its zero-based position in that source.
- chunkCount: the number of chunks produced from that source.

The chunk ID combines the filename, position and SHA-256 of its text. Repeating
transformation of identical input yields identical IDs, so retrieval remains
traceable to a source even after rebuilding.

## Load: publish a complete index

KnowledgeSearchService computes a fingerprint over all transformed chunk IDs,
texts and sorted metadata. If it matches the active snapshot, normal ingestion
returns updated=false and makes no embedding calls. Otherwise it uses the vector
store's DocumentWriter API:

```java
var candidate = SimpleVectorStore.builder(embeddingModel).build();
candidate.write(chunks);
index = new IndexSnapshot(candidate, fingerprint);
```

Writing creates embeddings. The service publishes the store and fingerprint as one
snapshot only after every write succeeds. A changed corpus replaces the entire
store; old chunks disappear. This deliberately favors a simple, atomic rebuild over
an incremental embedding cache: when any input changes, all chunks are re-embedded.

Concurrent initial searches share one successful ingestion. Searches during a
manual rebuild keep using the previous snapshot. A failed first build can be retried
by the next search; a failed rebuild preserves the working snapshot and fingerprint.

## Demonstration through Swagger

Start the normal application with OpenAI credentials, then open **Knowledge search**.

1. Execute GET /api/knowledge/documents. Show the six source procedures.
2. Execute GET /api/knowledge/chunks. Compare the battery procedure with its smaller
   fragments. Show source, documentId, chunkIndex and chunkCount. Repeat the preview:
   IDs stay the same, and neither preview calls the provider.
3. Without calling /index, execute POST /api/knowledge/search:

```json
{
  "query": "What should I do when energy is running low?",
  "topK": 3,
  "similarityThreshold": 0.0,
  "type": "SAFETY",
  "topic": "BATTERY"
}
```

The first search loads knowledge automatically and then embeds the query. Results
now represent chunks, so several matches can reference the same procedure.

4. Execute POST /api/knowledge/index with force=false. Show documentCount=6,
   chunkCount greater than six and updated=false. Unchanged input produces no new
   embedding calls.
5. Set force=true and execute again. updated=true demonstrates a deliberate rebuild
   and incurs embedding calls even though the documents are unchanged.
6. Restart and search again: ingestion runs again because both index and fingerprint
   live only in this process. No provider call happens just because the app starts.

Use the temporary-file test changedCorpusReplacesOldChunksAndFailedBuildCanBeRetried
when explaining source updates and rollback. Packaged classpath files are immutable
in normal deployment; changing them means rebuilding/redeploying. There is no upload
endpoint or automatic file watcher in this milestone.

## What I learned

ETL prepares searchable knowledge; it does not answer a question or approve a mission.
Stable IDs help trace chunks, but IDs alone do not avoid paying for repeated
embeddings. The fingerprint check must happen before the vector-store write.
Fingerprint deduplication is local to the running process, not persistent storage.

Tests exercise actual TextReader, TokenTextSplitter and SimpleVectorStore with
synthetic embeddings, including concurrent ingestion and rollback. The HTTP test
uses the actual OpenAI SDK against a local stub with a dummy key. Automated tests
verify mechanics; relevance and the quality of chunk boundaries still need manual
inspection with real queries.
