package pl.stalostech.ai_drone_mission_commander.agent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Profile;
import pl.stalostech.ai_drone_mission_commander.agent.exception.InvalidMissionOutputException;
import pl.stalostech.ai_drone_mission_commander.domain.MissionIntent;
import pl.stalostech.ai_drone_mission_commander.agent.dto.MissionIntentOutput;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.cfg.EnumFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.type.LogicalType;

@Service
@Profile("!simulator")
public class MissionIntentService {
    private final ChatClient client;
    private final BeanOutputConverter<MissionIntentOutput> converter;

    public MissionIntentService(ChatClient.Builder builder,
            @Value("classpath:prompts/mission-intent.st") Resource prompt) throws IOException {
        client = builder.defaultSystem(prompt.getContentAsString(StandardCharsets.UTF_8)).build();
        var mapper = JsonMapper.builder()
                .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .enable(EnumFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
                .withCoercionConfig(LogicalType.Textual, config -> config
                        .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail))
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                        DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES,
                        DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES,
                        DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES,
                        DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .build();
        converter = new BeanOutputConverter<>(MissionIntentOutput.class, mapper);
    }

    public MissionIntent extract(String message, boolean nativeOutput) {
        try {
            var result = client.prompt().messages(new UserMessage(message)).call()
                    .responseEntity(converter, spec -> {
                        if (nativeOutput) {
                            spec.useProviderStructuredOutput();
                        }
                    });
            var response = result.getResponse();
            if (result.getEntity() == null || response == null || response.getResult() == null
                    || !"stop".equalsIgnoreCase(response.getResult().getMetadata().getFinishReason())) {
                throw new InvalidMissionOutputException();
            }
            var output = result.getEntity();
            return new MissionIntent(output.droneId(), output.type(), output.targetSector(), output.returnHome());
        } catch (JacksonException | IllegalArgumentException exception) {
            // Never expose or log rejected model content or parser exception messages.
            throw new InvalidMissionOutputException();
        }
    }
}
