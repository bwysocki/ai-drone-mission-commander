
# Milestone 11 — Retrieval-Augmented Generation

The previous milestone could find procedure fragments. Now those fragments become
context for an answer. RAG connects retrieval with generation; it does not give the
model current telemetry or authority to execute a mission.

## Start with a policy question

POST /api/knowledge/ask is a simple question-answer path. It uses a dedicated
ChatClient with no tools or conversation memory. Set useRag=false to run the same
question with the same system prompt but without retrieving knowledge. This makes
the comparison clearer than changing both the prompt and the endpoint at once.

The answer includes a retrieval object with the actual search query and selected
chunks. The server obtains this context from the retrieval pipeline, not from text
the model generates. It shows what the model received, not which sources it truly
used to reach its answer.

## Build the modular pipeline

KnowledgeRag prepares a new RetrievalAugmentationAdvisor for each request:

```java
var advisor = RetrievalAugmentationAdvisor.builder()
        .order(ToolCallingAdvisor.DEFAULT_ORDER - 1)
        .taskExecutor(new SyncTaskExecutor())
        .queryTransformers(original -> original.mutate().text(query).build())
        .documentRetriever(q -> search.search(q.text(), selection.topK(),
                selection.threshold(), selection.type(), selection.topic()))
        .queryAugmenter((original, documents) -> {
            // Capture the actual selected documents in this request's trace.
            // Append reference JSON with chunk IDs, metadata and text.
            return original.mutate().text(augmentedText).build();
        })
        .build();
```

The augmenter body above is abbreviated; see KnowledgeRag.java for the complete
implementation. The retriever reuses the milestone 10 index and typed metadata
filters, including automatic ingestion on first use. No second vector store is
created for RAG. topK and similarityThreshold control the selected fragments.

The query transformer normalizes whitespace and optionally substitutes rag.query.
This is useful when the user says “What about an inspection?” but retrieval needs
“battery policy for starting inspections”. It changes retrieval only: the original
question is preserved for generation. We deliberately do not add an LLM query
rewriter, its extra latency or an automatic guess about ambiguous follow-ups.

The QueryAugmenter serializes selected fragments with their IDs and source metadata.
System instructions treat this material as reference data, never higher-priority
instructions. Empty retrieval is represented explicitly as an empty list; the model
is instructed to acknowledge missing policy evidence rather than invent a rule.
This is a prompting behavior to inspect, not deterministic proof of grounding.

## Combine tools, RAG and memory

The world agent opts into RAG when the request supplies rag, including an empty
object. The advisor order is:

```text
DevelopmentLoggingAdvisor (dev only)
    → MissionContextAdvisor
    → MessageChatMemoryAdvisor
    → RetrievalAugmentationAdvisor (when enabled)
    → ToolCallingAdvisor
    → model and read-only tool rounds
```

Memory must run before query augmentation. It then stores the original user message
and final assistant answer, rather than saving the retrieved corpus inside a user
turn. The RAG advisor also runs before the tool loop, so a multi-round tool call
does not repeat retrieval on every iteration. Each request owns its trace; sources
from one conversation cannot be reused as the trace for another.

Tools answer “What is Alpha's battery now?”. RAG answers “What does the inspection
policy say?”. Memory helps interpret conversation references. Java remains the
place for enforceable safety decisions.

## Demonstration in Swagger

1. Open **Knowledge answers → POST /api/knowledge/ask**:

```json
{
  "message": "What is the battery policy for starting a new inspection?",
  "useRag": false
}
```

Show retrieval.enabled=false and an empty documents array. No embedding request is
needed. A grounded policy answer is not available without supplied evidence.

2. Repeat with useRag=true and select the battery documents:

```json
{
  "message": "What is the battery policy for starting a new inspection?",
  "useRag": true,
  "rag": { "topK": 6, "type": "SAFETY", "topic": "BATTERY" }
}
```

Show the returned fragments, source filenames and scores. Find the statement that
battery below 20% means a new inspection should not start. The first request may
also build the index, so it can take longer and incur ingestion costs.

3. Change type to PROCEDURE while keeping topic BATTERY. There is no matching
combination: documents is empty. Inspect whether the answer acknowledges missing
policy evidence. A lack of matches is not evidence that an operation is safe.

4. Open **Simulation**, reset the world, then POST /api/simulation/events:

```json
{"type":"BATTERY_DROP","droneId":"alpha","amount":64}
```

Alpha starts at 82%, so it now has 18%. Reset weather has GOOD visibility and no rain.

5. Open **World agent → POST /api/agent/chat**:

```json
{
  "message": "Can Alpha start an inspection of SECTOR_B and return home? Check current status and weather, and consult the battery policy.",
  "rag": { "topK": 6, "type": "SAFETY", "topic": "BATTERY" }
}
```

Show the selected battery policy and the answer using current tool readings. Expect
advice against starting an inspection at 18%, citing the procedure. The agent cannot
approve or execute it. Use the returned conversationId to inspect history: it should
contain the original question, without the RETRIEVED PROCEDURES block.

6. For an agent comparison, omit rag and use a new conversation. Reusing the previous
conversation would carry its policy answer into history and contaminate the comparison.

## Verification and boundaries

KnowledgeRagTest uses real retrieval/advisor/tool implementations with mocked external
models. It verifies that the final prompt combines the retrieved 20% policy with
actual simulator tool results of 18% battery and good weather, retrieval runs once,
and memory retains the original question. Scripted answers do not prove real model
reasoning; inspect the live answer separately during the Swagger demonstration.

HTTP tests use the real OpenAI SDK against the existing local stub. They cover
with/without RAG, actual retrieval metadata, empty filters, invalid requests and
embedding-provider failures without real credentials or external model calls.

The new 20% policy is guidance stored in the knowledge base. This milestone does
not add its deterministic enforcement to mission execution. Milestone 12 adds the
Java safety layer; RAG alone must not be treated as that layer.
