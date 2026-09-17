# Milestone 1 — Preflight advice

Request: `POST /api/chat`

```json
{"message":"What should a drone operator check before an inspection mission?"}
```

Expected: HTTP 200 with answer text and metadata (response ID, model, finish reason
and token counts). The request passes through `ChatClient` and `ChatModel`.
No mission is executed. Repeat with `POST /api/chat/model` to compare direct model use.

Automated coverage: `AiDroneMissionCommanderApplicationTests` uses a local provider
stub returning a fixed preflight checklist and verifies both paths. Its HTTP 429 and
dropped-connection cases use disabled SDK retries. `ChatControllerTest` checks invalid
input (including non-string messages) returning HTTP 400 and empty answers returning
HTTP 502, using a mocked service. Plain JUnit tests cover mapping, prompt construction
and the full provider error/code matrix without HTTP calls.
Provider HTTP 400/401/403 errors return a sanitized HTTP 502 response. Rate limits,
provider HTTP 5xx and dropped connections return a sanitized HTTP 503 response.
Live answer quality requires the manual request with a real provider.

# Milestone 2 — Instructions, request options and incremental advice

Use the same question for all three endpoints:
`What should I check before sending Alpha to inspect Bravo?`

1. Send it to both POST endpoints without options. Expect a complete answer with
   metadata, using the configured model and default completion budget.
2. Repeat with `"options":{"maxCompletionTokens":1024}`. The configured model
   should be retained. An optional `model` override applies only to that request.
3. Send another request without options. Defaults should still apply.
4. Use the README's `curl -N` streaming example. Expect incremental `delta` events
   and a terminal `done`; concatenate text exactly, including whitespace.
5. Disconnect curl during a response. Spring MVC cancels the upstream subscription
   when it detects the disconnection, typically on its next write.
6. Append `Answer with a short numbered checklist.` to the prompt resource, then
   compare with `Explain each preflight check and explicitly list missing information.`
   Restart after each change and compare answer structure and missing facts.

Automated coverage: strict option validation, unchanged defaults after overrides,
resource-driven instructions, SSE event order, partial provider failure, no-content
failure and cancellation. Regression cases verify that whitespace-only answers and
provider streams ending without a generation finish reason produce an SSE error
with status 502 and no `done`. The local HTTP stub holds the second chunk until the first
has arrived in the MVC response, verifying incremental delivery through the real SDK.
Invalid query parameters return HTTP 400. Provider failures produce a sanitized SSE
`error` event under HTTP 200 and no `done`, even after partial text. These checks use
dummy credentials; live response quality and browser/proxy behavior require manual
verification. Prompt instructions do not validate or execute a drone mission.

# Milestone 3 — Extract a typed mission intent

Send this body to `POST /api/missions/intent`:

```json
{"message":"Send Alpha to Bravo, inspect the area and return home."}
```

Expected:

```json
{"droneId":"alpha","type":"INSPECTION","targetSector":"BRAVO","returnHome":true}
```

Repeat with `?nativeOutput=true` using a model that supports native structured output.
The domain result should be equivalent. Try `Have Alpha patrol Bravo.` to compare
`PATROL` and `returnHome=false`. No mission is executed and no safety assessment is made.

For an incomplete command such as `Inspect the area.`, the extraction instructions
require nulls for unknown identifiers. Java rejects that output with HTTP 502 and
title `Invalid AI output`. This is a manual semantic check: a schema alone cannot
prevent fabricated identifiers. Drone and sector existence checks belong to the
future simulator integration.

Automated coverage uses fixed provider responses to verify both wire formats, typed
conversion, invalid JSON, missing and extra fields, nulls, duplicate keys, wrong value
types, unsupported mission types and abnormal finish reasons. Only a complete valid
intent reaches the API. MVC tests verify invalid user input returns 400, invalid model
output returns 502 and provider transport errors return 503. Error responses and logs
do not reveal rejected model content. Tests require no real credentials or model calls.

# Milestone 4 — Deterministic simulation

Start with the `simulator` profile and call `POST /api/simulation/reset` before each
scenario. These scenarios use real Java services and no AI model.

| Scenario | Operations | Expected result |
|---|---|---|
| Normal inspection | Create Alpha → SECTOR_B, INSPECTION, returnHome=true; execute returned ID | COMPLETED, Alpha at BASE with 59%, no anomaly |
| One-way patrol | Create Charlie → SECTOR_C, PATROL, returnHome=false; execute | COMPLETED, Charlie at SECTOR_C with 49%, no inspection result |
| Inspection anomaly | Create Alpha → SECTOR_C, INSPECTION, returnHome=true; execute | COMPLETED, simulated vehicle finding, Alpha at BASE with 47% |
| Battery changed after planning | Create Alpha → SECTOR_B with return; inject BATTERY_DROP amount=60 for Alpha; execute | FAILED, battery stays 22%, position stays BASE, no inspection |
| Weather and position changed | Create Alpha → SECTOR_B with return; move Alpha to SECTOR_A; inject STRONG_WIND; execute | Route recalculated to 44 movement points plus 3 activity; completes at BASE with 28% |
| Hardware unavailable | Inject GPS_LOST, MOTOR_WARNING or COMMUNICATION_LOST for Alpha; attempt movement or mission | Direct move returns 409; mission becomes FAILED with no movement or extra consumption |
| GPS degradation | Inject GPS_DEGRADED for Alpha | GPS is DEGRADED; this technical simulator still permits movement |
| Repeat execution | Execute a completed mission again | 409; no additional battery consumption |
| Reset | Inject faults and create missions, then reset | Initial fixtures restored; missions and history empty |
| Old mission after reset | Create a mission, reset, create another, then execute the old ID | 404; the new mission and all drones remain unchanged |
| Misspelled event field | Send BATTERY_DROP with `ammount: 60` instead of `amount: 60` | 400; battery and event history remain unchanged |

Read `/api/simulation/world` to inspect all state and event history. BATTERY_DROP
clamps at zero; GPS_DEGRADED never repairs LOST GPS. A reset clears both faults.
Mission IDs are not reused across resets in a running world; always use the ID
returned by creation. Unknown JSON fields are rejected before mutations.
The current world uses SECTOR_A/B/C, not the BRAVO name from the earlier standalone
extraction example. Commands intended for later simulator use should name these IDs.

Execution holds a shared world lock for the complete operation. Concurrent events
are applied before or after execution; this milestone has no mid-flight timeline.
`DroneWorldTest` also verifies immutable snapshots, exact route costs, rejected inputs,
concurrent battery updates and single execution under concurrent requests.
`SimulationControllerTest` verifies the same services through MVC, including the
standalone profile, Swagger and absence of AI beans when credentials are blank.

The following scenarios involve safety policies or agent orchestration planned for later milestones.

---

# Scenario 1 — Normal mission

Drone Alpha:
battery: 80%
state: READY

Weather:
wind: 10 km/h

Command:
"Send Alpha to inspect Bravo and return home."

Expected:
mission accepted

---

# Scenario 2 — Low battery

Drone Alpha:
battery: 12%

Command:
"Send Alpha to Bravo."

Expected:
MissionSafetyValidator rejects execution.

---

# Scenario 3 — Inspection anomaly

Sector Bravo inspection image contains a vehicle.

Expected:
InspectionResult.anomaly = true

# Milestone 5 — Live world tools

With the normal application profile and OpenAI configured:

1. Reset the world through `POST /api/simulation/reset`.
2. Ask `POST /api/agent/chat`:
   `{"message":"Can Alpha inspect SECTOR_B and return home? Check current status, weather and route."}`
3. Expected tool selection: `getDroneStatus("alpha")`, `getWeather()`,
   `getSector("SECTOR_B")`, and `calculateRoute("alpha","SECTOR_B",true)`.
   Ordering and grouping may differ. Initial facts: battery 82%, wind 12 km/h,
   route 6 km / 20 battery points for movement, plus 3 for inspection.
   The answer must not claim that a mission was approved or executed.
4. Inject `{"type":"BATTERY_DROP","droneId":"alpha","amount":20}` through
   `POST /api/simulation/events`. Repeat the question. The new battery is 62%.
   Asking the agent must not change the simulator state.
5. Ask for a nonexistent drone. Expect a lookup error followed by an explanation
   or a request for a valid identifier, not invented telemetry.
6. Ask the agent to execute a mission. It must explain its read-only scope.

Automated coverage: `WorldToolsTest` exercises all seven callbacks against real
in-memory services, generated schemas, fresh state, read-only behavior, alert limits
and invalid arguments. `AiDroneMissionCommanderApplicationTests` scripts a tool-call
response from a local HTTP provider, then inspects the real OpenAI client's next
request for the four Java tool results (including battery 62%). It also covers
unknown IDs, malformed arguments, unknown execution tools, input validation and
definition discovery. These deterministic tests verify integration, not the live
model's choice of tools or the quality of its answer; steps above check those manually.

# Milestone 6 — Observe repeated model calls

Send the milestone 5 mission question to `POST /api/agent/chat`, then follow its
generated request ID in `AgentIterationLogger` logs. Every model invocation has
a numbered START and RESPONSE (or FAILED). A response requesting tools is followed
by another invocation containing their results. The number and grouping of calls
depend on the model; there is no fixed production sequence.

Deterministic coverage in `WorldAgentLoopTest` scripts three responses: first a
drone lookup, then weather and route requests together, then an answer. It verifies
the tool IDs, arguments, accumulated results and unchanged simulator state. Another
case corrects an unknown drone after receiving NOT_FOUND. Tests also check isolated
request counters and sanitized failure logs.

The manual-cycle test invokes ChatModel, ToolCallingManager and ChatModel explicitly,
and compares its follow-up conversation with the automatic advisor's conversation.
The endpoint remains automatic; this test is an educational comparison, not a
second production execution mode. All these tests run without a real provider.

# Milestone 7 — Explicit mission context

Create an inspection mission for Alpha and SECTOR_B using the Simulation API.
POST to `/api/agent/chat` with its returned `missionId`, a UUID `conversationId`,
and a request to summarize the selected mission. Expect the same conversation ID
in the reply. The model receives the simulator inventory and selected mission once,
before any tool calls. Tools remain available to verify current facts.

Repeat after completing the mission through the Simulation API: a new request's
context must reflect COMPLETED. Repeat with the same conversation ID and no
missionId: context must contain no selected mission. Since milestone 8, earlier
conversational messages remain available, but earlier application snapshots do not.
Reset the simulator and try the old missionId: expect 404 without a provider call.

With the dev profile, check that MissionContextAdvisor precedes ToolCallingAdvisor
and AgentIterationLogger follows it. The pipeline log runs once; iteration logs run
per model invocation, with matching request/conversation IDs and no raw payloads.

Automated coverage: WorldAgentContextTest checks ordering, one context message per
round, fresh state, concurrent selections, options and literal text preservation,
explicit mission selection, dev-profile activation and sanitized logs.
AiDroneMissionCommanderApplicationTests checks the HTTP contract, generated IDs,
selected mission data sent to the local provider, invalid fields and missing missions.

# Milestone 8 — Isolated dialogue and fresh state

Reset the world and clear two conversation IDs. Send "We are monitoring Alpha."
in A and "We are monitoring Charlie." in B. Ask "How much battery does it have?"
in each: expect Alpha (82%) and Charlie (67%) respectively. Inject BATTERY_DROP 20
for Alpha and repeat in A: expect 62%, read through a tool rather than copied from
an earlier answer.

Inspect GET /api/agent/conversations/{conversationId}/messages: only user and final
assistant text should be stored. No system/application context or Tool messages.
DELETE A's messages must not affect B or simulator state; resetting the world must
not clear dialogue history. A subsequent ambiguous question in cleared A should
ask for a drone identifier.

ConversationMemoryTest verifies filtering, the 20-message window, eviction, rollback
and serialized same-ID turns. AiDroneMissionCommanderApplicationTests verifies
separate provider request histories, fresh tool data, inspect/delete endpoints and
provider failure preserving previous history. Live model reference resolution
remains a manual check; automated model responses are scripted locally.

# Milestone 9 — Retrieve procedures without chat

In the normal profile, list GET /api/knowledge/documents and verify the six bundled
procedures with source, title, type and topic. Since milestone 10, the first search automatically ingests the corpus. Search
through POST /api/knowledge/search (or explicitly build POST /api/knowledge/index):

- “What should I do when energy is running low?”: inspect battery guidance.
- “The drone can no longer determine its position”: inspect GPS guidance.
- Add type SAFETY and topic BATTERY: only the battery document can match.
- Set type PROCEDURE with topic BATTERY: no document matches both constraints.
- Increase similarityThreshold: fewer than topK matches is valid.

Live semantic relevance is a manual check; exact scores and ranking are not fixed.
KnowledgeSearchServiceTest uses synthetic vectors with the real SimpleVectorStore
for deterministic ranking, thresholds, filters, validation and rebuild rollback.
AiDroneMissionCommanderApplicationTests uses a local HTTP provider with dummy keys
to verify the actual embedding client, scored REST results, input rejection and a
429 during rebuilding preserving the previous index. Every provider request in this
flow targets /v1/embeddings, never chat completions. Retrieval does not affect the
simulator, conversation memory or mission approval.

# Milestone 10 — Repeatable ETL and automatic loading

In Swagger, compare GET /api/knowledge/documents with GET /api/knowledge/chunks.
Both are local previews with no provider calls. Inspect parent documentId, source,
chunkIndex, chunkCount and stable chunk IDs. First search should ingest and retrieve
chunks automatically, without a preliminary /index request. Repeat POST /index:
updated=false and no embedding requests. Use force=true to rebuild deliberately.

KnowledgeSearchServiceTest checks deterministic IDs, retained source text (including
short final chunks), normalized line endings, inherited metadata and fingerprinting.
A temporary resource corpus exercises changed files: successful ingestion removes
obsolete chunks; failure preserves the old snapshot and permits retry. Concurrent
first searches embed the corpus once; searches during rebuilding use the old index.
The HTTP integration test covers preview, lazy ingestion, skipped repeated ingestion,
forced rebuild failure, provider error mapping and retrieval of chunk metadata.
Live semantic quality remains a manual check with the real embedding model.
