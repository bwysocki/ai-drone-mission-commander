# Drone Mission AI — Implementation Plan

## Goal

Build a complete learning project around modern Spring AI concepts using a simulated drone mission domain.

The project should demonstrate how Spring AI features work together in a realistic application instead of as isolated examples.

The project stays focused on:

* Java
* Spring Boot
* Spring AI
* deterministic in-memory simulation
* AI orchestration
* agentic workflows

The project does **not** use real drone hardware or robotics frameworks.

---

# Main Project Scenario

A typical user request:

> Send Alpha to inspect sector Bravo. Check whether the mission is safe first and report anything unusual.

The final system should be able to:

1. understand the natural-language request,
2. identify the requested drone and mission,
3. inspect current drone state,
4. inspect current weather and sector information,
5. retrieve operational procedures,
6. plan the mission,
7. validate the mission using deterministic Java safety rules,
8. execute the mission against the simulated drone world,
9. analyze an inspection image,
10. produce a mission report,
11. optionally accept voice input and return voice output.

---

# Architecture Principle

## AI plans. Java enforces.

The LLM may:

* understand natural language,
* extract intent,
* plan missions,
* choose tools,
* reason over retrieved knowledge,
* interpret images,
* summarize mission results.

The LLM must not be responsible for deterministic safety rules.

Safety constraints must be implemented in Java.

Example:

```text
User request
    ↓
LLM
    ↓
MissionPlan
    ↓
MissionSafetyValidator
    ↓
ACCEPT / REJECT
    ↓
Execution
```

---

# Technology

* Java 25
* Spring Boot 4.1.x
* Spring AI 2.0.x
* Maven
* Spring Web
* OpenAI model integration
* Spring Boot Actuator
* Micrometer
* JUnit 5
* AssertJ
* SimpleVectorStore initially
* in-memory Java state for the simulator

Optional later:

* persistent vector store
* external database

---

# Project Structure

Suggested package structure:

```text
api/
agent/
domain/
simulation/
safety/
tools/
rag/
memory/
multimodal/
voice/
mcp/
evaluation/
config/
```

Suggested resources:

```text
src/main/resources/

knowledge/
    battery-policy.md
    weather-policy.md
    gps-failure.md
    mission-procedure.md
    emergency-procedure.md
    inspection-procedure.md

inspection/
    sector-a.jpg
    sector-b.jpg
    sector-c.jpg

prompts/
```

Documentation:

```text
docs/
    article-notes.md
    scenarios.md
    architecture.md
```

---

# Milestone 1 — Spring AI Fundamentals

## Goal

Understand the basic Spring AI execution model before building an agent.

### Tasks

* [x] Create Spring Boot project
* [x] Add Spring Web
* [x] Add Spring AI OpenAI starter
* [x] Configure OpenAI API key through environment variables
* [x] Verify `ChatModel` autoconfiguration
* [x] Verify `ChatClient.Builder` autoconfiguration
* [x] Create a minimal `ChatModel` example
* [x] Create a minimal `ChatClient` example
* [x] Add `/api/chat` endpoint
* [x] Add Swagger UI with executable examples for both chat endpoints
* [x] Compare `ChatModel` and `ChatClient`
* [x] Inspect `Prompt`
* [x] Inspect `SystemMessage`
* [x] Inspect `UserMessage`
* [x] Inspect `ChatResponse`
* [x] Inspect `Generation`
* [x] Inspect `AssistantMessage`
* [x] Inspect response metadata

Implementation and object inspection: [Milestone 1 notes](article-notes.md#milestone-1--spring-ai-fundamentals).
Both REST paths and autoconfiguration are verified against a local HTTP provider stub
without real credentials. A live provider smoke test remains a manual check using
the [README example](../README.md#chat-examples-milestone-1).

### Learning Goals

Understand:

```text
ChatClient
    ↓
Prompt
    ↓
Messages
    ↓
ChatModel
    ↓
Provider
    ↓
ChatResponse
```

Understand the role of:

* `ChatModel`
* `ChatClient`
* `Prompt`
* `SystemMessage`
* `UserMessage`
* `AssistantMessage`
* `ToolMessage`
* `ChatResponse`
* model options

### Definition of Done

A request such as:

```text
What should a drone operator check before an inspection mission?
```

can be sent through:

```text
REST
 ↓
ChatClient
 ↓
LLM
 ↓
response
```

---

# Milestone 2 — Prompts, Options and Streaming

## Goal

Understand how Spring AI controls model behavior and response delivery.

### Tasks

* [x] Add default system prompt
* [x] Move system prompt to a reusable place
* [x] Experiment with system instructions
* [x] Add model options
* [x] Understand default options vs request-specific options
* [x] Add streaming endpoint
* [x] Compare `.call()` and `.stream()`
* [x] Inspect streaming response flow

Implementation and experiments: [scenarios](scenarios.md).
Resource variants are tested without a live model; the scenarios provide a manual
comparison procedure for response quality. Streaming integration verifies that
the first fragment arrives before the provider finishes.

### Example System Prompt

```text
You are an AI assistant for drone mission operations.

Help operators understand and plan drone missions.

Never claim that an action has been executed unless execution has been confirmed.
```

### Definition of Done

Support both:

```text
POST /api/chat
```

and:

```text
GET /api/chat/stream
```

---

# Milestone 3 — Structured Output

## Goal

Stop treating the LLM only as a text generator.

Use the LLM as a typed component of the Java application.

### Domain Types

Create:

* [x] `MissionIntent`
* [x] `MissionPlan`
* [x] `MissionAssessment`
* [x] `MissionReport`
* [x] supporting enums

Example:

```java
public record MissionIntent(
        String droneId,
        MissionType type,
        String targetSector,
        boolean returnHome
) {
}
```

### Tasks

* [x] Convert natural language into `MissionIntent`
* [x] Use Spring AI structured output APIs
* [x] Validate structured output
* [x] Handle invalid model output
* [x] Experiment with schema-based output
* [x] Add tests

Implemented as `POST /api/missions/intent`, with optional `nativeOutput=true`.
Both schema delivery modes are verified against a local provider stub; live semantic
accuracy remains a manual evaluation. Plans, assessments and reports are domain
contracts for later milestones, not model-generated execution claims.
See [Milestone 3 notes](article-notes.md) and [scenarios](scenarios.md).

### Example

Input:

```text
Send Alpha to Bravo, inspect the area and return home.
```

Output:

```text
MissionIntent(
    droneId = alpha,
    type = INSPECTION,
    targetSector = BRAVO,
    returnHome = true
)
```

### Definition of Done

Natural language can be converted reliably into typed Java objects.

---

# Milestone 4 — Drone World Simulator

## Goal

Create a deterministic environment for the AI agent.

This simulator replaces any need for real drone hardware.

### Domain Model

Create:

* [x] `Drone`
* [x] `DroneStatus`
* [x] `DroneState`
* [x] `Mission`
* [x] `MissionState`
* [x] `MissionType`
* [x] `Sector`
* [x] `Position`
* [x] `Weather`
* [x] `Route`
* [x] `InspectionResult`

### Initial World

Example drones:

```text
Alpha
battery: 82%
position: BASE
state: READY

Bravo
battery: 41%
position: SECTOR_A
state: IDLE

Charlie
battery: 67%
position: BASE
state: READY
```

Example sectors:

```text
SECTOR_A
SECTOR_B
SECTOR_C
```

Example weather:

```text
wind: 12 km/h
rain: false
visibility: GOOD
```

### Services

Create:

* [x] `DroneWorld`
* [x] `DroneSimulationService`
* [x] `MissionSimulationService`
* [x] `RouteService`
* [x] `WeatherService`
* [x] `SectorService`
* [x] `SimulationEventService`

### Simulator Capabilities

Support:

* [x] reading drone status
* [x] reading weather
* [x] reading sector information
* [x] calculating routes
* [x] estimating battery usage
* [x] creating missions
* [x] executing missions
* [x] moving drones
* [x] consuming battery
* [x] returning drones home
* [x] injecting simulation events

### Simulation Events

Support:

* [x] `BATTERY_DROP`
* [x] `GPS_DEGRADED`
* [x] `GPS_LOST`
* [x] `STRONG_WIND`
* [x] `MOTOR_WARNING`
* [x] `COMMUNICATION_LOST`

### Definition of Done

The simulator can be used without any AI code.

All deterministic domain logic must have normal Java tests.

Implemented with synchronous, atomic execution and deterministic fixture findings.
Run with the `simulator` profile to use the API and Swagger without OpenAI credentials.
Events affect subsequent operations; mid-flight event processing is outside this
instantaneous model. Route costs are re-evaluated at execution time. Technical
movement checks are implemented here; mission policy remains a later safety milestone.
See [Milestone 4 notes](article-notes.md) and [scenarios](scenarios.md).

---

# Milestone 5 — Tool Calling

## Goal

Give the LLM access to the simulated world.

### Tools

Create:

* [x] `getDroneStatus`
* [x] `getFleetStatus`
* [x] `getWeather`
* [x] `getSector`
* [x] `calculateRoute`
* [x] `getMission`
* [x] `getRecentAlerts`

Example:

```java
@Tool(description = "Returns current drone status")
DroneStatus getDroneStatus(String droneId) {
    ...
}
```

### Tasks

* [x] Create `DroneTools`
* [x] Create `WeatherTools`
* [x] Create `MissionTools`
* [x] Register tools with the agent
* [x] Inspect generated tool definitions
* [x] Inspect tool call arguments
* [x] Inspect tool results
* [x] Handle tool failures
* [x] Add integration tests

### Example Scenario

User:

```text
Can Alpha inspect SECTOR_B and return home?
```

Expected agent behavior:

```text
getDroneStatus("alpha")
    ↓
getWeather()
    ↓
getSector("SECTOR_B")
    ↓
calculateRoute(...)
    ↓
answer
```

### Definition of Done

The LLM can answer questions using live simulated state instead of inventing facts.

---

# Milestone 6 — Agent Loop

## Goal

Understand what makes a tool-using chatbot an agent.

### Tasks

* [ ] Understand `ToolCallingAdvisor`
* [ ] Observe one complete tool loop
* [ ] Log every agent iteration
* [ ] Inspect Assistant tool calls
* [ ] Inspect Tool messages
* [ ] Compare automatic and manual tool execution
* [ ] Temporarily implement one manual tool cycle
* [ ] Restore automatic tool loop
* [ ] Handle multi-tool scenarios
* [ ] Handle tool execution errors

### Mental Model

```text
User
 ↓
LLM
 ↓
Tool call
 ↓
Java tool
 ↓
Tool result
 ↓
LLM
 ↓
Tool call
 ↓
...
 ↓
Final response
```

### Definition of Done

The agent can use several tools in sequence before producing a final response.

---

# Milestone 7 — Advisors

## Goal

Understand Spring AI's main composition mechanism.

### Tasks

* [ ] Learn advisor chain behavior
* [ ] Understand advisor ordering
* [ ] Inspect default advisors
* [ ] Create `MissionContextAdvisor`
* [ ] Add simulation context
* [ ] Add current mission context
* [ ] Add conversation identifiers
* [ ] Add development logging advisor
* [ ] Verify interaction with tool calling

### Intended Chain

```text
ChatClient
    ↓
MissionContextAdvisor
    ↓
MemoryAdvisor
    ↓
RagAdvisor
    ↓
ToolCallingAdvisor
    ↓
LLM
```

Not all advisors need to exist immediately.

### Definition of Done

At least one custom advisor participates in the request pipeline.

---

# Milestone 8 — Chat Memory

## Goal

Understand conversation memory and how it differs from application state.

### Tasks

* [ ] Add `ChatMemory`
* [ ] Add a memory advisor
* [ ] Use conversation IDs
* [ ] Test isolated conversations
* [ ] Inspect stored messages
* [ ] Understand memory window behavior
* [ ] Add integration tests

### Scenario

Conversation A:

```text
We are monitoring Alpha.
How much battery does it have?
```

Conversation B:

```text
We are monitoring Charlie.
How much battery does it have?
```

The contexts must not mix.

### Key Concept

Understand:

```text
Chat Memory
    ≠
RAG
    ≠
Application State
    ≠
Model Context Window
```

### Definition of Done

The agent remembers conversational references without storing domain state inside the LLM conversation.

---

# Milestone 9 — Embeddings and Vector Store

## Goal

Understand semantic search before adding RAG.

### Knowledge Base

Create:

* [ ] `battery-policy.md`
* [ ] `weather-policy.md`
* [ ] `gps-failure.md`
* [ ] `mission-procedure.md`
* [ ] `emergency-procedure.md`
* [ ] `inspection-procedure.md`

### Tasks

* [ ] Understand embeddings
* [ ] Configure `EmbeddingModel`
* [ ] Configure `SimpleVectorStore`
* [ ] Create `Document` objects
* [ ] Store documents
* [ ] Perform `similaritySearch`
* [ ] Inspect similarity results
* [ ] Add metadata
* [ ] Experiment with metadata filters

### Example Metadata

```text
type = SAFETY
topic = BATTERY
```

### Definition of Done

A semantic query can retrieve the correct drone procedure without involving the chat model.

---

# Milestone 10 — Document ETL

## Goal

Build a repeatable knowledge ingestion pipeline.

### Tasks

* [ ] Use a document reader
* [ ] Split documents into chunks
* [ ] Add metadata
* [ ] Transform documents
* [ ] Write documents into the vector store
* [ ] Make ingestion repeatable
* [ ] Prevent unnecessary duplicate ingestion

### Pipeline

```text
knowledge/*.md
    ↓
DocumentReader
    ↓
DocumentTransformer
    ↓
Chunks
    ↓
EmbeddingModel
    ↓
VectorStore
```

### Definition of Done

The knowledge base can be loaded automatically when required.

---

# Milestone 11 — RAG

## Goal

Give the agent operational knowledge.

### Phase 1 — Simple RAG

* [ ] Add simple question-answer RAG
* [ ] Ask policy questions
* [ ] Compare with and without RAG
* [ ] Inspect retrieved context

### Phase 2 — Modular RAG

* [ ] Add `RetrievalAugmentationAdvisor`
* [ ] Add query transformation if useful
* [ ] Add metadata filtering
* [ ] Control document selection
* [ ] Inspect retrieval pipeline

### Key Architectural Rule

```text
Tools
→ current facts

RAG
→ policies, manuals and procedures

Memory
→ conversation

Java
→ deterministic rules
```

### Main Scenario

Current state:

```text
Alpha battery = 18%
weather = GOOD
```

Knowledge:

```text
battery below 20%:
do not start a new inspection mission
```

Agent should combine both.

### Definition of Done

The agent can combine live tool data with retrieved knowledge.

---

# Milestone 12 — Java Safety Layer

## Goal

Make deterministic safety independent from the LLM.

### Create

* [ ] `MissionSafetyValidator`
* [ ] `SafetyViolation`
* [ ] `SafetyDecision`

### Example Hard Rules

* [ ] battery below hard threshold → reject
* [ ] excessive wind → reject
* [ ] drone not ready → reject
* [ ] unknown sector → reject
* [ ] invalid route → reject

### Pipeline

```text
LLM
 ↓
MissionPlan
 ↓
MissionSafetyValidator
 ↓
ACCEPT / REJECT
 ↓
Mission execution
```

### Important Rule

The safety validator:

* must not call an LLM,
* must not depend on prompts,
* must be deterministic,
* must have standard unit tests.

### Definition of Done

An unsafe mission cannot be executed even if the LLM recommends execution.

---

# Milestone 13 — Mission Execution

## Goal

Allow the agent to change the simulated world.

### Tools

Create:

* [ ] `createMission`
* [ ] `executeMission`
* [ ] `cancelMission`
* [ ] `returnToHome`

### Execution Flow

```text
MissionIntent
    ↓
MissionPlan
    ↓
SafetyValidator
    ↓
MissionSimulationService
    ↓
DroneWorld state update
```

### Test

Before:

```text
Alpha
position = BASE
battery = 80%
```

After mission:

```text
Alpha
position = BRAVO
battery = 65%
```

### Definition of Done

Tool execution changes the Java simulation state.

---

# Milestone 14 — Multimodal Vision

## Goal

Use image input as part of the mission workflow.

### Resources

Prepare sample images for sectors:

* [ ] empty area
* [ ] vehicle
* [ ] smoke
* [ ] damaged structure

### Tasks

* [ ] Understand Spring AI media messages
* [ ] Send image + text to a multimodal model
* [ ] Create structured `InspectionResult`
* [ ] Detect anomalies
* [ ] Add inspection tool
* [ ] Connect inspection result to mission report

### Example

```java
public record InspectionResult(
        boolean anomalyDetected,
        String description,
        double confidence
) {
}
```

### Definition of Done

A simulated mission can return an image that the AI analyzes into a typed inspection result.

---

# Milestone 15 — Speech-to-Text

## Goal

Allow voice commands as an input adapter.

### Tasks

* [ ] Add transcription model
* [ ] Add voice upload endpoint
* [ ] Transcribe speech
* [ ] Send transcription into the existing agent pipeline
* [ ] Add error handling

### Example

Voice:

```text
Send Alpha to inspect Bravo and return home.
```

Pipeline:

```text
Audio
 ↓
Speech-to-Text
 ↓
Text
 ↓
Mission Agent
```

### Important Rule

Voice must not create a separate mission implementation.

It is only another input adapter.

### Definition of Done

A recorded voice command can start the same flow as a text request.

---

# Milestone 16 — Text-to-Speech

## Goal

Return spoken mission results.

### Tasks

* [ ] Add TTS model
* [ ] Convert mission report to audio
* [ ] Add optional audio response endpoint
* [ ] Keep text response available

### Definition of Done

The system can accept a spoken mission request and return a spoken result.

---

# Milestone 17 — MCP Server

## Goal

Expose drone capabilities outside the application.

### Expose as MCP Tools

* [ ] `getDroneStatus`
* [ ] `getFleetStatus`
* [ ] `getWeather`
* [ ] `getMission`
* [ ] `createMission`
* [ ] `executeMission`

### Optional MCP Resources

* [ ] `drone://{id}/status`
* [ ] `mission://{id}`

### Tasks

* [ ] Configure MCP server
* [ ] Use MCP annotations
* [ ] Expose tools
* [ ] Expose at least one resource
* [ ] Test MCP tool discovery
* [ ] Test tool invocation externally

### Learning Goal

Understand:

```text
Local @Tool
```

vs:

```text
MCP Tool
```

A local tool is available to this agent.

An MCP tool is a portable capability that can be discovered and used by external AI clients.

### Definition of Done

The drone simulator can be accessed through MCP.

---

# Milestone 18 — MCP Client

## Goal

Make the Mission Agent consume external capabilities through MCP.

### Tasks

* [ ] Configure MCP client
* [ ] Discover tools
* [ ] Replace one direct Spring tool with MCP tool usage
* [ ] Call Drone MCP Server
* [ ] Inspect remote tool lifecycle
* [ ] Handle unavailable MCP server

### Architecture

```text
Mission Agent
    │
    │ MCP
    ▼
Drone MCP Server
    │
    ▼
Drone World
```

### Definition of Done

At least one mission capability is executed through MCP instead of direct bean invocation.

---

# Milestone 19 — Agent Skills

## Goal

Understand the difference between knowledge, tools and reusable task instructions.

### Skills

Create:

```text
skills/

preflight-check/
    SKILL.md

area-inspection/
    SKILL.md

emergency-return/
    SKILL.md
```

### Learning Goal

Understand:

```text
RAG
→ what the system knows

Tool
→ what the system can do

Skill
→ how the agent should perform a task
```

### Tasks

* [ ] Add Agent Skills support
* [ ] Create at least two skills
* [ ] Load skills progressively
* [ ] Test skill selection

### Definition of Done

The agent can use reusable task instructions without placing all instructions in the system prompt.

---

# Milestone 20 — Optional Subagent

## Priority

Optional.

Do only if core milestones are complete.

### Candidate

Create:

```text
InspectionAgent
```

Responsibilities:

* analyze inspection images,
* return structured findings,
* avoid mission planning responsibilities.

Main agent:

```text
MissionAgent
    ↓
InspectionAgent
```

### Definition of Done

The main agent delegates one clearly isolated task.

---

# Milestone 21 — Optional A2A

## Priority

Bonus only.

Do not delay project completion for this milestone.

### Goal

Explore communication between separately defined agents.

Possible agents:

```text
Mission Agent
Safety Agent
Inspection Agent
```

### Definition of Done

A minimal example demonstrates agent-to-agent communication.

---

# Milestone 22 — Observability

## Goal

Make agent execution inspectable.

### Tasks

* [ ] Add Spring Boot Actuator
* [ ] Add Micrometer
* [ ] Enable Spring AI observations
* [ ] Observe ChatClient calls
* [ ] Observe advisor execution
* [ ] Observe tool calls
* [ ] Inspect latency
* [ ] Inspect token usage
* [ ] Add development logging
* [ ] Add correlation identifiers

### Desired View

```text
Mission request
    │
ChatClient
    │
RAG                  35 ms
    │
LLM                 820 ms
    │
getDroneStatus        4 ms
    │
getWeather            2 ms
    │
LLM                 610 ms
```

### Definition of Done

A developer can inspect how an agent request was executed and where time/tokens were consumed.

---

# Milestone 23 — Evaluation

## Goal

Learn how to test an application containing nondeterministic AI behavior.

### Deterministic Tests

Use standard JUnit tests for:

* [ ] simulator
* [ ] route calculations
* [ ] battery calculations
* [ ] mission safety
* [ ] mission state transitions
* [ ] validation

### AI Evaluation Scenarios

Create:

#### Scenario 1

```text
battery = 10%
expected = mission rejected
```

#### Scenario 2

```text
battery = 80%
weather = good
expected = mission allowed
```

#### Scenario 3

```text
GPS = LOST
expected = abort or emergency recommendation
```

#### Scenario 4

```text
unknown sector
expected = rejection
```

#### Scenario 5

```text
wind = unsafe
expected = rejection
```

### Tasks

* [ ] Learn Spring AI evaluation API
* [ ] Create evaluation dataset
* [ ] Run evaluation tests
* [ ] Compare deterministic validation with LLM evaluation
* [ ] Document failures

### Definition of Done

The project clearly distinguishes:

```text
deterministic correctness
```

from:

```text
LLM response quality
```

---

# Milestone 24 — End-to-End Scenarios

## Scenario A — Normal Mission

```text
Alpha
battery = 83%
state = READY

Weather
GOOD

User:
Send Alpha to inspect Bravo.
Check whether the mission is safe first.
```

Expected:

```text
intent extracted
 ↓
status checked
 ↓
weather checked
 ↓
RAG consulted
 ↓
mission planned
 ↓
Java safety accepted
 ↓
mission executed
 ↓
image analyzed
 ↓
mission report created
```

---

## Scenario B — Low Battery

```text
Alpha
battery = 12%
```

User:

```text
Send Alpha to Bravo.
```

Expected:

```text
MissionSafetyValidator
    ↓
REJECT
```

The mission must not execute.

---

## Scenario C — GPS Failure

Inject:

```text
GPS_LOST
```

Expected:

* agent retrieves emergency procedure,
* agent explains the incident,
* deterministic execution remains controlled by Java.

---

## Scenario D — Strong Wind

Inject unsafe wind.

Expected:

```text
mission rejected
```

---

## Scenario E — Inspection Anomaly

Inspection image contains an anomaly.

Expected:

```text
InspectionResult.anomalyDetected = true
```

and the anomaly appears in the mission report.

---

# Milestone 25 — Project Cleanup

## Tasks

* [ ] remove unused code
* [ ] remove experimental hacks
* [ ] remove duplicated prompts
* [ ] remove duplicated business logic
* [ ] review package structure
* [ ] review naming
* [ ] verify configuration
* [ ] verify secrets are not committed
* [ ] run full tests
* [ ] run end-to-end scenarios

---

# Milestone 26 — README

README should include:

* [ ] project goal
* [ ] architecture
* [ ] technology stack
* [ ] how to run
* [ ] required environment variables
* [ ] example requests
* [ ] example voice workflow
* [ ] RAG explanation
* [ ] MCP explanation
* [ ] safety philosophy
* [ ] screenshots or terminal output
* [ ] link to article

Important statement:

```text
This project is not a drone flight controller.

It is a Spring AI learning project using a deterministic Java drone simulator.
```

---

# Milestone 27 — Article

## Working Title

# Building an AI Drone Mission Commander with Spring AI 2.0

Possible subtitle:

> From ChatClient and RAG to Tools, MCP and Multimodal Agents

---

# Article Structure

## 1. Introduction

Explain:

* why this project exists,
* why a drone mission domain is useful,
* which Spring AI concepts will be demonstrated.

Keep this short.

---

## 2. Starting with ChatClient

Cover:

* `ChatModel`
* `ChatClient`
* prompts
* messages
* model abstraction

---

## 3. From Natural Language to Java

Cover:

* structured output
* `MissionIntent`
* typed LLM responses

Key idea:

```text
Natural language
    ↓
LLM
    ↓
Java object
```

---

## 4. Giving the Agent Tools

Cover:

* `@Tool`
* tool definitions
* tool calls
* tool results
* live simulator state

---

## 5. When Does It Become an Agent?

Explain agent loop:

```text
LLM
 ↓
Tool
 ↓
Observation
 ↓
LLM
 ↓
Tool
 ↓
...
```

---

## 6. Advisors

Explain:

* advisor chain,
* custom advisors,
* composition.

---

## 7. Memory Is Not RAG

Explain differences between:

```text
Memory
RAG
Tools
Application state
```

---

## 8. Giving the Agent Knowledge

Cover:

* documents
* embeddings
* VectorStore
* ETL
* RAG
* metadata filtering

---

## 9. Combining Tools and RAG

Show:

```text
Tools
→ current world state

RAG
→ policies and procedures
```

This should be one of the core sections.

---

## 10. Giving the Agent Eyes and Ears

Cover:

* image input
* multimodal model
* speech-to-text
* text-to-speech

---

## 11. MCP

Cover:

* local Spring tools,
* MCP server,
* MCP client,
* portable capabilities.

---

## 12. Tools vs RAG vs Skills

Explain:

```text
Tool
→ capability

RAG
→ knowledge

Skill
→ reusable procedure
```

---

## 13. AI Plans. Java Enforces.

This is the architectural centerpiece.

Show:

```text
LLM
 ↓
MissionPlan
 ↓
MissionSafetyValidator
 ↓
Execution
```

Explain why deterministic safety remains Java code.

---

## 14. Observability

Show:

* tool calls,
* model calls,
* latency,
* tokens,
* advisor execution.

---

## 15. Evaluation

Explain:

* deterministic unit tests,
* AI evaluation,
* scenario-based testing.

---

## 16. What I Learned

Possible conclusions:

* agents are mainly orchestration systems,
* structured output is extremely useful in Java,
* RAG and memory solve different problems,
* tools connect AI to real application state,
* MCP makes capabilities portable,
* deterministic business rules should remain deterministic,
* AI works best where interpretation and reasoning are needed.

---

# Article Notes Workflow

After every meaningful implementation step update:

```text
docs/article-notes.md
```

For every topic record:

```text
## Topic

Problem:

Spring AI concept:

Implementation:

Important code:

What surprised me:

What I learned:

Potential article snippet:
```

Do not wait until the project is finished to start writing.

---

# Priority

## P0 — Must Have

The project is not complete without:

* [ ] ChatClient
* [ ] Prompts
* [ ] Structured Output
* [ ] Drone World Simulator
* [ ] Tool Calling
* [ ] Agent Loop
* [ ] Advisors
* [ ] Chat Memory
* [ ] Embeddings
* [ ] VectorStore
* [ ] ETL
* [ ] RAG
* [ ] Java Safety Layer
* [ ] MCP Server
* [ ] MCP Client
* [ ] Observability
* [ ] Evaluation
* [ ] README
* [ ] Article

---

## P1 — Strongly Recommended

* [ ] Vision
* [ ] Speech-to-Text
* [ ] Text-to-Speech
* [ ] Agent Skills

---

## P2 — Bonus

* [ ] Subagents
* [ ] A2A
* [ ] persistent database
* [ ] external vector database
* [ ] UI
* [ ] advanced RAG
* [ ] complex reranking

Do not delay publication for P2 features.

---

# Suggested 4-Week Schedule

## Week 1 — From LLM to Agent

Target:

* [ ] Spring AI fundamentals
* [ ] prompts
* [ ] streaming
* [ ] structured output
* [ ] Drone World Simulator
* [ ] tools
* [ ] agent loop

End-of-week result:

```text
User
 ↓
Mission Agent
 ↓
multiple tools
 ↓
simulated world
 ↓
answer
```

---

## Week 2 — Context and Knowledge

Target:

* [ ] Advisors
* [ ] Memory
* [ ] Embeddings
* [ ] VectorStore
* [ ] ETL
* [ ] RAG
* [ ] Java Safety Layer

End-of-week result:

```text
Agent
 ├── Tools → live state
 ├── RAG → operational knowledge
 ├── Memory → conversation
 └── Java → hard safety rules
```

---

## Week 3 — Multimodal and Interoperability

Target:

* [ ] Vision
* [ ] Speech-to-Text
* [ ] Text-to-Speech
* [ ] MCP Server
* [ ] MCP Client
* [ ] Agent Skills

Optional:

* [ ] Subagent

End-of-week result:

```text
Voice
 ↓
Agent
 ↓
MCP / Tools / RAG
 ↓
Mission
 ↓
Image analysis
 ↓
Voice report
```

---

## Week 4 — Production Quality and Publication

Target:

* [ ] observability
* [ ] evaluation
* [ ] scenario tests
* [ ] cleanup
* [ ] README
* [ ] architecture diagrams
* [ ] article draft
* [ ] article review
* [ ] publication-ready repository

---

# Daily Development Rule

Each development session should finish with:

1. code,
2. tests,
3. one small documentation update,
4. one article note.

Suggested commit naming:

```text
day-01-chat-client
day-02-prompts-streaming
day-03-structured-output
day-04-drone-world
day-05-tools
day-06-agent-loop
...
```

---

# Definition of Project Complete

The project is complete when the following command can be processed end-to-end:

> Send Alpha to inspect sector Bravo. Check whether the mission is safe first and report anything unusual.

And the execution demonstrates:

```text
natural language
    ↓
structured intent
    ↓
agent reasoning
    ↓
tools
    ↓
RAG
    ↓
Java safety validation
    ↓
simulated mission execution
    ↓
image analysis
    ↓
mission report
```

Optional voice layer:

```text
speech
 ↓
STT
 ↓
same pipeline
 ↓
TTS
```

The final repository should demonstrate the main Spring AI concepts through one coherent application rather than a collection of unrelated demos.
