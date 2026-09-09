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

The following scenarios are planned for later milestones.

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
