package pl.stalostech.ai_drone_mission_commander.api;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

import com.openai.errors.OpenAIIoException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import pl.stalostech.ai_drone_mission_commander.agent.ChatService;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ChatStreamControllerTest {
    private final ChatService service = mock(ChatService.class);
    private final ChatStreamController controller = new ChatStreamController(service, new ChatExceptionHandler());

    @Test
    void emitsJsonDeltasAndOneDoneEventPreservingWhitespace() {
        when(service.stream(eq("question"), any())).thenReturn(Flux.just("Check", " ", "battery.\nThen GPS."));
        StepVerifier.create(controller.stream("question", null, null))
                .assertNext(event -> { assertThat(event.event()).isEqualTo("delta"); assertThat(event.data().text()).isEqualTo("Check"); })
                .assertNext(event -> assertThat(event.data().text()).isEqualTo(" "))
                .assertNext(event -> assertThat(event.data().text()).isEqualTo("battery.\nThen GPS."))
                .assertNext(event -> assertThat(event.event()).isEqualTo("done"))
                .expectComplete().verify(Duration.ofSeconds(2));
    }

    @Test
    void emitsSanitizedErrorAfterPartialAnswerWithoutDone() {
        when(service.stream(eq("question"), any())).thenReturn(Flux.concat(
                Flux.just("Partial answer"), Flux.error(new OpenAIIoException("secret-provider-message"))));
        StepVerifier.create(controller.stream("question", null, null))
                .assertNext(event -> assertThat(event.event()).isEqualTo("delta"))
                .assertNext(event -> {
                    assertThat(event.event()).isEqualTo("error");
                    assertThat(event.data().error().getStatus()).isEqualTo(503);
                    assertThat(event.data().error().getDetail()).doesNotContain("secret-provider-message");
                }).expectComplete().verify(Duration.ofSeconds(2));
    }

    @Test
    void emptyProviderStreamIsAnError() {
        when(service.stream(eq("question"), any())).thenReturn(Flux.empty());
        StepVerifier.create(controller.stream("question", null, null))
                .assertNext(event -> {
                    assertThat(event.event()).isEqualTo("error");
                    assertThat(event.data().error().getStatus()).isEqualTo(502);
                }).expectComplete().verify(Duration.ofSeconds(2));
    }

    @Test
    void whitespaceOnlyStreamEndsWithErrorAndValidationResetsForEachSubscription() {
        when(service.stream(eq("question"), any())).thenReturn(Flux.just("Answer"), Flux.just(" ", "\n", "\t"));
        var stream = controller.stream("question", null, null);
        StepVerifier.create(stream)
                .expectNextCount(1)
                .assertNext(event -> assertThat(event.event()).isEqualTo("done"))
                .expectComplete().verify(Duration.ofSeconds(2));
        StepVerifier.create(stream)
                .expectNextCount(3)
                .assertNext(event -> {
                    assertThat(event.event()).isEqualTo("error");
                    assertThat(event.data().error().getStatus()).isEqualTo(502);
                }).expectComplete().verify(Duration.ofSeconds(2));
    }

    @Test
    void cancellationReachesUpstreamAndDoesNotStartAnotherRequest() {
        var cancelled = new AtomicBoolean();
        when(service.stream(eq("question"), any())).thenReturn(
                Flux.concat(Flux.just("First"), Flux.<String>never()).doOnCancel(() -> cancelled.set(true)));
        var stream = controller.stream("question", null, null);
        verifyNoInteractions(service);
        StepVerifier.create(stream).expectNextCount(1).thenCancel().verify(Duration.ofSeconds(2));
        assertThat(cancelled).isTrue();
        verify(service).stream(eq("question"), any());
    }

    @Test
    void rejectsInvalidInputBeforeSubscription() {
        assertThatThrownBy(() -> controller.stream(" ", null, null))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> controller.stream("question", null, 0)).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(service);
    }
}
