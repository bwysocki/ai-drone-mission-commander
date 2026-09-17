package pl.stalostech.ai_drone_mission_commander.rag;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.core.io.FileSystemResource;
import pl.stalostech.ai_drone_mission_commander.rag.exception.InvalidKnowledgeQueryException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class KnowledgeSearchServiceTest {
    private final EmbeddingModel embedding = mock(EmbeddingModel.class);
    private final KnowledgePipeline pipeline = new KnowledgePipeline();

    private KnowledgeSearchService service(KnowledgeCatalog catalog) {
        when(embedding.embed(any(Document.class))).thenAnswer(invocation -> {
            Document document = invocation.getArgument(0);
            var vector = new float[6];
            vector[KnowledgeTopic.valueOf((String) document.getMetadata().get("topic")).ordinal()] = 1;
            return vector;
        });
        when(embedding.embed(anyString())).thenReturn(new float[]{.8f, .6f, 0, 0, 0, 0});
        return new KnowledgeSearchService(catalog, embedding, pipeline);
    }

    @Test
    void readsSplitsAndTransformsDeterministicallyWithoutEmbedding() {
        var catalog = new KnowledgeCatalog();
        var documents = catalog.documents();
        assertThat(documents).hasSize(6);
        var chunks = pipeline.apply(documents);
        assertThat(chunks.size()).isGreaterThan(6);
        assertThat(chunks).extracting(Document::getId).doesNotHaveDuplicates();
        assertThat(pipeline.fingerprint(chunks)).isEqualTo(pipeline.fingerprint(pipeline.apply(catalog.documents())));
        for (var source : documents) {
            var parts = chunks.stream().filter(d -> source.getId().equals(d.getMetadata().get("documentId"))).toList();
            assertThat(parts).isNotEmpty();
            for (int i = 0; i < parts.size(); i++) {
                assertThat(parts.get(i).getMetadata()).containsAllEntriesOf(source.getMetadata())
                        .containsEntry("chunkIndex", i).containsEntry("chunkCount", parts.size());
            }
            // Splitting must retain the final sentence too, including a short final chunk.
            String joined = parts.stream().map(Document::getText).collect(java.util.stream.Collectors.joining(" "));
            assertThat(joined.replaceAll("\\s+", " ")).isEqualTo(source.getText().strip().replaceAll("\\s+", " "));
        }
        var crlf = documents.stream().map(d -> new Document(d.getId(), d.getText().replace("\n", "\r\n"), d.getMetadata())).toList();
        assertThat(pipeline.fingerprint(pipeline.apply(crlf))).isEqualTo(pipeline.fingerprint(chunks));
        verifyNoInteractions(embedding);
    }

    @Test
    void lazilyIngestsThenRanksAndFiltersChunksWithoutDuplicateEmbedding() {
        var service = service(new KnowledgeCatalog());
        var result = service.search("energy", 1, .7, null, null);
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getMetadata()).containsEntry("topic", "BATTERY");
        assertThat(result.getFirst().getScore()).isCloseTo(.8, within(.0001));
        int count = service.chunks().size();
        var skipped = service.index(false);
        assertThat(skipped).isEqualTo(new KnowledgeIngestionResult(6, count, false));
        verify(embedding, times(count)).embed(any(Document.class));
        assertThat(service.search("query", 6, .5, KnowledgeType.SAFETY, KnowledgeTopic.WEATHER))
                .isNotEmpty().allSatisfy(d -> assertThat(d.getMetadata()).containsEntry("topic", "WEATHER"));
        assertThat(service.search("query", 6, 0, KnowledgeType.PROCEDURE, KnowledgeTopic.BATTERY)).isEmpty();
        assertThat(service.search("query", 6, .99, null, null)).isEmpty();
    }

    @Test
    void rejectsInvalidQueriesBeforeAutomaticIngestion() {
        var service = service(new KnowledgeCatalog());
        for (String query : new String[]{null, " ", "x".repeat(2001)}) {
            assertThatThrownBy(() -> service.search(query, 3, 0, null, null)).isInstanceOf(InvalidKnowledgeQueryException.class);
        }
        for (int topK : new int[]{0, 7}) {
            assertThatThrownBy(() -> service.search("query", topK, 0, null, null)).isInstanceOf(InvalidKnowledgeQueryException.class);
        }
        for (double threshold : new double[]{-1, 1.1, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThatThrownBy(() -> service.search("query", 3, threshold, null, null)).isInstanceOf(InvalidKnowledgeQueryException.class);
        }
        verifyNoInteractions(embedding);
    }

    @Test
    void changedCorpusReplacesOldChunksAndFailedBuildCanBeRetried(@TempDir Path directory) throws Exception {
        for (var document : new KnowledgeCatalog().documents()) Files.writeString(directory.resolve(document.getId()), document.getText());
        var catalog = new KnowledgeCatalog(source -> new FileSystemResource(directory.resolve(Path.of(source).getFileName())));
        var service = service(catalog);
        service.index(false);
        var oldIds = service.search("query", 6, .7, null, null).stream().map(Document::getId).toList();
        Files.writeString(directory.resolve("battery-policy.md"), "# Updated battery policy\n\nA short replacement procedure.");
        var attempts = new AtomicInteger();
        when(embedding.embed(any(Document.class))).thenAnswer(invocation -> {
            if (attempts.incrementAndGet() == 2) throw new IllegalStateException("Provider unavailable");
            return new float[]{1, 0, 0, 0, 0, 0};
        });
        assertThatThrownBy(() -> service.index(false)).isInstanceOf(IllegalStateException.class);
        assertThat(service.search("query", 6, .7, null, null)).extracting(Document::getId).containsExactlyElementsOf(oldIds);
        when(embedding.embed(any(Document.class))).thenReturn(new float[]{1, 0, 0, 0, 0, 0});
        assertThat(service.index(false).updated()).isTrue();
        var results = service.search("query", 6, 0, null, KnowledgeTopic.BATTERY);
        assertThat(results).hasSize(1).extracting(Document::getId).doesNotContainAnyElementsOf(oldIds);
        assertThat(service.index(false).updated()).isFalse();
        assertThat(service.index(true).updated()).isTrue();
    }

    @Test
    void failedInitialIngestionIsRetriedOnNextSearch() {
        var service = service(new KnowledgeCatalog());
        when(embedding.embed(any(Document.class))).thenThrow(new IllegalStateException("Unavailable"));
        assertThatThrownBy(() -> service.search("query", 3, 0, null, null)).isInstanceOf(IllegalStateException.class);
        verify(embedding, never()).embed(anyString());
        doReturn(new float[]{1, 0, 0, 0, 0, 0}).when(embedding).embed(any(Document.class));
        assertThat(service.search("query", 3, 0, null, null)).hasSize(3);
    }

    @Test
    void concurrentFirstSearchesIngestOnlyOnce() throws Exception {
        var service = service(new KnowledgeCatalog());
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var start = new CountDownLatch(1);
            var tasks = java.util.stream.IntStream.range(0, 8).mapToObj(i -> executor.submit(() -> {
                start.await();
                return service.search("query", 1, 0, null, null);
            })).toList();
            start.countDown();
            for (var task : tasks) assertThat(task.get(10, TimeUnit.SECONDS)).hasSize(1);
        }
        verify(embedding, times(service.chunks().size())).embed(any(Document.class));
    }

    @Test
    void searchUsesPreviousSnapshotWhileRebuildIsInProgress() throws Exception {
        var service = service(new KnowledgeCatalog());
        service.index(false);
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        when(embedding.embed(any(Document.class))).thenAnswer(invocation -> {
            started.countDown();
            if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Test timeout");
            return new float[]{0, 1, 0, 0, 0, 0};
        });
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var rebuild = executor.submit(() -> service.index(true));
            try {
                assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
                var search = executor.submit(() -> service.search("query", 1, .7, null, null));
                assertThat(search.get(2, TimeUnit.SECONDS)).hasSize(1);
            }
            finally { release.countDown(); }
            assertThat(rebuild.get(5, TimeUnit.SECONDS).updated()).isTrue();
        }
        assertThat(service.search("query", 1, .7, null, null)).isEmpty();
    }
}
