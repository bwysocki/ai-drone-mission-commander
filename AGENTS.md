# Project

Drone Mission AI is a learning project demonstrating modern Spring AI concepts
using a simulated drone environment.

The project must remain pure Java/Spring.
Do NOT introduce PX4, ROS2, Gazebo or Python.

# Main goal

The project demonstrates:

- ChatClient
- prompts and messages
- structured output
- tool calling
- ToolCallingAdvisor / agent loop
- Advisors
- Chat Memory
- embeddings
- VectorStore
- ETL
- RAG
- multimodal / vision
- speech-to-text
- text-to-speech
- MCP server
- MCP client
- observability
- evaluation

Optional:
- Agent Skills
- subagents
- A2A

# Architecture principle

AI plans. Java enforces.

LLMs may:
- interpret natural language
- select tools
- plan missions
- reason over retrieved knowledge
- summarize results

LLMs must NOT implement deterministic safety rules.

Safety constraints belong in Java code, primarily MissionSafetyValidator.

# Technology

- Java 25
- Spring Boot 4.1.x
- Spring AI 2.0.x
- Maven
- JUnit 5
- AssertJ

Prefer Spring AI APIs over custom abstractions when the goal is to demonstrate
a Spring AI feature.

# Domain

The application contains a deterministic Drone World Simulator.

Main domain concepts:

- Drone
- DroneStatus
- Mission
- MissionIntent
- MissionPlan
- MissionReport
- Sector
- Position
- Weather
- InspectionResult

No real drone hardware integration is required.

# Package structure

api/
agent/
tools/
domain/
simulation/
safety/
rag/
memory/
multimodal/
mcp/

Keep domain logic separate from AI orchestration.

# Development rules

- Prefer records for immutable DTO/domain values.
- Prefer constructor injection.
- Do not use Lombok.
- Do not create unnecessary abstractions.
- Keep methods small and names explicit.
- Use Spring Boot conventions.
- Add tests for deterministic domain logic.
- Do not mock domain logic when an in-memory implementation is sufficient.
- Never hide hard safety rules inside prompts.

# Testing

Run:

./mvnw test

before considering a task complete.

For changes affecting agent behavior, add or update a scenario test.

# Documentation

When implementing a significant Spring AI concept,
also update docs/article-notes.md with:

- problem being solved
- Spring AI concept used
- important code snippet or file
- lesson learned
- anything surprising

# Current implementation plan

See docs/PLAN.md.

Always check the current milestone before implementing features that belong
to a later milestone.