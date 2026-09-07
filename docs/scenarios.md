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
