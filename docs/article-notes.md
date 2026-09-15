# Milestone 8 — Chat Memory

Until now, conversation IDs only correlated requests. Now the agent can interpret
a follow-up such as “How much battery does it have?” using an earlier message:
“We are monitoring Alpha.”

## Add memory before the tool loop

The application uses Spring AI's MessageChatMemoryAdvisor with ConversationMemory,
a small wrapper around MessageWindowChatMemory:

```java
builder.defaultAdvisors(MessageChatMemoryAdvisor.builder(memory)
        .order(ToolCallingAdvisor.DEFAULT_ORDER - 1).build());
```

Every request passes its UUID through the standard advisor parameter:

```java
.advisors(spec -> spec.param(AgentRequestContext.KEY, context)
        .param(ChatMemory.CONVERSATION_ID, conversationId.toString()))
```

The chain is now:

```text
DevelopmentLoggingAdvisor (dev only)
 → MissionContextAdvisor
 → MessageChatMemoryAdvisor
 → ToolCallingAdvisor
 → AgentIterationLogger
 → model
```

The memory advisor surrounds the whole tool loop. Only user text and final
assistant text are retained. Application snapshots, system messages, tool requests
and tool results are excluded. Each request still gets fresh application context.

ConversationMemory serializes turns sharing an ID and restores prior history if
the turn fails. This matters because the standard advisor saves the user message
before the model responds. Otherwise a failed call could leave an incomplete turn.

## Memory is not the simulator

The window retains at most 20 user/assistant messages, usually 10 turns. Older
messages are evicted, so old references can be forgotten. Twenty messages is not
twenty tokens, nor does it bound the complete model prompt.

Past answers can mention telemetry, but they are historical conversation, not
authoritative state. The agent must read tools again for current battery, weather
and mission status. Explicit mission selection remains request-local.

Chat memory provides recent dialogue. RAG retrieves relevant external knowledge.
Application state holds drones and missions. The model context window limits the
total input the provider can process. These are different responsibilities.

## Demonstration through Swagger UI

Start the application with OpenAI configured; use the dev profile to see the chain:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Open `http://localhost:8080/swagger-ui/index.html` and reset the simulator using
**Simulation → POST /api/simulation/reset**. For a repeatable demonstration, clear
both IDs below with **Conversation memory → DELETE …/messages**.

1. Under **World agent → POST /api/agent/chat**, use **Try it out** and execute:

   ```json
   {
     "conversationId": "4ea95657-b281-4330-97c4-9894249bb992",
     "message": "We are monitoring Alpha."
   }
   ```

2. Start conversation B using a different UUID:

   ```json
   {
     "conversationId": "c8b10f35-6095-4051-8dba-302ae5c13597",
     "message": "We are monitoring Charlie."
   }
   ```

3. Send “How much battery does it have?” to each ID. The expected subjects are
   Alpha in A (82% initially) and Charlie in B (67%). The live model chooses the
   tools and wording; compare its answer with the Simulation endpoints.
4. Inject an event through **POST /api/simulation/events**:

   ```json
   {"type":"BATTERY_DROP","droneId":"alpha","amount":20}
   ```

   Ask the same follow-up in A. The current answer should be 62%, demonstrating
   remembered identity with fresh telemetry.
5. Under **Conversation memory → GET …/messages**, inspect each UUID. Expect only
   user/assistant role-content pairs, without raw tool data or application snapshots.
6. Delete A's messages. Its GET now returns an empty list; B's history remains.
   If you ask the ambiguous battery question in A again, the agent should request
   a drone identifier. Resetting the simulator alone would not erase this history.

