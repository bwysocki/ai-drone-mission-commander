package pl.stalostech.ai_drone_mission_commander.rag;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.ai.document.Document;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
@Profile("!simulator")
public class KnowledgeCatalog {
    private final List<Entry> entries;

    public KnowledgeCatalog() throws IOException {
        var loaded = new ArrayList<Entry>();
        loaded.add(load("battery-policy.md", "Battery readiness", KnowledgeType.SAFETY, KnowledgeTopic.BATTERY));
        loaded.add(load("weather-policy.md", "Weather readiness", KnowledgeType.SAFETY, KnowledgeTopic.WEATHER));
        loaded.add(load("gps-failure.md", "GPS failure", KnowledgeType.SAFETY, KnowledgeTopic.GPS));
        loaded.add(load("mission-procedure.md", "Mission preparation", KnowledgeType.PROCEDURE, KnowledgeTopic.MISSION));
        loaded.add(load("emergency-procedure.md", "Fault escalation", KnowledgeType.PROCEDURE, KnowledgeTopic.EMERGENCY));
        loaded.add(load("inspection-procedure.md", "Sector inspection", KnowledgeType.PROCEDURE, KnowledgeTopic.INSPECTION));
        entries = List.copyOf(loaded);
    }

    private static Entry load(String file, String title, KnowledgeType type, KnowledgeTopic topic) throws IOException {
        String source = "knowledge/" + file;
        String text = new ClassPathResource(source).getContentAsString(StandardCharsets.UTF_8);
        if (text.isBlank()) throw new IllegalStateException("Knowledge document must not be empty");
        return new Entry(file, text, Map.of("source", source, "title", title, "type", type.name(), "topic", topic.name()));
    }

    public List<Document> documents() {
        return entries.stream().map(entry -> new Document(entry.id(), entry.text(), entry.metadata())).toList();
    }

    private record Entry(String id, String text, Map<String, Object> metadata) {}
}
