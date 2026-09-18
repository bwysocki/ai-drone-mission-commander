package pl.stalostech.ai_drone_mission_commander.api.mapper;

import java.util.List;
import pl.stalostech.ai_drone_mission_commander.api.dto.*;
import pl.stalostech.ai_drone_mission_commander.rag.KnowledgeRag;
import pl.stalostech.ai_drone_mission_commander.rag.RagSelection;

public final class RagMapper {
    private RagMapper() {}

    public static RagSelection selection(String message, RagOptions options) {
        return new RagSelection(options == null || options.query() == null ? message : options.query(),
                options == null || options.topK() == null ? 3 : options.topK(),
                options == null || options.similarityThreshold() == null ? 0 : options.similarityThreshold(),
                options == null ? null : options.type(), options == null ? null : options.topic());
    }

    public static RagContext context(KnowledgeRag.Session session) {
        if (session == null) return new RagContext(false, null, List.of());
        return new RagContext(true, session.query(), session.documents().stream()
                .map(d -> new KnowledgeDocument(d.getId(), d.getText(), d.getMetadata(), d.getScore())).toList());
    }
}
