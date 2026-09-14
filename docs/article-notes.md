# Milestone 7 — Advisors

Tool calling already uses an advisor. This milestone adds application context
before that loop and makes the composition visible. The goal is to keep context
enrichment separate from the controller, tool implementations and model call.

## One context message per request

`MissionContextAdvisor` reads an immutable simulator snapshot and adds a system
message containing known drone and sector IDs and an optional selected mission.
Selection is explicit through `missionId`; it never picks another caller's latest
mission. The snapshot describes the request's starting context. Tools still read
live telemetry and mission state.

The advisor preserves existing system instructions, literal user text and options:

```java
return chain.nextCall(request.mutate()
        .prompt(new Prompt(messages, request.prompt().getOptions())).build());
```

Its order places it immediately before the automatic tool advisor:

```java
public int getOrder() {
    return ToolCallingAdvisor.DEFAULT_ORDER - 1;
}
```

This means the context is added once and carried into later tool rounds.
Putting an enrichment advisor inside the loop would make it run for each model call.

## Identifiers are not memory

The agent request now accepts `conversationId`, an optional UUID, and `missionId`.
The response returns the supplied conversation ID or a generated one. Each request
also has a separate generated request ID for tracing its iterations.

These values travel through the advisor context as `AgentRequestContext`, rather
than being interpolated into the user prompt. Request and conversation IDs are
not sent as model messages. The selected mission's data is deliberately sent.

Reusing a conversation ID does not recover previous messages or a previous
mission selection. That behavior belongs to milestone 8. Unknown selected missions
return 404 before contacting the model; malformed context fields return 400.

## Inspecting the chain

Enable the `dev` profile to register `DevelopmentLoggingAdvisor` and its DEBUG log.
It prints names and order values, without raw conversation data:

```text
DevelopmentLoggingAdvisor
  → MissionContextAdvisor
    → ToolCallingAdvisor
      → AgentIterationLogger
        → model call
```

The development advisor surrounds the request once, while the iteration logger
runs inside the tool loop. Lower order values run first; responses unwind the chain.
The built-in tool advisor's displayed name is `Tool Calling Advisor`; the terminal
model advisor is named `call`. No memory or RAG advisor has been registered yet.

## Demonstration through Swagger UI

Start with OpenAI configured:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

1. Open `http://localhost:8080/swagger-ui/index.html`. Under **Simulation**, execute
   `POST /api/simulation/reset`, then create a mission with
   `POST /api/simulation/missions`:

   ```json
   {"droneId":"alpha","type":"INSPECTION","targetSector":"SECTOR_B","returnHome":true}
   ```

2. Copy the returned mission ID. Under **World agent**, use **Try it out** on
   `POST /api/agent/chat`. Replace the entire body and paste that ID:

   ```json
   {
     "message": "Summarize the selected mission and check Alpha's current status.",
     "conversationId": "4ea95657-b281-4330-97c4-9894249bb992",
     "missionId": "<paste the returned mission ID>"
   }
   ```

3. Execute the request. The response should describe the selected inspection
   mission and return the same conversation ID. The console shows the advisor
   chain once and a separate iteration entry for each model call.
4. Repeat with the same conversation ID, omit `missionId`, and ask
   “Is a current mission selected in this request?” No mission should be selected.
   This demonstrates that correlation is separate from memory.
5. Select the mission again after resetting the simulator. Expect HTTP 404 and no
   model invocation for that request. Mission IDs are not reused by reset.

Tests verify that enrichment happens once across tool rounds, options remain
intact, mission snapshots refresh between requests, concurrent selections stay
separate and development logs omit payloads. HTTP tests use a local provider stub;
live model wording and tool selection can vary.

The lesson: advisors compose behavior around model calls. Their order determines
whether that behavior applies once to the request or repeatedly inside the loop.
