package pl.stalostech.ai_drone_mission_commander.rag;

import java.util.List;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import pl.stalostech.ai_drone_mission_commander.rag.exception.InvalidKnowledgeQueryException;

@Service
@Profile("!simulator")
public class KnowledgeSearchService {
    private final KnowledgeCatalog catalog;
    private final EmbeddingModel embeddingModel;
    private final KnowledgePipeline pipeline;
    private volatile IndexSnapshot index;

    public KnowledgeSearchService(KnowledgeCatalog catalog, EmbeddingModel embeddingModel, KnowledgePipeline pipeline) {
        this.catalog = catalog;
        this.embeddingModel = embeddingModel;
        this.pipeline = pipeline;
    }

    /** Serialize ingestion and atomically publish the complete index with its fingerprint. */
    public synchronized KnowledgeIngestionResult index(boolean force) {
        var documents = catalog.documents();
        var chunks = pipeline.apply(documents);
        String fingerprint = pipeline.fingerprint(chunks);
        if (!force && index != null && index.fingerprint().equals(fingerprint)) {
            return new KnowledgeIngestionResult(documents.size(), chunks.size(), false);
        }
        var candidate = SimpleVectorStore.builder(embeddingModel).build();
        candidate.write(chunks);
        index = new IndexSnapshot(candidate, fingerprint);
        return new KnowledgeIngestionResult(documents.size(), chunks.size(), true);
    }

    private synchronized IndexSnapshot ensureIndex() {
        if (index == null) index(false);
        return index;
    }

    public List<Document> chunks() { return pipeline.apply(catalog.documents()); }

    private record IndexSnapshot(SimpleVectorStore store, String fingerprint) {}

    public List<Document> search(String query, int topK, double threshold, KnowledgeType type, KnowledgeTopic topic) {
        validateSearch(query, topK, threshold);
        var current = index;
        if (current == null) current = ensureIndex();
        var request = SearchRequest.builder().query(query).topK(topK).similarityThreshold(threshold);
        var filters = new FilterExpressionBuilder();
        FilterExpressionBuilder.Op filter = null;
        if (type != null) filter = filters.eq("type", type.name());
        if (topic != null) {
            var byTopic = filters.eq("topic", topic.name());
            filter = filter == null ? byTopic : filters.and(filter, byTopic);
        }
        if (filter != null) request.filterExpression(filter.build());
        return current.store().similaritySearch(request.build());
    }
    public static void validateSearch(String query, int topK, double threshold) {
        if (query == null || query.isBlank() || query.length() > 2000) {
            throw new InvalidKnowledgeQueryException("query must contain 1 to 2000 nonblank characters");
        }
        if (topK < 1 || topK > 6) throw new InvalidKnowledgeQueryException("topK must be between 1 and 6");
        if (!Double.isFinite(threshold) || threshold < 0 || threshold > 1) {
            throw new InvalidKnowledgeQueryException("similarityThreshold must be between 0 and 1");
        }
    }

}
