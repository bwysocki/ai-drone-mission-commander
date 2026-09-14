# AI Drone Mission Commander

A learning project built with Java and Spring AI, being developed to support
mission planning in a simulated drone environment.

## Requirements

- JDK 25 (`java -version` should report this version).
- Internet access for the initial build to download Maven and dependencies.
- An OpenAI API key for a standard application startup. The `simulator` profile
  runs the simulation and Swagger UI without a key.

The project includes Maven Wrapper, so a separate Maven installation is not required.
Run the following commands from the project root (Linux/macOS).
On Windows, use `mvnw.cmd` instead of `./mvnw`.

## Running the application

Set the API key as an environment variable in the terminal used to start the application:

```bash
export SPRING_AI_OPENAI_API_KEY="your-api-key"
./mvnw spring-boot:run
```

Replace `your-api-key` with your own key. Do not commit the key to the repository.
In PowerShell, set the variable with `$env:SPRING_AI_OPENAI_API_KEY="your-api-key"`.
To run from an IDE, add the same environment variable to the run configuration for
`AiDroneMissionCommanderApplication` and select JDK 25.

By default, the server starts at `http://localhost:8080`.
The log message `Started AiDroneMissionCommanderApplication` confirms a successful startup.
The application exposes the chat endpoints and Swagger UI described below.
There is no home page, so an HTTP 404 response at `/` is expected.
Press `Ctrl+C` to stop the application running in the terminal.

## Testing and building

```bash
./mvnw test
./mvnw clean install
```

The test suite separates fast checks from HTTP integration:

- Plain JUnit tests cover response mapping, provider error classification and safe
  logging. `ChatServiceTest` uses a mocked `ChatModel` with a real `ChatClient` to
  verify prompt construction, alternative system instructions, literal user input
  and stream cancellation without networking. Stream controller tests cover event
  ordering and failures using Reactor's `StepVerifier`.
- `ChatControllerTest` uses `@WebMvcTest` and a mocked `ChatService` to check routing,
  JSON validation, empty answers and error responses without creating an AI client.
- A small full-context suite in `AiDroneMissionCommanderApplicationTests` verifies real
  model/builder autoconfiguration, Swagger, both chat flows and representative HTTP
  and connection failures against a local provider stub. It also checks option
  overrides without leakage and incremental delivery from a streaming provider.

The `test` profile disables AI models by default. Only the integration tests enable
the chat model, override credentials with a dummy key and point it at localhost.
They also set both common and chat-specific `max-retries=0` and `timeout=2s` using
dynamic properties. Production retry settings are unchanged. Tests and builds require
neither a real API key nor a connection to OpenAI.

If you have Maven installed and configured to use JDK 25, you can also run
`mvn clean install`.

After building the project, you can run the application from the JAR file in a terminal
with `SPRING_AI_OPENAI_API_KEY` set:

```bash
java -jar target/ai-drone-mission-commander-0.0.1-SNAPSHOT.jar
```

The `test` profile is available only during tests and is not packaged with the application.

## Chat examples (Milestone 1)

### Try the endpoints in your browser

1. Start the application with `./mvnw spring-boot:run` and your API key configured.
2. Open [Swagger UI](http://localhost:8080/swagger-ui/index.html)
   (or [the shortcut](http://localhost:8080/swagger-ui.html)).
3. Expand `POST /api/chat` under **Chat** and click **Try it out**.
4. Keep the example question or edit `message`, then click **Execute**.
5. Inspect the response text, metadata and HTTP status. Repeat with `POST /api/chat/model`
   to compare the two invocation styles.

The OpenAI key is configured on the server; there is no key field in Swagger UI.
Executing a chat request uses the configured provider, just like calling it with curl.
Opening the documentation alone does not call the model.

The [OpenAPI JSON specification](http://localhost:8080/v3/api-docs) is available for
API client tooling. Swagger UI displays request/response schemas and the documented
HTTP 400, 502 and 503 error cases.

### Terminal example

With the application running, send a question through `ChatClient`:

```bash
curl --fail-with-body http://localhost:8080/api/chat \
  -H 'Content-Type: application/json' \
  -d '{"message":"What should a drone operator check before an inspection mission?"}'
```

To compare this with a direct `ChatModel.call(Prompt)` invocation, send the same
request to `http://localhost:8080/api/chat/model`. Each request makes one model call.
Both endpoints are stateless and use the same system and user messages.

Example response (text and metadata vary with the provider):

```json
{
  "message": "Check battery, weather, GPS and the inspection area.",
  "metadata": {
    "id": "chatcmpl-example",
    "model": "your-model",
    "finishReason": "STOP",
    "promptTokens": 20,
    "completionTokens": 12,
    "totalTokens": 32
  }
}
```

Both endpoints require `message` to be a nonblank JSON string. Missing, null or blank
messages, numbers, booleans, arrays, objects and malformed JSON return HTTP 400
without calling the provider. A model response without text returns HTTP 502.

Provider failures return a sanitized Problem Detail JSON response:

- HTTP 502 for rejected provider requests, including authentication or permission errors.
- HTTP 503 for provider rate limits, provider HTTP 5xx responses and connection failures.

The response includes `status`, `title` (`AI provider error`) and a generic `detail`.
It does not expose the provider's raw error message or credentials. The SDK may retry
transient failures before the application returns the error.

The AI endpoints answer questions and extract intent. Simulated execution is exposed
separately through the Simulation endpoints described below.

### Diagnosing provider errors

The public HTTP 503 response groups provider HTTP 429, provider HTTP 5xx and transport
failures. To identify which occurred, inspect the application console for
`AI provider failure` after sending a request. For example:

```text
AI provider failure: providerStatus=429, providerCode=rate_limit_exceeded, exceptionType=RateLimitException, causeType=none
```

The handler logs the original HTTP status (or `n/a` for a transport failure), an
allowlisted provider code and exception class names. It does not log provider messages,
request content, headers or credentials. Unrecognized provider codes appear as `other`.
These fields distinguish failure categories without exposing sensitive error details.

OpenAI HTTP 429 can mean different limits. The recognized codes include:

| Provider code | What to check |
|---|---|
| `credit_balance_exhausted` | The organization's prepaid API credit balance |
| `organization_spend_limit_exceeded` | The configured organization spend limit |
| `project_spend_limit_exceeded` | The configured project spend limit |
| `organization_usage_limit_exceeded` | The organization's OpenAI-assigned usage limit |
| `rate_limit_exceeded` / `slow_down` | Request rate; respect `Retry-After` when supplied |

Billing and quota errors require correcting the relevant credits or limits; repeated
requests alone do not resolve them. See the [official OpenAI error guide](https://developers.openai.com/api/docs/guides/error-codes).

The chat model uses the starter's default model unless overridden with
`SPRING_AI_OPENAI_CHAT_MODEL`. Actual provider calls require a valid key
and access to the selected model.

## Prompts, options and streaming (Milestone 2)

All three chat paths use the system instructions in
[`mission-assistant.st`](src/main/resources/prompts/mission-assistant.st).
Edit this resource and restart the application to experiment with response style.
User input remains a separate `UserMessage`, including literal JSON or braces.

The default completion budget is 2048 tokens, configurable with
`SPRING_AI_OPENAI_CHAT_MAX_COMPLETION_TOKENS`. The model uses the starter default
unless `SPRING_AI_OPENAI_CHAT_MODEL` is configured. Temperature is left unset;
supported options depend on the selected model. Completion budgets can include
reasoning tokens, so a very small budget may leave no visible answer.

Both POST endpoints accept optional overrides:

```bash
curl --fail-with-body http://localhost:8080/api/chat \
  -H 'Content-Type: application/json' \
  -d '{"message":"Give me a short preflight checklist.","options":{"maxCompletionTokens":1024}}'
```

Add `"model":"your-accessible-model"` inside `options` to select a model for that
request. Omitted fields retain the configured defaults; overrides do not affect
later requests. Blank models and nonpositive or noninteger token limits return 400.

The POST endpoints use `.call()` and return a complete answer with metadata.
`GET /api/chat/stream` uses `.stream().chatResponse()` to validate completion metadata
and deliver text fragments as SSE:

```bash
curl -N --get http://localhost:8080/api/chat/stream \
  --data-urlencode 'message=What should I check before sending Alpha to Bravo?' \
  --data-urlencode 'maxCompletionTokens=2048'
```

It also accepts an optional `model` query parameter. Example wire output:

```text
event:delta
data:{"text":"Check "}

event:delta
data:{"text":"battery and weather."}

event:done
data:{}

```

Concatenate each `delta.text` exactly, preserving spaces. Fragments are not
necessarily single tokens. Streaming does not include the POST response metadata.
Success ends with `done`; a provider failure ends with `error`, including after
partial output. Its JSON contains an `error` Problem Detail with a sanitized
`status` and `detail`. An empty or whitespace-only stream produces an error with
status 502. A stream ending without a generation finish reason also produces 502,
even if the provider closes its HTTP response without a transport error.

Invalid query parameters return HTTP 400 before streaming. Provider failures are
encoded as SSE `error` events under HTTP 200, even if no text has arrived yet;
clients must inspect events rather than just the HTTP status. Partial text followed
by `error` is an incomplete answer. Close browser `EventSource` connections on
`done` or a server `error` event to avoid automatic reconnection and another call.

Use `Ctrl+C` to disconnect curl. Cancellation propagates upstream when Spring MVC
detects disconnection, typically on a subsequent write; detection is not immediate
while the provider is silent. The MVC async timeout is 120 seconds, configurable
with `SPRING_MVC_ASYNC_REQUEST_TIMEOUT`. A broken connection or timeout may end
without a terminal SSE event. Proxies can buffer SSE; disable their buffering if needed.

Swagger UI documents the streaming endpoint, but `curl -N` is the simplest way to
observe incremental arrival. See [scenarios](docs/scenarios.md) for the prompt
experiment and streaming checks.

## Structured mission intent (Milestone 3)

`POST /api/missions/intent` converts a single natural-language command into a typed
`MissionIntent`. It uses a dedicated extraction prompt and the configured chat model.
Try it under **Missions** in Swagger UI, or run:

```bash
curl --fail-with-body http://localhost:8080/api/missions/intent \
  -H 'Content-Type: application/json' \
  -d '{"message":"Send Alpha to Bravo, inspect the area and return home."}'
```

Expected response for that command:

```json
{
  "droneId": "alpha",
  "type": "INSPECTION",
  "targetSector": "BRAVO",
  "returnHome": true
}
```

Supported mission types are `INSPECTION` and `PATROL`. Identifiers contain letters,
digits, underscores or hyphens and start with a letter or digit. Java trims them,
lowercases the drone ID and uppercases the sector. This checks identifier format;
it does not confirm that a drone or sector exists. `returnHome` is false unless
returning home is requested.

The default mode includes the generated schema in the prompt. To compare it with
provider-native JSON Schema, send the same body to:

```text
POST /api/missions/intent?nativeOutput=true
```

Native mode requires a model supporting structured output. Both modes use Spring
AI's `BeanOutputConverter` and `ChatClient.responseEntity(...)`. The provider DTO
allows explicit nulls for unknown mission details, while Java rejects incomplete
output before creating a domain intent. This avoids asking a strict-schema model
to invent mandatory identifiers. Format instructions do not guarantee semantic
accuracy: inspect the extracted intent before using it in subsequent workflows.

Request validation returns 400 for missing/blank/non-string messages or an invalid
mode parameter. Rejected output returns a sanitized 502 (`Invalid AI output`):
malformed JSON, missing or extra fields, duplicate keys, wrong value types, unknown
mission types, invalid identifiers, nulls, or generation ending without `STOP`.
Missing or ambiguous mission details can also result in 502; provide a more explicit
command. Provider failures keep the existing 502/503 classification. Raw model text
and parser error messages are not included in the error response.

There is no automatic model-output repair loop. Each extraction makes one logical
model call; the SDK's configured transport retries still apply. The new tests use
mocks and a localhost provider with dummy credentials, never a real OpenAI account.

`MissionPlan`, `MissionAssessment`, `MissionReport` and their enums are immutable
contracts prepared for later milestones. This endpoint only extracts intent. It
does not generate safety decisions, inspect telemetry, create execution reports
or execute missions.

See [scenarios](docs/scenarios.md) for structured extraction examples and checks.

## Drone World Simulator (Milestone 4)

Run the simulator without an OpenAI key:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=simulator
```

Open [Swagger UI](http://localhost:8080/swagger-ui/index.html) and use the
**Simulation** group. This profile disables AI services and their endpoints.
A normal startup with an API key exposes both Simulation and AI endpoints; the AI
endpoints are not connected to mission execution yet.

The world lives in memory. Restarting the application or calling
`POST /api/simulation/reset` restores these fixtures and clears missions and events:

| Drone | Battery | Position | State |
|---|---:|---|---|
| alpha | 82% | BASE `(0,0)` | READY |
| bravo | 41% | SECTOR_A `(2,0)` | IDLE |
| charlie | 67% | BASE `(0,0)` | READY |

Coordinates are kilometres on a flat map. The sectors are `SECTOR_A (2,0)`,
`SECTOR_B (0,3)` and `SECTOR_C (4,3)`. Weather starts at 12 km/h wind, no rain
and GOOD visibility. IDs are case-insensitive on simulation lookups. `BRAVO`, used
in earlier extraction examples, is not a sector alias: use `SECTOR_B` in commands
intended for this world.

### Inspect the world and quote a route

```bash
curl http://localhost:8080/api/simulation/world
curl 'http://localhost:8080/api/simulation/routes?droneId=alpha&sectorId=SECTOR_B&returnHome=true'
```

Individual reads are available at `/drones`, `/drones/{id}`, `/weather`, `/sectors`,
`/sectors/{id}`, `/missions` and `/missions/{id}`, all under `/api/simulation`.

Routes use straight lines. For each leg, battery consumption in percentage points is:

```text
ceil(distanceKm × 2 × (1 + windKmh / 20))
```

The route quote sums costs rounded separately for each leg. It includes movement
only; an inspection adds 3 points and a patrol adds 2. Rain and visibility are
reported but do not affect this simplified energy model.

### Create and execute a mission

```bash
curl http://localhost:8080/api/simulation/missions \
  -H 'Content-Type: application/json' \
  -d '{"droneId":"alpha","type":"INSPECTION","targetSector":"SECTOR_B","returnHome":true}'

curl -X POST http://localhost:8080/api/simulation/missions/mission-1/execute
curl http://localhost:8080/api/simulation/drones/alpha
```

Replace `mission-1` in the example with the ID returned by creation. The mission
counter continues across resets within a running application, so old mission IDs
return 404 rather than referring to newly created missions. Restarting the application
starts a fresh in-memory world.
Creation returns a `CREATED` mission and does not move or reserve a drone. Execution
recalculates the route using the current position and weather, then checks hardware
availability and the complete energy budget, including activity and return.

For Alpha inspecting SECTOR_B and returning home in the initial world, movement
costs 20 points and inspection costs 3. The final battery is **59%**, position BASE,
state READY. Without returnHome, the drone stays at the target in IDLE state.

A completed inspection of SECTOR_C reports a fixed simulated vehicle anomaly;
SECTOR_A and SECTOR_B have no anomalies. Patrol missions do not create inspection
results. These observations are fixtures, not image analysis.

Execution is synchronous and atomic. It returns HTTP 200 with mission state
`COMPLETED` or `FAILED`; inspect the state rather than just the HTTP status. A failed
precondition leaves the drone unchanged. Re-executing a completed or failed mission
returns 409, without charging the battery again. Create a new mission to retry.
`RUNNING` and `IN_MISSION` are internal transition states, not polling phases in this
instantaneous simulator. Concurrent events are applied before or after execution,
not halfway through a flight.

### Inject an event or move a drone directly

```bash
curl http://localhost:8080/api/simulation/events \
  -H 'Content-Type: application/json' \
  -d '{"type":"BATTERY_DROP","droneId":"alpha","amount":60}'

curl http://localhost:8080/api/simulation/events \
  -H 'Content-Type: application/json' \
  -d '{"type":"STRONG_WIND","amount":45}'

curl -X POST 'http://localhost:8080/api/simulation/drones/charlie/move?sectorId=SECTOR_A'
curl -X POST http://localhost:8080/api/simulation/drones/charlie/return-home
```

| Event | Scope | Effect |
|---|---|---|
| BATTERY_DROP | Required droneId | Subtract amount (1–100, default 20), clamped at zero |
| GPS_DEGRADED | Required droneId | Mark degraded GPS; does not repair LOST GPS |
| GPS_LOST | Required droneId | Prevent movement and mission execution |
| STRONG_WIND | World; omit droneId | Set wind (30–200 km/h, default 45), increasing flight costs |
| MOTOR_WARNING | Required droneId | Prevent movement and mission execution |
| COMMUNICATION_LOST | Required droneId | Prevent movement and mission execution |

Omit `amount` for events other than battery and wind. Event history is included in
`/world`; sequence numbers replace timestamps so replay is deterministic. Battery
events record the actual number of points removed. A reset clears faults and history.

Invalid input returns 400, unknown objects 404, and blocked direct movement or
invalid mission transitions 409. JSON enums must be strings, booleans must be JSON
booleans, and duplicate or unknown JSON fields are rejected. For example, a misspelled
`ammount` returns 400 instead of silently applying the default battery drop.
Domain status READY means idle at
BASE; inspect battery and fault flags before assuming a drone can operate.

The technical checks here prevent impossible simulator operations. Mission policies
such as battery reserves, weather limits and authorization belong to the later
`MissionSafetyValidator` milestone. The simulator does not operate real hardware.

See [Milestone 4 notes](docs/article-notes.md) and [scenarios](docs/scenarios.md).

## Milestone 5: ask the agent about the simulated world

Start the application with the standard profile and a configured OpenAI key.
Use **World agent** in [Swagger UI](http://localhost:8080/swagger-ui/index.html),
or send a request:

```bash
curl http://localhost:8080/api/agent/chat \
  -H 'Content-Type: application/json' \
  -d '{"message":"Can Alpha inspect SECTOR_B and return home? Check its current status, weather and route first."}'
```

This endpoint accepts the same optional `options` object as `/api/chat` and returns
the same `message` and `metadata` structure. The model can call seven read-only tools:
`getDroneStatus`, `getFleetStatus`, `getWeather`, `getSector`, `calculateRoute`,
`getMission` and `getRecentAlerts`. Inspect their generated descriptions and JSON
input schemas without a model call:

```bash
curl http://localhost:8080/api/agent/tools
```

Tool arguments are validated against those schemas before invoking Java methods.
Unknown objects and invalid arguments produce sanitized tool results that the model
can explain or correct. Unrecoverable tool orchestration failures return HTTP 502;
provider failures retain the existing HTTP 502/503 handling.

Change the world through the Simulation API, then ask again to read the new state.
Each request is independent: no conversation memory is implemented yet.
Route estimates cover movement only; an inspection consumes 3 additional battery
points and a patrol 2. Recent alerts are event history, newest first, with a required
`limit` from 1 to 20. They are not a list of currently active faults.

The agent cannot create or execute missions, move drones or inject events. Its answer
is advice, not safety approval. Tools read the current state individually, so a quote
can become stale if the world changes. A request may make multiple provider calls
while Spring AI executes selected tools and sends their results back to the model.
Response ID, model and finish reason describe the final response; Spring AI accumulates
reported token usage across the tool-calling turns.

The `simulator` profile keeps these AI endpoints disabled. Automated tests use
in-memory services and a small local provider stub with dummy credentials; they do
not call a real model.

## Milestone 6: observe the agent loop

The existing `POST /api/agent/chat` endpoint logs each model invocation with a
generated `requestId`, an `iteration` counter and `START` / `RESPONSE` / `FAILED`
phases. `priorToolResults` counts accumulated tool results in the prompt;
`toolCalls` counts tools requested by that response. Several tools can be requested
within one iteration. Each HTTP request starts its own counter at 1.

Logs contain counts rather than prompts, tool arguments or returned data. Follow
one request ID when multiple requests run at once. To disable these informational
traces, set `logging.level.pl.stalostech.ai_drone_mission_commander.agent.AgentIterationLogger=WARN`.

`WorldAgentLoopTest` demonstrates multiple automatic rounds, recovery after an
invalid lookup and a single manual cycle with `ToolCallingManager`. The manual
cycle exists only in tests; production continues to use Spring AI's automatic loop.

```bash
./mvnw test -Dtest=WorldAgentLoopTest
```

## Milestone 7: mission context and advisor composition

`POST /api/agent/chat` accepts optional `conversationId` (UUID) and `missionId`
fields in addition to `message` and `options`. Successful replies retain
`message` and `metadata`, and add `conversationId`; a UUID is generated when omitted.
The ID correlates requests and logs. It does not store messages or remember the
selected mission. Supply `missionId` again on each request that needs it.

`MissionContextAdvisor` adds an application context message once, before the tool
loop: the simulated environment, known drone/sector IDs and the explicitly selected
mission's snapshot. It does not select the latest mission automatically.
Telemetry still comes from tools; a request-start snapshot can become stale.

Create a mission through `POST /api/simulation/missions`, copy its returned ID,
then use **World agent** in Swagger:

```json
{
  "message": "Summarize the selected mission and check the drone's current status.",
  "conversationId": "4ea95657-b281-4330-97c4-9894249bb992",
  "missionId": "<paste the returned mission ID>"
}
```

An unknown or reset mission returns HTTP 404 before a model call. Invalid context
fields return HTTP 400. Omitting `missionId` selects no mission, even when the
conversation ID was used previously.

To inspect advisor composition, start with OpenAI configured and the `dev` profile:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

The development log shows advisor names and their order, plus request/conversation
IDs. It contains no prompt, answer, tool arguments or mission payload. The chain is:

```text
DevelopmentLoggingAdvisor (dev only)
  → MissionContextAdvisor
    → ToolCallingAdvisor
      → AgentIterationLogger
        → model call
```

Lower order values run first on the request; responses pass back in reverse.
The context and development advisors run once per request. The iteration logger
runs inside the tool loop. In Spring AI's log names, the tool advisor appears as
`Tool Calling Advisor` and the terminal model advisor as `call`.
Memory and RAG advisors are not part of this milestone. The standalone `simulator`
profile keeps AI advisors and endpoints disabled.
