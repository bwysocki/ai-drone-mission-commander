package pl.stalostech.ai_drone_mission_commander.rag;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import pl.stalostech.ai_drone_mission_commander.rag.exception.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class KnowledgeSearchServiceTest {
    private final EmbeddingModel embedding = mock(EmbeddingModel.class);

    private KnowledgeSearchService service() throws Exception {
        when(embedding.embed(any(Document.class))).thenAnswer(invocation -> {
            Document document = invocation.getArgument(0);
            var vector = new float[6];
            vector[KnowledgeTopic.valueOf((String) document.getMetadata().get("topic")).ordinal()] = 1;
            return vector;
        });
        when(embedding.embed(anyString())).thenReturn(new float[]{.8f, .6f, 0, 0, 0, 0});
        return new KnowledgeSearchService(new KnowledgeCatalog(), embedding);
    }

    @Test
    void catalogContainsSixWholeDocumentsWithStableIdsAndMetadata() throws Exception {
        var documents = new KnowledgeCatalog().documents();
        assertThat(documents).hasSize(6).extracting(Document::getId).doesNotHaveDuplicates()
                .contains("battery-policy.md", "weather-policy.md", "gps-failure.md",
                        "mission-procedure.md", "emergency-procedure.md", "inspection-procedure.md");
        assertThat(documents).allSatisfy(document -> {
            assertThat(document.getText()).startsWith("# ").contains("simulat");
            assertThat(document.getMetadata()).containsKeys("source", "title", "type", "topic");
            assertThat(document.getMetadata().get("source")).isEqualTo("knowledge/" + document.getId());
        });
        verifyNoInteractions(embedding);
    }

    @Test
    void ranksWithRealVectorStoreAndAppliesThresholdTopKAndCombinedFilters() throws Exception {
        var service = service();
        assertThat(service.index()).isEqualTo(6);
        var results = service.search("low energy in windy conditions", 2, 0, null, null);
        assertThat(results).extracting(Document::getId).containsExactly("battery-policy.md", "weather-policy.md");
        assertThat(results.getFirst().getScore()).isCloseTo(.8, within(.0001));
        assertThat(service.search("query", 1, 0, null, null)).hasSize(1);
        assertThat(service.search("query", 6, .7, null, null)).extracting(Document::getId).containsExactly("battery-policy.md");
        assertThat(service.search("query", 6, .5, KnowledgeType.SAFETY, KnowledgeTopic.WEATHER))
                .extracting(Document::getId).containsExactly("weather-policy.md");
        assertThat(service.search("query", 6, 0, KnowledgeType.PROCEDURE, KnowledgeTopic.BATTERY)).isEmpty();
        assertThat(service.search("query", 6, .99, null, null)).isEmpty();
    }

    @Test
    void requiresExplicitIndexingAndValidatesBeforeEmbedding() throws Exception {
        var service = service();
        assertThatThrownBy(() -> service.search("query", 3, 0, null, null)).isInstanceOf(KnowledgeIndexNotReadyException.class);
        assertThatThrownBy(() -> service.search(" ", 3, 0, null, null)).isInstanceOf(InvalidKnowledgeQueryException.class);
        assertThatThrownBy(() -> service.search("x".repeat(2001), 3, 0, null, null)).isInstanceOf(InvalidKnowledgeQueryException.class);
        for (int topK : new int[]{0, 7}) {
            assertThatThrownBy(() -> service.search("query", topK, 0, null, null)).isInstanceOf(InvalidKnowledgeQueryException.class);
        }
        for (double threshold : new double[]{-1, 1.1, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThatThrownBy(() -> service.search("query", 3, threshold, null, null)).isInstanceOf(InvalidKnowledgeQueryException.class);
        }
        verifyNoInteractions(embedding);
    }

    @Test
    void failedRebuildKeepsPreviousIndexAndSuccessfulRebuildDoesNotDuplicateDocuments() throws Exception {
        var service = service();
        service.index();
        service.index();
        assertThat(service.search("query", 6, 0, null, null)).hasSize(6);
        var attempts = new AtomicInteger();
        when(embedding.embed(any(Document.class))).thenAnswer(invocation -> {
            if (attempts.incrementAndGet() == 3) throw new IllegalStateException("Embedding provider failure");
            return new float[]{0, 1, 0, 0, 0, 0};
        });
        assertThatThrownBy(service::index).isInstanceOf(IllegalStateException.class);
        assertThat(service.search("query", 1, .7, null, null)).extracting(Document::getId).containsExactly("battery-policy.md");
    }

    @Test
    void failedInitialBuildDoesNotPublishPartialIndex() throws Exception {
        var service = service();
        when(embedding.embed(any(Document.class))).thenThrow(new IllegalStateException("Unavailable"));
        assertThatThrownBy(service::index).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.search("query", 3, 0, null, null)).isInstanceOf(KnowledgeIndexNotReadyException.class);
        verify(embedding, never()).embed(anyString());
    }
}
