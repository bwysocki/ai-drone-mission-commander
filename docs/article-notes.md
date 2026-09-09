# Milestone 3 — Structured Output

## Problem

A free-form answer cannot serve as a dependable input to mission orchestration.
This milestone extracts a typed intent from a command such as:

```text
Send Alpha to Bravo, inspect the area and return home.
```

The expected domain result is:

```java
new MissionIntent("alpha", MissionType.INSPECTION, "BRAVO", true);
```

The endpoint is `POST /api/missions/intent`. It interprets a command without checking
world state or executing a mission. Request and response examples are in the README
and Swagger UI's Missions group.

## Spring AI concepts

`MissionIntentService` creates a dedicated `ChatClient` with instructions loaded
from `prompts/mission-intent.st`. The user's command is passed as a `UserMessage`,
so JSON braces in user input remain literal text.

The service uses `BeanOutputConverter<MissionIntentOutput>` with a strict Jackson
mapper. The converter derives the provider schema from the output record, supplies
format instructions and parses the response. The essential call is:

```java
var result = client.prompt()
        .messages(new UserMessage(message))
        .call()
        .responseEntity(converter, spec -> {
            if (nativeOutput) {
                spec.useProviderStructuredOutput();
            }
        });
```

`responseEntity` retains both the typed result and the original `ChatResponse`.
That lets the service require a `STOP` finish reason before accepting the intent.
A syntactically valid object returned after a token limit or filtering is rejected.

Two modes are exposed on the same endpoint:

| Request | Schema delivery |
|---|---|
| `/api/missions/intent` | Format instructions in the prompt |
| `/api/missions/intent?nativeOutput=true` | Provider-native JSON Schema |

The local HTTP integration tests verify that native mode sends
`response_format.type=json_schema`, `strict=true`, all four required properties
and `additionalProperties=false`. Prompt mode includes schema instructions instead.
The selected provider model must support native structured output; there is no silent
fallback if it rejects the format.

## Untrusted output versus domain values

`agent.dto.MissionIntentOutput` models the provider response. Its schema permits
explicit nulls for unknown drone IDs, sectors and mission types. This matters for
native output: mandatory non-null values would conflict with instructions not to
invent details when a command is incomplete.

All fields must still be present. The service's strict parser rejects null values,
then constructs the validated domain object:

```java
var output = result.getEntity();
return new MissionIntent(
        output.droneId(), output.type(), output.targetSector(), output.returnHome());
```

`MissionIntent` trims identifiers, normalizes their case with `Locale.ROOT` and
checks their syntax. It requires a supported `MissionType`: `INSPECTION` or `PATROL`.
Drone IDs use lowercase and sector IDs uppercase. Whether those identifiers exist
will be checked against the simulator in a later milestone.

The extraction instruction sets `returnHome=false` when no return is requested.
A missing JSON boolean is nevertheless an output error; Java must not silently
replace an omitted field with the primitive default.

The other domain records establish small contracts for future work:

- `MissionPlan`: intent and proposed steps; a proposal is not execution approval.
- `MissionAssessment`: status and reasons, reserved for deterministic Java rules.
- `MissionReport`: intent, outcome, summary and observations from execution.

Their constructors validate required values and create immutable copies of lists.
This milestone does not expose endpoints that ask the model to invent assessments
or completed mission reports.

## Validation and errors

The dedicated JSON mapper rejects scalar coercion, unknown fields, missing fields,
nulls, duplicate keys, numeric enums and trailing JSON values. Domain constructors
reject blank or malformed identifiers. `InvalidMissionOutputException` deliberately
contains no original parser exception or model content.

The API returns:

- 400 for invalid request input.
- 502 with title `Invalid AI output` for rejected model output, including incomplete
  mission details or abnormal generation completion.
- The existing sanitized provider 502/503 responses for provider failures.

Malformed output is not repaired through another model call. Spring AI also offers
`validateSchema()` with a self-correcting retry loop; this milestone uses strict
conversion and deterministic value validation instead. SDK transport retries remain
configured independently.

Schema compliance establishes structure, not truth. Tests cannot prove that a live
model will identify the right drone or understand every ambiguous command. The
prompt instructs it to mark unknown or ambiguous fields as null; Java rejects that
output. A fabricated but well-formed identifier still requires later verification.

## Verification

Most checks use plain JUnit and Mockito with a real ChatClient and converter.
They cover canonical intent extraction, literal user input, invalid JSON, coercion,
missing fields, unknown enums, output truncation and provider error propagation.
Domain tests verify normalization and immutable collection snapshots. MVC slice
tests verify request validation and the 400/502/503 API boundary.

The existing localhost provider fixture additionally checks both schema delivery
modes over the real SDK, rejected output in both modes, sanitized logs and OpenAPI.
It uses dummy credentials and disabled transport retries. No live model was called.

For a manual comparison, send the README command once in each mode, then try an
incomplete command such as `Inspect the area.`. Inspect semantic accuracy as well
as the returned HTTP status. Live evaluation remains separate from automated tests.

## Lessons

A record alone is not sufficient validation. Required fields and strict conversion
prevent missing booleans and coerced strings from becoming plausible domain data.
Native schemas also need a way to represent missing information, while the domain
can require complete values. Keeping provider output separate makes both contracts
explicit. Generation metadata remains useful even when the answer parses correctly.

References:

- [Spring AI native structured output](https://docs.spring.io/spring-ai/reference/api/structured-output/native.html)
- [Spring AI schema validation and self-correction](https://docs.spring.io/spring-ai/reference/api/structured-output/validation.html)

Implementation checked against the project's Spring AI 2.0.1 and Jackson 3 dependencies.
