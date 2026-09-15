package pl.stalostech.ai_drone_mission_commander.memory;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.*;
import static org.assertj.core.api.Assertions.*;

class ConversationMemoryTest {
    @Test
    void defaultWindowRetainsTheLatestTwentyMessages() {
        var memory = new ConversationMemory();
        for (int turn = 0; turn < 11; turn++) {
            memory.add("A", List.of(new UserMessage("question " + turn), new AssistantMessage("answer " + turn)));
        }
        var history = memory.get("A");
        assertThat(history).hasSize(20);
        assertThat(history.getFirst().getText()).isEqualTo("question 1");
        assertThat(history.getLast().getText()).isEqualTo("answer 10");
    }

    @Test
    void storesOnlyUserAndFinalAssistantText() {
        var memory = new ConversationMemory();
        memory.add("A", List.of(new SystemMessage("private snapshot"), new UserMessage("Monitor Alpha"),
                AssistantMessage.builder().content("").toolCalls(List.of(
                        new AssistantMessage.ToolCall("id", "function", "getDroneStatus", "{}"))).build(),
                ToolResponseMessage.builder().responses(List.of(
                        new ToolResponseMessage.ToolResponse("id", "getDroneStatus", "private telemetry"))).build(),
                new AssistantMessage("Monitoring Alpha")));
        assertThat(memory.get("A")).extracting(Message::getText).containsExactly("Monitor Alpha", "Monitoring Alpha");
        assertThat(memory.get("B")).isEmpty();
        assertThatThrownBy(() -> memory.get("A").clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void evictsOldMessagesAndRestoresEvictedHistoryAfterAFailedTurn() {
        var memory = new ConversationMemory(4);
        memory.add("A", List.of(new UserMessage("one"), new AssistantMessage("reply one"),
                new UserMessage("two"), new AssistantMessage("reply two")));
        var before = memory.get("A");
        assertThatThrownBy(() -> memory.inConversation("A", () -> {
            memory.add("A", new UserMessage("failed"));
            throw new IllegalStateException("provider unavailable");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(memory.get("A")).isEqualTo(before);
        memory.inConversation("A", () -> {
            memory.add("A", new UserMessage("three"));
            memory.add("A", new AssistantMessage("reply three"));
            return null;
        });
        assertThat(memory.get("A")).extracting(Message::getText).containsExactly("two", "reply two", "three", "reply three");
        memory.clear("A");
        memory.clear("A");
        assertThat(memory.get("A")).isEmpty();
    }

    @Test
    void serializesWholeTurnsOfTheSameConversation() throws Exception {
        var memory = new ConversationMemory();
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var secondAttempted = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> memory.inConversation("A", () -> {
                memory.add("A", new UserMessage("first"));
                started.countDown();
                await(release);
                memory.add("A", new AssistantMessage("first reply"));
                return null;
            }));
            try {
                assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
                var second = executor.submit(() -> {
                    secondAttempted.countDown();
                    return memory.inConversation("A", () -> {
                        assertThat(memory.get("A")).extracting(Message::getText).containsExactly("first", "first reply");
                        memory.add("A", List.of(new UserMessage("second"), new AssistantMessage("second reply")));
                        return null;
                    });
                });
                assertThat(secondAttempted.await(2, TimeUnit.SECONDS)).isTrue();
                assertThat(second.isDone()).isFalse();
                release.countDown();
                first.get(2, TimeUnit.SECONDS);
                second.get(2, TimeUnit.SECONDS);
                assertThat(memory.get("A")).extracting(Message::getText)
                        .containsExactly("first", "first reply", "second", "second reply");
            } finally {
                release.countDown();
            }
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(2, TimeUnit.SECONDS)) throw new IllegalStateException("Timed out");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }
}
