# Milestone 6 — The Agent Loop

Milestone 5 already introduced the agent loop. When the model requests a tool,
Spring AI executes the Java method, adds the result to the conversation and calls
the model again. This milestone makes that behavior visible.

## What repeats?

```text
User → model → AssistantMessage with tool calls
     → Java tools → ToolResponseMessage → model → ... → final answer
```

One model response can request several tools. It can also request another tool
after reading an earlier result. Both cases are covered by `WorldAgentLoopTest`.
The model's selection is scripted in tests; the ChatClient loop and simulator
tools are real.

Spring AI's `ToolCallingAdvisor` manages this repetition automatically. The
production endpoint remains `POST /api/agent/chat`; no manual loop is exposed.

## Seeing each iteration

`WorldAgentService` now creates one `AgentIterationLogger` per request:

```java
var request = client.prompt().messages(new UserMessage(message))
        .advisors(new AgentIterationLogger());
```

The logger runs just after `ToolCallingAdvisor` in advisor order, inside its loop.
It logs each model invocation with a generated request ID, iteration number and
counts. Prompts, answers, tool names, arguments and results are not logged.
An abbreviated example, with timestamps and request IDs omitted:

```text
iteration=1, phase=START, priorToolResults=0
iteration=1, phase=RESPONSE, toolCalls=1
iteration=2, phase=START, priorToolResults=1
iteration=2, phase=RESPONSE, toolCalls=2
iteration=3, phase=START, priorToolResults=3
iteration=3, phase=RESPONSE, toolCalls=0
```

Here three model calls requested three tools across two rounds. The result count
is cumulative. A failed model invocation logs `phase=FAILED`; tool execution
failures retain milestone 5's sanitized handling.

## One manual cycle, for comparison

The test also performs the same cycle explicitly using `ToolCallingManager`:

```java
ChatResponse response = model.call(initial);
var execution = manager.executeToolCalls(initial, response);
var followUp = new Prompt(execution.conversationHistory(), initial.getOptions());
ChatResponse finalResponse = model.call(followUp);
```

It checks that the follow-up messages match those produced by the automatic loop.
This is a controlled, single-cycle exercise in test code, not a replacement for
the production advisor. A general manual loop would also need termination, limits,
error handling and usage accounting.

## Demonstration through Swagger UI

Start the application with OpenAI configured as described in the
[README](../README.md#running-the-application). Use the normal profile; the
`simulator` profile disables the agent endpoint. Keep the application console
visible and open [Swagger UI](http://localhost:8080/swagger-ui/index.html).

1. Under **Simulation**, expand `POST /api/simulation/reset`, select
   **Try it out**, then **Execute**. This restores the initial state and clears
   existing missions and events.
2. Under **World agent**, expand `POST /api/agent/chat` and select **Try it out**.
   Replace the entire request body with:

```json
{
  "message": "Can Alpha inspect SECTOR_B and return home? Check its current status, weather, sector and route before answering."
}
```

3. Click **Execute**. Swagger displays the final `message` and `metadata`.
   The intermediate tool calls appear as counts in the application logs, not in
   the HTTP response.
4. In the console, follow one `requestId`. A response with `toolCalls > 0` should
   be followed by another model invocation with additional `priorToolResults`.
   The model may request all four tools together or spread them across rounds;
   do not expect a fixed number of iterations.
5. Compare the answer with the initial simulator values: Alpha has **82%**
   battery, wind is **12 km/h**, and the round-trip route is **6 km**, costing
   **20 battery percentage points** for movement plus **3** for inspection.
   These are estimates for a hypothetical mission, not safety approval.
6. Execute `GET /api/simulation/drones/{id}` with `id = alpha` and
   `GET /api/simulation/missions`. Battery should still be **82%** and the mission
   list empty: the agent read the world without executing a mission.

Suggested narration: “One HTTP request can contain several model calls. Each time
the model asks for tools, Java executes them and Spring AI sends the results back.
Swagger shows the final answer; the console shows the iterations that produced it.”

The lesson: a tool-using agent repeatedly reads results and decides what to request
next. Java still controls the available operations. This milestone adds visibility
to that loop; conversation memory and broader advisor composition come later.
