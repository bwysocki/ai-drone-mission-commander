## Milestone 1 — Spring AI Fundamentals

### Problem

Expose a first REST conversation and understand what Spring AI sends to the model
and returns to application code. Keep the build independent of real credentials.

### Spring AI concept

`ChatModel` is the provider abstraction. `ChatClient` offers a fluent API on top of
that abstraction. Spring Boot supplies an OpenAI `ChatModel` and a prototype
`ChatClient.Builder` through autoconfiguration when the chat model is enabled.
See the official [Chat Model API](https://docs.spring.io/spring-ai/reference/api/chatmodel.html)
and [Chat Client API](https://docs.spring.io/spring-ai/reference/api/chatclient.html).

### Implementation and important code

- `src/main/java/pl/stalostech/ai_drone_mission_commander/agent/ChatService.java`:
  two minimal invocation styles using the same instructions and user input.
- `src/main/java/pl/stalostech/ai_drone_mission_commander/api/ChatController.java`:
  `POST /api/chat` uses `ChatClient`; `POST /api/chat/model` uses `ChatModel` directly.
- `src/main/java/pl/stalostech/ai_drone_mission_commander/api/ChatResponseMapper.java`:
  extracts the first generation, its assistant message and selected metadata.
- `src/main/java/pl/stalostech/ai_drone_mission_commander/api/dto/`:
  contains the HTTP request and response records, `ChatRequest` and `ChatReply`.
  `ChatReply` only holds data; the mapper handles Spring AI conversion and rejects
  responses without text with HTTP 502.
- `src/test/java/pl/stalostech/ai_drone_mission_commander/AiDroneMissionCommanderApplicationTests.java`:
  verifies autoconfiguration and both REST flows against a local HTTP provider stub.

Direct model invocation:

```java
Prompt prompt = new Prompt(List.of(
        new SystemMessage(SYSTEM_MESSAGE),
        new UserMessage(message)));
ChatResponse response = chatModel.call(prompt);
```

Fluent client invocation:

```java
ChatResponse response = chatClient.prompt()
        .messages(new SystemMessage(SYSTEM_MESSAGE), new UserMessage(message))
        .call()
        .chatResponse();
```

### ChatModel vs ChatClient

| Aspect | Direct model example | Client example |
|---|---|---|
| Injected dependency | `ChatModel` | `ChatClient.Builder`, used to build the client |
| Input construction | Explicit `Prompt` | Fluent request with message objects |
| Invocation | `chatModel.call(prompt)` | `.call().chatResponse()` |
| Output here | `ChatResponse` | `ChatResponse` |
| Use in this project | Shows the lower-level request/response boundary | Main REST chat path; basis for later client features |

Both examples ultimately use the same autoconfigured model. The client example
keeps the complete response instead of selecting only `.content()`, because this
milestone also examines metadata.

### Objects along the request path

```text
POST /api/chat {"message": "..."}
    → ChatClient
    → Prompt [SystemMessage, UserMessage]
    → ChatModel (OpenAiChatModel)
    → provider HTTP request
    → ChatResponse
        ├── response metadata: id, model, usage
        └── Generation
            ├── generation metadata: finish reason
            └── AssistantMessage: answer text
    → ChatReply JSON
```

| Type | What to inspect in this implementation |
|---|---|
| `Prompt` | Ordered messages passed to the model; the direct example constructs it explicitly |
| `SystemMessage` | Application instructions identifying the assistant's role and lack of execution capability |
| `UserMessage` | The caller's literal question, kept separate from system instructions |
| `ChatResponse` | The complete Spring AI response, with results and response metadata |
| `Generation` | One generated candidate; `getResult()` selects the first candidate for this API |
| `AssistantMessage` | Generated answer text accessed with `getText()` |
| Response metadata | Provider response ID, model name and token usage |
| Generation metadata | Finish reason; the tested OpenAI integration maps `stop` to `STOP` |

`ToolResponseMessage` represents a tool result in Spring AI. It is not used in this
milestone; tool calling belongs to a later milestone. Model options control provider
settings such as model selection. These examples use autoconfigured defaults;
request-specific options and streaming belong to milestone 2.

### Verification

Run `./mvnw test` or `./mvnw clean install`.

The small full-context integration suite uses MockMvc for REST and a local JDK
`HttpServer` as the OpenAI provider.
They verify that the context contains exactly one `ChatModel`, that it is an
`OpenAiChatModel`, and that separate lookups yield separate builder instances.
No `ChatModel` or `ChatClient` mock replaces Spring AI in that integration suite.

Both endpoint scenarios check the provider request's system/user roles, literal
question, model, HTTP path and dummy authorization, then verify the returned text
and metadata. The remaining checks run at narrower boundaries: `ChatServiceTest`
checks literal braces with a mocked model; `ChatControllerTest` uses `@WebMvcTest`
with a mocked service for invalid input, routing and HTTP errors; plain JUnit tests
exercise the response mapper and exception handler, including diagnostic logging.

The test profile disables models by default. Dynamic test properties enable only
the chat model and override both common and chat-specific credentials and base URLs
with a dummy key and a loopback address. They disable SDK retries and set a two-second
timeout only for those tests. A 429 scenario verifies exactly one outbound request.
Real credentials are unnecessary, and production retry settings are unchanged.

For a live smoke test, start the app with your environment key and run the README's
curl example. Automated tests verify the integration protocol using a fixed response;
they do not evaluate a real model's answer quality or validate account access.

### What surprised me

- Including the OpenAI starter also enables models beyond chat. The initial context
  test failed while creating the speech model, before any chat request was made.
- In the installed Spring AI 2.0.1 integration, the provider finish reason `stop`
  becomes `STOP` in Spring AI's generation metadata.
- A passing context test with all AI models disabled does not verify chat autoconfiguration.

### What I learned

Testing against a local provider boundary covers the real model adapter and fluent
client without paying for model calls. Explicit message objects preserve literal
user input, including braces, without treating it as a prompt template.
This stage provides advice only. Deterministic mission safety and execution remain
future Java responsibilities.

### Potential article snippet

The first useful slice is a question travelling from HTTP through `ChatClient` to
`ChatModel`, then returning as a `ChatResponse`. Reading the response object shows
that an LLM result includes a generated message, a completion reason and token usage.
Keeping those visible makes the next experiments easier to understand.

### Review follow-up — API input and provider errors

Problem: Jackson accepted numeric and boolean `message` values by converting them
to strings, and provider exceptions escaped as generic HTTP 500 responses.

Implementation:

- `ChatRequest.MessageDeserializer` requires a JSON string for this specific field.
  Missing/null/blank values are still rejected by the controller. Other application
  string fields keep their existing Jackson behavior.
- `ChatExceptionHandler` handles OpenAI SDK exceptions for the chat controller.
  Rejected requests map to HTTP 502; rate limits, provider HTTP 5xx and transport
  failures map to HTTP 503. Problem Detail responses contain controlled text,
  without provider error bodies or credentials.
- MVC tests cover non-string values and HTTP error translation. Unit tests cover
  provider HTTP 400/401/403/429/500/503, transport errors and safe logging. The small
  integration suite checks real SDK handling of HTTP 429 and a dropped connection.

Lesson learned: a Java `String` field alone does not enforce a JSON string input.
Provider status codes also need an application-level interpretation: a provider's
401 concerns the application's provider credentials, not the caller's authentication.

Diagnostic follow-up: the sanitized response alone was insufficient to investigate a
reported HTTP 503. `ChatExceptionHandler` now logs the provider HTTP status, an allowlisted
error code and exception/cause class names. Tests check both HTTP and transport errors,
recognized codes and suppression of arbitrary provider messages/codes. The public
response remains sanitized; troubleshooting starts with the console's `AI provider failure` line.

A reported `providerStatus=429, providerCode=other` exposed an incomplete allowlist.
It now includes the current credit balance, organization/project spend, organization
usage and `slow_down` codes from the [OpenAI error guide](https://developers.openai.com/api/docs/guides/error-codes).
Regression tests verify that these codes remain visible while arbitrary provider
content is still suppressed. HTTP 429 alone does not distinguish billing from throttling.

### Milestone 1 — Interactive API documentation

Problem: demonstrate both chat examples directly in a browser, with a ready-to-run
question and visible response metadata.

Implementation: `springdoc-openapi-starter-webmvc-ui` 3.1.1 generates the OpenAPI
specification and serves Swagger UI. The version follows the Spring Boot 4-compatible
springdoc 3.x line described in the [official compatibility guide](https://springdoc.org/faq.html).
`OpenApiConfiguration` defines the API title and scope; controller and DTO annotations
describe operations, schemas, examples and error statuses.

Integration detail: Spring AI and the OpenAI SDK bring older Swagger annotation
artifacts, including two artifacts containing the same annotation classes. The AI
starter excludes both annotation variants so springdoc supplies its matching Jakarta
annotations. Without this, generating `/v3/api-docs` failed with a `NoSuchMethodError`
for `Schema.$dynamicRef()`, even though the UI HTML and chat tests passed.

Open `/swagger-ui/index.html`, expand an endpoint, click **Try it out**, and then
**Execute**. `/v3/api-docs` exposes the machine-readable contract. The OpenAI key stays
in server configuration. Browsing documentation does not call the model.

Verification: integration tests check UI assets, the local specification URL, both
documented operations, request/response schema references, required string input and
error descriptions. They execute the documented example against the local provider stub.

Lesson learned: Swagger UI is a useful first presentation layer for an API learning
project. It lets the reader inspect the contract and try requests without a custom frontend.

### Fast test suite

Problem: routing every error and validation case through the real SDK made routine
tests slow, especially because HTTP 429/5xx and transport failures triggered retry delays.

The suite now has three levels: plain JUnit tests, a `@WebMvcTest` controller slice
with a mocked service, and eight full-context integration cases. The complete error
classification and logging matrix runs directly against `ChatExceptionHandler`,
without Spring or sockets. Integration tests keep the real OpenAI model and disable
retries using test-only dynamic properties; a 429 case asserts exactly one request.

Local measurement: the previous run took 58.188 seconds for `./mvnw test`, with 54.67
seconds in the single integration test class. After the split, the command took 9.025
seconds, with 4.676 seconds in the remaining integration class. All 69 cases passed.
These are individual local runs, not a benchmark or a promised CI duration.

Lesson: keep HTTP tests for wiring and protocol behavior, and test combinatorial
validation and error cases at the smallest useful boundary. Changing the HTTP stub
library would not by itself remove SDK retry delays.
