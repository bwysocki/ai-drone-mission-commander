package pl.stalostech.ai_drone_mission_commander.rag;

import java.util.List;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import pl.stalostech.ai_drone_mission_commander.rag.exception.KnowledgeIndexNotReadyException;
import pl.stalostech.ai_drone_mission_commander.rag.exception.InvalidKnowledgeQueryException;

@Service
@Profile("!simulator")
public class KnowledgeSearchService {
    private final KnowledgeCatalog catalog;
    private final EmbeddingModel embeddingModel;
    private volatile SimpleVectorStore index;

    public KnowledgeSearchService(KnowledgeCatalog catalog, EmbeddingModel embeddingModel) {
        this.catalog = catalog;
        this.embeddingModel = embeddingModel;
    }

    /** Explicit rebuild. Publish only after every document was embedded successfully. */
    public synchronized int index() {
        var documents = catalog.documents();
        var candidate = SimpleVectorStore.builder(embeddingModel).build();
        candidate.add(documents);
        index = candidate;
        return documents.size();
    }

    public List<Document> search(String query, int topK, double threshold, KnowledgeType type, KnowledgeTopic topic) {
        if (query == null || query.isBlank() || query.length() > 2000) {
            throw new InvalidKnowledgeQueryException("query must contain 1 to 2000 nonblank characters");
        }
        if (topK < 1 || topK > 6) throw new InvalidKnowledgeQueryException("topK must be between 1 and 6");
        if (!Double.isFinite(threshold) || threshold < 0 || threshold > 1) {
            throw new InvalidKnowledgeQueryException("similarityThreshold must be between 0 and 1");
        }
        var current = index;
        if (current == null) throw new KnowledgeIndexNotReadyException();
        var request = SearchRequest.builder().query(query).topK(topK).similarityThreshold(threshold);
        var filters = new FilterExpressionBuilder();
        FilterExpressionBuilder.Op filter = null;
        if (type != null) filter = filters.eq("type", type.name());
        if (topic != null) {
            var byTopic = filters.eq("topic", topic.name());
            filter = filter == null ? byTopic : filters.and(filter, byTopic);
        }
        if (filter != null) request.filterExpression(filter.build());
        return current.similaritySearch(request.build());
    }
}
