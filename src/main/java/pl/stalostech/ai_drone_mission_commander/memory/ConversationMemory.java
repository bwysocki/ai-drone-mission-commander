package pl.stalostech.ai_drone_mission_commander.memory;

import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** In-process conversation history. Never stores application context or raw tool exchanges. */
@Component
@Profile("!simulator")
public class ConversationMemory implements ChatMemory {
    public static final int MAX_MESSAGES = 20;
    private final ChatMemory delegate;
    // Bounded lock storage; colliding conversations may wait, but never share history.
    private final ReentrantLock[] locks = IntStream.range(0, 64)
            .mapToObj(index -> new ReentrantLock()).toArray(ReentrantLock[]::new);

    public ConversationMemory() { this(MAX_MESSAGES); }

    public ConversationMemory(int maxMessages) {
        if (maxMessages < 2 || maxMessages % 2 != 0) {
            throw new IllegalArgumentException("Memory window must be a positive number of user/assistant pairs");
        }
        delegate = MessageWindowChatMemory.builder().maxMessages(maxMessages).build();
    }

    private ReentrantLock lock(String id) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Conversation ID is required");
        return locks[Math.floorMod(id.hashCode(), locks.length)];
    }

    @Override
    public void add(String id, List<Message> messages) {
        var lock = lock(id);
        lock.lock();
        try {
            var conversational = messages.stream().filter(message -> message instanceof UserMessage
                    || message instanceof AssistantMessage assistant && assistant.getToolCalls().isEmpty())
                    .map(ConversationMemory::textOnly).toList();
            if (!conversational.isEmpty()) delegate.add(id, conversational);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public List<Message> get(String id) {
        var lock = lock(id);
        lock.lock();
        try {
            return delegate.get(id).stream().map(ConversationMemory::textOnly).toList();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void clear(String id) {
        var lock = lock(id);
        lock.lock();
        try { delegate.clear(id); }
        finally { lock.unlock(); }
    }

    /** Serialize whole turns and hide partial writes from readers; failed turns leave no history. */
    public <T> T inConversation(String id, Supplier<T> action) {
        var lock = lock(id);
        lock.lock();
        try {
            var before = get(id);
            try {
                return action.get();
            } catch (RuntimeException exception) {
                delegate.clear(id);
                if (!before.isEmpty()) delegate.add(id, before);
                throw exception;
            }
        } finally {
            lock.unlock();
        }
    }

    private static Message textOnly(Message message) {
        return message instanceof UserMessage ? new UserMessage(message.getText())
                : new AssistantMessage(message.getText());
    }
}
