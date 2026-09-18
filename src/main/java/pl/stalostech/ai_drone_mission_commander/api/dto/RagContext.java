package pl.stalostech.ai_drone_mission_commander.api.dto;

import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Actual retrieval context, not proof that the model cited or obeyed each source")
public record RagContext(boolean enabled, String query, List<KnowledgeDocument> documents) {
    public RagContext { documents = List.copyOf(documents); }
}
