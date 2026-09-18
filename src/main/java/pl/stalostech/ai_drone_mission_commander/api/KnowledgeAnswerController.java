package pl.stalostech.ai_drone_mission_commander.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;
import pl.stalostech.ai_drone_mission_commander.api.dto.*;
import pl.stalostech.ai_drone_mission_commander.api.mapper.*;
import pl.stalostech.ai_drone_mission_commander.rag.*;

@RestController
@Profile("!simulator")
@Tag(name = "Knowledge answers", description = "Compare policy answers with and without RAG; no tools or conversation memory")
public class KnowledgeAnswerController {
    private final KnowledgeAnswerService answers;
    private final KnowledgeRag rag;

    public KnowledgeAnswerController(KnowledgeAnswerService answers, KnowledgeRag rag) {
        this.answers = answers; this.rag = rag;
    }

    @PostMapping(value = "/api/knowledge/ask", produces = "application/json")
    @Operation(summary = "Ask about operational procedures", description = "RAG defaults to enabled. useRag=false skips retrieval; rag options are still validated. Returns the exact selected chunks alongside the answer. Empty retrieval is not evidence that a mission is safe.")
    public KnowledgeAskReply ask(@RequestBody KnowledgeAskRequest request) {
        ChatRequestMapper.validateMessage(request.message());
        var selection = RagMapper.selection(request.message(), request.rag());
        var session = Boolean.FALSE.equals(request.useRag()) ? null : rag.prepare(selection);
        return new KnowledgeAskReply(ChatResponseMapper.toReply(answers.answer(request.message(), session)), RagMapper.context(session));
    }
}
