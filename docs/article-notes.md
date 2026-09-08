# Milestone 2 — Prompts, Options and Streaming

## Problem

The first chat endpoints returned a complete answer using a fixed system message.
This milestone makes the instructions reusable, lets callers override generation
settings and adds incremental delivery while keeping the same Spring MVC application.

## Shared instructions and separate message roles

`src/main/resources/prompts/mission-assistant.st` contains the system prompt.
`ChatService` reads it through a Spring `Resource` at startup and configures
`ChatClient.Builder.defaultSystem(...)`. The direct `ChatModel` path uses the same
text in a `SystemMessage`; each request supplies a separate `UserMessage`.
User text is passed as a message, so braces in JSON are literal, not template variables.

The prompt tells the assistant to distinguish facts from assumptions and avoid
claiming execution. It is behavioral guidance, not a safety validator. Actual drone
execution and deterministic mission safety belong to later milestones.

To experiment, append one of these instructions to the resource, restart and send
exactly the same preflight question:

1. `Answer with a short numbered checklist.`
2. `Explain each preflight check and explicitly list missing information.`

Compare length, structure and treatment of missing telemetry. Keep the factual and
execution constraints in both variants. Automated tests verify that changed resource
content reaches both call styles; they do not assess the quality of a live model's
response. No live prompt evaluation was performed for this implementation.

## Default options and request overrides

`application.properties` supplies a completion budget of 2048 tokens. Spring AI
supplies the default model, or the operator can configure `spring.ai.openai.chat.model`.
The REST DTO `ChatRequestOptions` exposes only `model` and `maxCompletionTokens`.
The same values are query parameters on the streaming endpoint.

`ChatInput` validates these values and produces a sparse `OpenAiChatOptions.Builder`.
The fluent path passes it to `request.options(...)`. The direct model path merges
it with the configured model options before constructing the `Prompt`:

```java
var effectiveOptions = options == null ? null : OpenAiChatOptions.builder()
        .combineWith(chatModel.getOptions().mutate()).combineWith(options).build();
```

An important discovery in Spring AI 2.0.1: building native `OpenAiChatOptions` too
soon supplies the native default model even when the request only overrides the
token budget. Passing that built object's options can silently replace the configured
model. Keeping overrides as a builder until they are merged preserves omitted values.
A regression test verifies explicit overrides, a token-only override and a subsequent
request without overrides for both POST paths. Each request uses fresh options.

Temperature is not forced because model support differs. The completion limit may
include reasoning tokens; it is not a guaranteed visible answer length.

## One complete response versus a stream

The two paths use the same prompt and options:

```java
request(message, options).call().chatResponse();  // complete response + metadata
request(message, options).stream().content();    // Flux<String> text fragments
```

`ChatService.stream` defers construction until subscription. `ChatStreamController`
returns `Flux<ServerSentEvent<ChatStreamEvent>>`; Spring MVC subscribes and writes
SSE through its asynchronous response handling. No application code manually
subscribes, blocks or collects the entire response.

```text
GET /api/chat/stream
  -> MVC subscribes to Flux
  -> ChatClient -> OpenAiChatModel -> provider streaming HTTP request
  <- text fragments <- provider chunks
  -> delta events -> done
```

Chunks may contain words, spaces or newlines; chunk boundaries are not token
boundaries. JSON event payloads preserve text safely, including embedded newlines.
Empty strings are skipped; whitespace fragments are retained.

The event contract is deliberately small:

| Event | JSON data | Meaning |
|---|---|---|
| `delta` | `{"text":"Check "}` | Append the text exactly |
| `done` | `{}` | Successful completion; close the connection |
| `error` | `{"error":{"status":503,"detail":"..."}}` | Incomplete answer; close the connection |

Input validation happens before streaming and returns HTTP 400. Provider failures
are encoded inside an SSE event under HTTP 200, including failures before the first
text fragment. The controller reuses the existing provider error classifier and
safe logging; raw provider messages and credentials are not returned. A failed
stream does not emit `done`. This text-only endpoint does not collect usage metadata.

The native async SDK wraps errors in `CompletionException`; the controller unwraps
async exceptions before classification. Spring AI's `MessageAggregator` otherwise
logs the full raw provider exception, so that specific logger is disabled. Our
sanitized handler still logs status, allowlisted code and exception type. A local
HTTP 429 test verifies both the SSE status and absence of raw provider text in logs.

Cancellation propagates through the Flux chain to the provider subscription. With
Servlet MVC, client disconnection is usually discovered when writing; cancellation
is not an immediate guarantee during a silent provider interval. A 120-second async
timeout bounds the MVC request. Network loss or timeout can prevent delivery of a
terminal event. Clients should treat a stream without `done` as incomplete.

Native browser `EventSource` reconnects automatically, so close it on terminal events.
Swagger documents the endpoint; use the README's `curl -N` example to see chunks
arrive without client buffering.

## Verification without a real model

Most checks remain plain JUnit/Mockito or MVC slice tests. `StepVerifier` covers
fragment order, partial failure, empty streams and cancellation. A real `ChatClient`
with a mocked `ChatModel` also verifies that cancellation reaches the model publisher.

The existing small full-context suite uses the local HTTP provider stub, dummy
credentials and disabled retries. Its streaming test deliberately holds back the
second provider chunk until the first SSE fragment is visible in the MVC response.
This catches accidental response buffering without waiting for a real model.
It also verifies the actual outbound model and completion token settings.

## References

- [Spring AI ChatClient](https://docs.spring.io/spring-ai/reference/api/chatclient.html)
- [Spring AI OpenAI Chat options](https://docs.spring.io/spring-ai/reference/api/chat/openai-chat.html)
- [Spring MVC asynchronous requests](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-async.html)

API details were checked against the project's Spring AI 2.0.1 dependency.
