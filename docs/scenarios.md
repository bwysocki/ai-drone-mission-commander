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
6. Compare the two instruction variants described in article-notes, restarting
   after changing the prompt resource. Compare answer structure and missing facts.

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
