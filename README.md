# AI Drone Mission Commander

A learning project built with Java and Spring AI, being developed to support
mission planning in a simulated drone environment.

## Requirements

- JDK 25 (`java -version` should report this version).
- Internet access for the initial build to download Maven and dependencies.
- An OpenAI API key for a standard application startup.

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

The application answers questions; it does not execute or validate drone missions yet.

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
observe incremental arrival. See [Milestone 2 notes](docs/article-notes.md) and
[scenarios](docs/scenarios.md) for the implementation and prompt experiment.
