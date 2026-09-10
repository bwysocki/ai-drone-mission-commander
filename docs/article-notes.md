# Milestone 5 — Tool Calling

## Problem

Until now, the model could give advice and extract mission intent, but it could not
see the simulator. A question about Alpha's battery required access to Java state.
This milestone adds seven read-only tools and a dedicated `POST /api/agent/chat`.

## Spring AI concept

`@Tool` describes a Java method to the model; `@ToolParam` documents its arguments.
Spring AI generates the JSON input schema and binds the model's JSON arguments to
the method parameters. For example, in `tools/DroneTools.java`:

```java
@Tool(description = "Read the current status of every simulated drone. Use this to discover drone identifiers.")
public List<DroneStatus> getFleetStatus() {
    return drones.list();
}
```

`WorldToolRegistry` builds the callbacks using `ToolCallbacks.from(...)`.
`WorldAgentService` attaches only these callbacks to its ChatClient:

```java
client = builder.defaultSystem(prompt.getContentAsString(StandardCharsets.UTF_8))
        .defaultToolCallbacks(tools.callbacks())
        .build();
```

In Spring AI 2.0, ChatClient automatically uses ToolCallingAdvisor for this flow:
the model requests tools, Java executes them, and another model request includes
their results. We use that built-in behavior here. Explicit loop configuration and
iteration analysis belong to milestone 6.

## Boundaries and failures

The tools expose drone/fleet status, weather, sectors, route quotes, existing
missions and recent events. They cannot mutate the simulator. A route quote is not
mission authorization, and its battery estimate covers movement rather than the
inspection/patrol activity. Event history is not proof of an active fault.

`ValidatedToolCallback` validates arguments against the generated schema before
method binding. Missing fields, incorrect types and malformed JSON are rejected.
Expected failures return fixed JSON error codes (`NOT_FOUND`, `INVALID_ARGUMENTS`);
unexpected callback failures return `TOOL_UNAVAILABLE`. Raw arguments and exception
messages are not included in these errors or the wrapper's logs.

Tool resolution fallback is disabled. An unregistered tool name cannot discover
another application bean; unrecoverable orchestration errors become HTTP 502.

## Inspecting and testing

`GET /api/agent/tools` exposes the names, descriptions and input schemas without a
provider call. Most tests invoke callbacks against the real, deterministic in-memory
simulator. A few integration scenarios use the existing local HTTP provider stub
to inspect actual tool declarations, tool-call arguments and subsequent tool results
sent by the OpenAI client. No real credentials or external model are needed.

## Lessons learned

Tool calling makes the model a consumer of application data; it does not give it
authority to execute missions. The tool list is an explicit capability boundary.
The descriptions also matter: route costs, identifiers and historical alerts must
have clear semantics so the model can interpret the data.

One chat request can involve several provider requests. Spring AI accumulates reported
token usage across the tool-calling turns: the integration scenario returns 64 total
tokens from two responses reporting 32 each. Response ID and finish reason come from
the final response. Every request starts without conversation memory. Stubbed integration tests prove the wiring;
they cannot prove that a live model will always choose the right tools.

## How to present this milestone

Use a short demonstration built around one question: **can the assistant read a
change in the simulated world?** Start with observable behavior, then explain the
Java methods and Spring AI integration behind it. Allow about 5–8 minutes.

### 1. Prepare a known starting point

Run the application with OpenAI configured as described in the
[README](../README.md#running-the-application). Use the normal profile; the
`simulator` profile disables the agent endpoints. Open
`http://localhost:8080/swagger-ui/index.html` and expand **Simulation** and
**World agent**. The following curl commands are equivalent to Swagger's
**Try it out** actions.

Reset the shared simulator before the demonstration:

```bash
curl -X POST http://localhost:8080/api/simulation/reset
curl http://localhost:8080/api/simulation/drones/alpha
```

Point out Alpha's `batteryPercent: 82`. This is the reference value supplied by
Java. Reset also clears any previously created missions and injected events.

### 2. Let the model read that state

```bash
curl http://localhost:8080/api/agent/chat \
  -H 'Content-Type: application/json' \
  -d '{"message":"Read the current status of drone alpha. What is its battery percentage?"}'
```

The expected answer reports **82%**, based on `getDroneStatus`. Explain that Java
provides the value and the model turns it into a natural-language answer. The API
returns `message` and `metadata`; it does not return a tool execution trace.

### 3. Change the world and repeat the same question

```bash
curl http://localhost:8080/api/simulation/events \
  -H 'Content-Type: application/json' \
  -d '{"type":"BATTERY_DROP","droneId":"alpha","amount":20}'

curl http://localhost:8080/api/agent/chat \
  -H 'Content-Type: application/json' \
  -d '{"message":"Read the current status of drone alpha. What is its battery percentage?"}'
```

Now the expected answer is **62%**. Confirm it with
`GET /api/simulation/drones/alpha`. Keep the wording of the question unchanged:
this makes the effect of fresh tool data easy to see. Each request is independent;
this demonstration does not rely on chat memory.

Suggested narration: “The question stayed the same, but the world changed. The
assistant can read that change through a Java tool.”

### 4. Expand to the mission question

```bash
curl http://localhost:8080/api/agent/chat \
  -H 'Content-Type: application/json' \
  -d '{"message":"Can Alpha inspect SECTOR_B and return home? Check current drone status, weather, sector and route. Explain the battery estimate and any limitations."}'
```

This gives the model a reason to combine `getDroneStatus`, `getWeather`,
`getSector` and `calculateRoute`. Tool order and grouping can vary.
With no other changes since the reset and battery drop, check the answer against:

| Fact | Expected value |
| --- | --- |
| Current Alpha battery | 62% |
| Wind | 12 km/h |
| Round-trip route distance | 6 km |
| Movement battery estimate | 20 percentage points |
| Additional inspection cost | 3 percentage points |
| Estimated remaining battery after that hypothetical mission | 39% |

The 39% figure is an estimate, not an executed mission result. The actual battery
must remain 62%, and `GET /api/simulation/missions` must still return an empty list.
Describe the answer as advice based on current data, not deterministic safety
approval. Quotes can become stale as the simulator changes.

### 5. Show the small amount of code behind the behavior

Open these files in this order:

1. [DroneTools.java](../src/main/java/pl/stalostech/ai_drone_mission_commander/tools/DroneTools.java):
   show `getDroneStatus`, `@Tool`, `@ToolParam` and the delegation to the simulator.
2. `GET /api/agent/tools): expand the definition of `getDroneStatus` to show the
   name, description and JSON input schema the model receives. `inputSchema` is
   a JSON-encoded string. These are definitions, not evidence of executed calls.
3. [WorldToolRegistry.java](../src/main/java/pl/stalostech/ai_drone_mission_commander/tools/WorldToolRegistry.java):
   show `ToolCallbacks.from(...)`, the seven explicitly selected tools and the
   validated callbacks. Mention the 1–20 bound on `getRecentAlerts`.
4. [WorldAgentService.java](../src/main/java/pl/stalostech/ai_drone_mission_commander/agent/WorldAgentService.java):
   show `defaultToolCallbacks(tools.callbacks())` and the regular ChatClient call.

Use this flow to connect the snippets:

```text
User question → model requests a tool → Java reads the simulator
              → tool result goes back to the model → answer
```

For the article, pair the before/after battery responses with the tool method and
ChatClient registration snippets. This keeps the main explanation focused; introduce
validation and error handling after the reader understands the request path.

### 6. Demonstrate one failure and explain the boundary

Ask: “Read the current status of drone missing-drone.”
The lookup produces `NOT_FOUND`; the model should explain that the drone was not
found or ask for a valid identifier. It must not invent battery or position values.
Then ask it to execute a mission and explain that no execution tool is registered.
Actual mission execution remains available only through the separate Simulation API.

Do not promise exact wording or tool selection from a live model. If it skips a
tool or invents a fact, compare the answer with the simulator and show the limitation
honestly.

### Deterministic fallback and evidence of tool execution

If the provider is unavailable, show the integration scenario instead:

```bash
./mvnw test -Dtest=AiDroneMissionCommanderApplicationTests#agentUsesLiveToolResultsThroughTheRealOpenAiClient
```

Open that test and show the scripted tool-call response, the captured second
provider request, and the assertions for battery 62%, wind 12 and route cost 20.
It uses real Java tools and the real OpenAI client against a local HTTP stub with
dummy credentials. Clearly label this as a wiring test: the stub chooses the tool
calls and supplies the final answer, so it does not evaluate model reasoning.

For a live demonstration of actual calls, set IDE breakpoints in the tool methods.
Avoid enabling raw provider/tool logging just for the presentation; the application
intentionally sanitizes failure logs. Detailed loop instrumentation belongs to
milestone 6.
