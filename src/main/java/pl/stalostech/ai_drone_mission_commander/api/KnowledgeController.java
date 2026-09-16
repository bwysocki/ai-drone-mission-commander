package pl.stalostech.ai_drone_mission_commander.api;

import java.util.List;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.ai.document.Document;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;
import pl.stalostech.ai_drone_mission_commander.api.dto.*;
import pl.stalostech.ai_drone_mission_commander.rag.KnowledgeCatalog;
import pl.stalostech.ai_drone_mission_commander.rag.KnowledgeSearchService;

@RestController
@Profile("!simulator")
@RequestMapping(value = "/api/knowledge", produces = "application/json")
@Tag(name = "Knowledge search", description = "Semantic retrieval using embeddings, without a chat model or RAG")
public class KnowledgeController {
    private final KnowledgeCatalog catalog;
    private final KnowledgeSearchService search;

    public KnowledgeController(KnowledgeCatalog catalog, KnowledgeSearchService search) {
        this.catalog = catalog;
        this.search = search;
    }

    @GetMapping("/documents")
    @Operation(summary = "Inspect the six bundled documents", description = "Text and metadata; no provider call. score is null before retrieval.")
    public List<KnowledgeDocument> documents() { return catalog.documents().stream().map(KnowledgeController::reply).toList(); }

    @PostMapping("/index")
    @Operation(summary = "Build or replace the in-memory index", description = "Embeds all six documents using the configured embedding model. May incur provider charges. An unsuccessful rebuild preserves the previous index. Restart loses the index.")
    public KnowledgeIndexReply index() { return new KnowledgeIndexReply(search.index()); }

    @PostMapping("/search")
    @Operation(summary = "Find relevant procedures", description = "Embeds the query and returns scored documents, without generating an answer. Optional type/topic filters are combined with AND. Requires indexing first (409); invalid input returns 400; provider errors return 502/503.")
    public List<KnowledgeDocument> search(@RequestBody KnowledgeSearchRequest request) {
        return search.search(request.query(), request.topK() == null ? 3 : request.topK(),
                request.similarityThreshold() == null ? 0 : request.similarityThreshold(), request.type(), request.topic())
                .stream().map(KnowledgeController::reply).toList();
    }

    private static KnowledgeDocument reply(Document document) {
        return new KnowledgeDocument(document.getId(), document.getText(), document.getMetadata(), document.getScore());
    }
}
