# Drone Mission AI — Architecture

## 1. Purpose

Drone Mission AI is a Spring Boot application created to demonstrate modern Spring AI concepts in one coherent project.

The application uses a simulated drone environment implemented entirely in Java.

The main goal is to explore:

* ChatClient
* structured output
* tool calling
* agent loops
* Advisors
* Chat Memory
* embeddings
* VectorStore
* RAG
* multimodal input
* speech-to-text
* text-to-speech
* MCP
* observability
* evaluation

The system is not a real drone flight controller.

---

# 2. Main Architectural Principle

## AI plans. Java enforces.

The LLM is responsible for tasks that benefit from language understanding and reasoning.

The Java application remains responsible for deterministic business and safety rules.

```text
User request
    ↓
Spring AI Agent
    ↓
MissionPlan
    ↓
Java Safety Validation
    ↓
Mission Execution
```

The AI may propose:

```text
EXECUTE MISSION
```

but execution is only possible if Java validation accepts the plan.

```text
LLM
 ↓
MissionPlan
 ↓
MissionSafetyValidator
 ├── ACCEPT → execution
 └── REJECT → no execution
```

---

# 3. High-Level Architecture

```text
                     ┌──────────────────┐
                     │      User        │
                     └────────┬─────────┘
                              │
                 ┌────────────┴────────────┐
                 │                         │
               Text                      Voice
                 │                         │
                 │                  Speech-to-Text
                 │                         │
                 └────────────┬────────────┘
                              │
                              ▼
                      ┌───────────────┐
                      │ Mission Agent │
                      │  Spring AI    │
                      └───────┬───────┘
                              │
           ┌──────────────────┼──────────────────┐
           │                  │                  │
           ▼                  ▼                  ▼
        Memory               RAG               Tools
           │                  │                  │
           │           Knowledge Base      Drone Services
           │                                     │
           └──────────────────┬──────────────────┘
                              │
                              ▼
                        MissionPlan
                              │
                              ▼
                   MissionSafetyValidator
                              │
                    ┌─────────┴─────────┐
                    │                   │
                  ACCEPT              REJECT
                    │
                    ▼
                Mission Execution
                    │
                    ▼
               Drone World Simulator
                    │
                    ▼
              Inspection Image
                    │
                    ▼
              Multimodal Model
                    │
                    ▼
               MissionReport
                    │
              ┌─────┴─────┐
              │           │
            Text          TTS
                          │
                        Audio
```

---

# 4. Main Components

## 4.1 API Layer

Package:

```text
api/
```

Responsibilities:

* expose REST endpoints,
* accept text commands,
* accept audio commands,
* return text or audio responses,
* manage conversation identifiers,
* translate HTTP concerns into application calls.

Example components:

```text
ChatController
MissionController
VoiceController
SimulationController
```

The API layer must not contain AI orchestration or domain logic.

---

# 5. Mission Agent

Package:

```text
agent/
```

The `MissionAgent` is the main AI orchestration component.

It uses Spring AI `ChatClient`.

Responsibilities:

* interpret natural-language commands,
* obtain structured `MissionIntent`,
* use tools,
* retrieve knowledge,
* use conversation memory,
* prepare mission plans,
* explain decisions,
* produce mission reports.

Conceptually:

```text
MissionAgent
     │
     ├── ChatClient
     ├── Advisors
     ├── Tools
     ├── Memory
     └── RAG
```

The agent must not directly modify simulator state.

State changes happen only through application tools and domain services.

---

# 6. Spring AI Execution Pipeline

The main request pipeline is:

```text
User
 ↓
ChatClient
 ↓
Advisor Chain
 ↓
ChatModel
 ↓
LLM
```

Expected advisor chain:

```text
ChatClient
    ↓
MissionContextAdvisor
    ↓
MemoryAdvisor
    ↓
RAG Advisor
    ↓
ToolCallingAdvisor
    ↓
ChatModel
```

The exact ordering may evolve during implementation.

---

# 7. Structured Output

Natural language should be converted into typed Java objects as early as possible.

Example:

```text
"Send Alpha to inspect Bravo and return home."
```

becomes:

```java
MissionIntent(
    droneId = "alpha",
    missionType = INSPECTION,
    targetSector = "BRAVO",
    returnHome = true
)
```

Main structured types:

```text
MissionIntent
MissionPlan
MissionAssessment
InspectionResult
MissionReport
```

The goal is to minimize unstructured text inside business logic.

Preferred flow:

```text
Natural language
      ↓
LLM
      ↓
Typed Java object
      ↓
Normal application logic
```

---

# 8. Tool Layer

Package:

```text
tools/
```

Tools expose application capabilities to the LLM.

Examples:

```text
getDroneStatus
getFleetStatus
getWeather
getSector
calculateRoute
getMission
getRecentAlerts
createMission
executeMission
cancelMission
returnToHome
```

Tools are adapters between the AI layer and deterministic Java services.

```text
LLM
 ↓
@Tool
 ↓
Java Service
 ↓
Drone World
```

The LLM never directly accesses internal simulator state.

---

# 9. Agent Loop

Tool calling creates the agent execution loop.

```text
User
 ↓
LLM
 ↓
getDroneStatus("alpha")
 ↓
Tool result
 ↓
LLM
 ↓
getWeather()
 ↓
Tool result
 ↓
LLM
 ↓
calculateRoute(...)
 ↓
Tool result
 ↓
LLM
 ↓
Final answer
```

Spring AI `ToolCallingAdvisor` manages this loop.

The project should expose enough logging and observability to make each step inspectable.

---

# 10. Drone World Simulator

Package:

```text
simulation/
```

The Drone World Simulator represents the external world.

It is implemented entirely in Java and must be deterministic.

Main components:

```text
DroneWorld
DroneSimulationService
MissionSimulationService
RouteService
WeatherService
SectorService
SimulationEventService
```

Example state:

```text
Alpha
battery: 82%
position: BASE
state: READY
```

The simulator supports:

* drone status,
* positions,
* batteries,
* weather,
* sectors,
* missions,
* routes,
* mission execution,
* alerts,
* simulated failures.

---

# 11. Simulation Events

The simulator may generate or receive events such as:

```text
BATTERY_DROP
GPS_DEGRADED
GPS_LOST
STRONG_WIND
MOTOR_WARNING
COMMUNICATION_LOST
```

These events modify deterministic application state.

Example:

```text
SimulationEvent
      ↓
DroneWorld
      ↓
DroneStatus changes
      ↓
Agent observes new state through tools
```

The LLM does not generate the underlying world state.

---

# 12. Domain Layer

Package:

```text
domain/
```

The domain layer should remain independent from Spring AI whenever possible.

Core objects:

```text
Drone
DroneStatus
DroneState
Mission
MissionIntent
MissionPlan
MissionReport
MissionState
Sector
Position
Weather
Route
InspectionResult
Alert
```

Prefer immutable records where appropriate.

Example:

```java
public record DroneStatus(
        String droneId,
        int batteryPercent,
        Position position,
        DroneState state
) {
}
```

---

# 13. Safety Layer

Package:

```text
safety/
```

The safety layer contains hard deterministic constraints.

Main component:

```text
MissionSafetyValidator
```

Example rules:

```text
battery below hard threshold → REJECT

wind above hard threshold → REJECT

drone not READY → REJECT

unknown sector → REJECT

invalid route → REJECT
```

Architecture:

```text
MissionAgent
    ↓
MissionPlan
    ↓
MissionSafetyValidator
    ↓
SafetyDecision
```

The validator must:

* not call an LLM,
* not depend on prompts,
* not depend on RAG,
* be deterministic,
* have normal unit tests.

---

# 14. Memory

Package:

```text
memory/
```

Chat Memory stores conversational context.

Example:

```text
User:
We are monitoring Alpha.

User:
How much battery does it have?
```

Memory allows the system to understand that `it` means `Alpha`.

Memory must not become the source of truth for drone state.

```text
Memory
→ conversation context

DroneWorld
→ current state
```

These concepts must remain separate.

---

# 15. RAG

Package:

```text
rag/
```

RAG provides operational knowledge.

Knowledge examples:

```text
battery-policy.md
weather-policy.md
gps-failure.md
mission-procedure.md
emergency-procedure.md
inspection-procedure.md
```

Pipeline:

```text
Documents
   ↓
Document Reader
   ↓
Transformation / Chunking
   ↓
Embedding Model
   ↓
VectorStore
```

At request time:

```text
Question
   ↓
Retriever
   ↓
Relevant documents
   ↓
LLM context
```

---

# 16. RAG vs Tools vs Memory

This separation is fundamental to the architecture.

```text
TOOLS
→ what is true now

RAG
→ what the system knows

MEMORY
→ what was discussed

JAVA STATE
→ source of truth

JAVA SAFETY
→ what is allowed
```

Example:

```text
Tool:
Alpha battery = 18%

RAG:
New missions should not start below 20%.

Java:
Hard minimum battery = 15%.
```

The model can reason over these facts, but Java still decides whether execution is technically allowed.

---

# 17. Knowledge Base

Resources:

```text
src/main/resources/knowledge/
```

Knowledge documents should contain information that is:

* descriptive,
* procedural,
* contextual,
* suitable for semantic retrieval.

Hard constraints should not exist only in knowledge documents.

For example:

Good RAG content:

```text
Recommended battery operating policy
Emergency GPS procedures
Inspection procedure
Weather guidance
```

Hard Java rule:

```text
battery < 15% → execution rejected
```

---

# 18. Multimodal Inspection

Package:

```text
multimodal/
```

The simulator may associate inspection images with sectors.

Example:

```text
Mission Execution
      ↓
Sector Bravo
      ↓
inspection image
      ↓
Multimodal model
      ↓
InspectionResult
```

Example result:

```java
public record InspectionResult(
        boolean anomalyDetected,
        String description,
        double confidence
) {
}
```

Image analysis remains an AI task because it involves interpretation rather than deterministic domain logic.

---

# 19. Voice

Package:

```text
voice/
```

Voice is an input/output adapter.

Input:

```text
Audio
 ↓
Speech-to-Text
 ↓
Text command
 ↓
MissionAgent
```

Output:

```text
MissionReport
 ↓
Text-to-Speech
 ↓
Audio
```

Voice must use the same agent pipeline as REST text input.

No separate mission logic should exist for voice commands.

---

# 20. MCP Architecture

MCP allows drone capabilities to be exposed outside the local Spring AI application.

Initial implementation:

```text
Mission Agent
      │
      │ local @Tool
      ▼
Drone Services
```

Later:

```text
Mission Agent
      │
      │ MCP
      ▼
Drone MCP Server
      │
      ▼
Drone Services
      │
      ▼
Drone World
```

MCP tools may include:

```text
getDroneStatus
getFleetStatus
getWeather
getMission
createMission
executeMission
```

Possible resources:

```text
drone://alpha/status
mission://123
```

---

# 21. Local Tool vs MCP Tool

Local architecture:

```text
MissionAgent
    ↓
@Tool
    ↓
DroneService
```

MCP architecture:

```text
MissionAgent
    ↓
MCP Client
    ↓
MCP Server
    ↓
DroneService
```

The underlying domain behavior should remain unchanged.

MCP changes the integration boundary, not the domain model.

---

# 22. Agent Skills

Skills may be added later.

Example:

```text
skills/

preflight-check/
    SKILL.md

area-inspection/
    SKILL.md

emergency-return/
    SKILL.md
```

Architectural meaning:

```text
Tool
→ capability

RAG
→ knowledge

Skill
→ procedure for performing a task
```

Skills must not replace hard Java safety rules.

---

# 23. Optional Subagents

Subagents are optional.

Possible architecture:

```text
                MissionAgent
                     │
              ┌──────┴──────┐
              │             │
        InspectionAgent   Safety reasoning
```

If implemented, subagents should have clearly separated responsibilities.

Do not create multiple agents unless there is a real responsibility boundary.

---

# 24. Observability

Observability should cover the full AI execution path.

```text
request
  ↓
ChatClient
  ↓
Advisor
  ↓
LLM
  ↓
Tool
  ↓
LLM
  ↓
RAG
  ↓
LLM
```

Important measurements:

* request latency,
* model latency,
* token usage,
* tool execution duration,
* tool failures,
* RAG retrieval duration,
* conversation ID,
* mission ID.

Use Spring Boot Actuator, Micrometer and Spring AI observations.

---

# 25. Evaluation and Testing

Testing is divided into two categories.

## Deterministic Tests

Normal JUnit tests for:

```text
route calculation
battery consumption
mission transitions
safety validation
world state
simulation events
```

These tests should be fully deterministic.

---

## AI Evaluation

Evaluation scenarios verify AI behavior.

Examples:

```text
Low battery
→ agent should recognize risk

GPS lost
→ agent should recommend appropriate procedure

Inspection anomaly
→ report should contain anomaly
```

AI evaluation must not replace deterministic unit tests.

---

# 26. Main End-to-End Flow

Final scenario:

```text
User:
"Send Alpha to inspect sector Bravo.
Check whether the mission is safe first
and report anything unusual."
```

Flow:

```text
User command
      ↓
ChatClient
      ↓
MissionIntent
      ↓
Agent Loop
      ↓
getDroneStatus()
getWeather()
getSector()
calculateRoute()
      ↓
RAG
      ↓
MissionPlan
      ↓
MissionSafetyValidator
      ↓
ACCEPT
      ↓
executeMission()
      ↓
Drone World Simulator
      ↓
Inspection image
      ↓
Multimodal analysis
      ↓
InspectionResult
      ↓
MissionReport
```

Optional:

```text
Voice
 ↓
STT
 ↓
same pipeline
 ↓
TTS
```

---

# 27. Dependency Direction

Preferred dependency direction:

```text
API
 ↓
Agent / Application
 ↓
Domain Services
 ↓
Domain
```

AI adapters depend on the domain.

The domain should not depend on Spring AI.

```text
domain
    ✗ should not know ChatClient
    ✗ should not know prompts
    ✗ should not know LLM providers
```

This keeps the AI layer replaceable and prevents AI concerns from leaking into business logic.

---

# 28. Package Dependency Rules

Preferred:

```text
api
 ↓
agent
 ↓
tools
 ↓
services
 ↓
domain
```

Additional infrastructure:

```text
agent → rag
agent → memory
agent → multimodal
agent → mcp
```

Safety:

```text
application
 ↓
safety
 ↓
domain
```

Avoid:

```text
domain → agent
domain → Spring AI
domain → MCP
domain → REST
```

---

# 29. State Ownership

Each kind of state must have one clear owner.

| State                       | Owner                    |
| --------------------------- | ------------------------ |
| drone position              | DroneWorld               |
| battery                     | DroneWorld               |
| mission status              | MissionSimulationService |
| weather                     | WeatherService           |
| conversation history        | ChatMemory               |
| knowledge documents         | VectorStore              |
| safety constraints          | Java safety code         |
| temporary reasoning context | LLM prompt               |

The LLM is never the source of truth for application state.

---

# 30. Error Handling

Errors should remain explicit.

Possible categories:

```text
UnknownDroneException
UnknownSectorException
MissionRejectedException
InvalidMissionStateException
ToolExecutionException
McpUnavailableException
AiResponseException
```

Tool failures must be represented clearly so the agent can reason about them without inventing successful execution.

Never convert:

```text
tool failure
```

into:

```text
mission completed
```

---

# 31. Security and Safety Boundaries

Even though the project uses a simulator, use production-oriented boundaries.

Rules:

* tools expose only required capabilities,
* execution tools are separate from read-only tools,
* LLM output is treated as untrusted input,
* structured output is validated,
* execution requires deterministic validation,
* API keys are never committed,
* retrieved documents are not automatically trusted as executable instructions.

---

# 32. Architectural Non-Goals

The project intentionally does not focus on:

* real drone communication,
* drone flight control,
* autopilot implementation,
* ROS integration,
* hardware integration,
* real-time control loops,
* advanced physics simulation,
* frontend development,
* distributed microservice architecture.

The goal is Spring AI architecture.

---

# 33. Guiding Principles

## Keep AI at the edges of determinism

Use AI where interpretation is useful.

Use Java where correctness must be deterministic.

---

## Prefer typed boundaries

Prefer:

```text
LLM
 ↓
MissionIntent
```

over:

```text
LLM
 ↓
arbitrary String
```

---

## Tools expose capabilities

The LLM should not know internal implementation details.

---

## RAG provides knowledge, not application state

Live data comes from tools.

---

## Memory provides conversation context, not truth

Current drone state always comes from the simulator.

---

## MCP changes integration, not business logic

Do not duplicate domain logic inside the MCP layer.

---

## Keep the project understandable

This is a learning project.

Prefer simple, explicit architecture over unnecessary abstractions.

---

# 34. Final Architecture Summary

```text
                        USER
                         │
                  Text / Voice
                         │
                         ▼
                   Mission Agent
                     Spring AI
                         │
        ┌────────────────┼────────────────┐
        │                │                │
      Memory            RAG             Tools
        │                │                │
        │         Operational Docs        │
        │                                 ▼
        │                          Java Services
        │                                 │
        └────────────────┬────────────────┘
                         │
                         ▼
                    MissionPlan
                         │
                         ▼
               MissionSafetyValidator
                         │
                  ACCEPT / REJECT
                         │
                         ▼
                 Drone World Simulator
                         │
                         ▼
                  Inspection Image
                         │
                         ▼
                   Vision Model
                         │
                         ▼
                  MissionReport

External capability boundary:

Mission Agent
      │
      │ MCP
      ▼
Drone MCP Server
      │
      ▼
Java Services
```

The central architectural idea remains:

> **AI plans. Java enforces.**

Spring AI provides interpretation, reasoning, retrieval, tool orchestration and multimodal capabilities.

Java remains responsible for state, deterministic domain logic and hard safety constraints.
