package pl.stalostech.ai_drone_mission_commander.rag;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.TextReader;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

@Component
@Profile("!simulator")
public class KnowledgeCatalog {
    private final Function<String, Resource> resources;

    public KnowledgeCatalog() { this(ClassPathResource::new); }

    KnowledgeCatalog(Function<String, Resource> resources) { this.resources = resources; }

    /** Read a fresh snapshot; no provider calls. The explicit catalog owns trusted classification metadata. */
    public List<Document> documents() {
        return List.of(
                read("battery-policy.md", "Battery readiness", KnowledgeType.SAFETY, KnowledgeTopic.BATTERY),
                read("weather-policy.md", "Weather readiness", KnowledgeType.SAFETY, KnowledgeTopic.WEATHER),
                read("gps-failure.md", "GPS failure", KnowledgeType.SAFETY, KnowledgeTopic.GPS),
                read("mission-procedure.md", "Mission preparation", KnowledgeType.PROCEDURE, KnowledgeTopic.MISSION),
                read("emergency-procedure.md", "Fault escalation", KnowledgeType.PROCEDURE, KnowledgeTopic.EMERGENCY),
                read("inspection-procedure.md", "Sector inspection", KnowledgeType.PROCEDURE, KnowledgeTopic.INSPECTION));
    }

    private Document read(String file, String title, KnowledgeType type, KnowledgeTopic topic) {
        String source = "knowledge/" + file;
        var reader = new TextReader(resources.apply(source));
        reader.setCharset(StandardCharsets.UTF_8);
        String text = reader.get().getFirst().getText();
        if (text == null || text.isBlank()) throw new IllegalStateException("Knowledge document must not be empty: " + source);
        // Keep a portable source path rather than a machine-specific resource URI.
        return new Document(file, text, Map.of("source", source, "title", title, "type", type.name(), "topic", topic.name()));
    }
}
