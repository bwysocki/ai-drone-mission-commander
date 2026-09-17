package pl.stalostech.ai_drone_mission_commander.rag;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.TreeMap;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentTransformer;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!simulator")
public class KnowledgePipeline implements DocumentTransformer {
    // Small chunks make the ETL visible with this deliberately short teaching corpus.
    private final TokenTextSplitter splitter = TokenTextSplitter.builder()
            .withChunkSize(80).withMinChunkSizeChars(0).withMinChunkLengthToEmbed(0)
            .withKeepSeparator(true).build();

    @Override
    public List<Document> apply(List<Document> documents) {
        var result = new ArrayList<Document>();
        for (var document : documents) {
            String normalized = document.getText().replace("\r\n", "\n").replace('\r', '\n').strip();
            if (normalized.isBlank()) throw new IllegalArgumentException("Cannot ingest a blank document");
            var parts = splitter.apply(List.of(new Document(document.getId(), normalized, document.getMetadata())));
            for (int i = 0; i < parts.size(); i++) {
                var part = parts.get(i);
                var metadata = new HashMap<>(document.getMetadata());
                metadata.put("documentId", document.getId());
                metadata.put("chunkIndex", i);
                metadata.put("chunkCount", parts.size());
                String id = document.getId() + "#" + i + "-" + hash(part.getText());
                result.add(new Document(id, part.getText(), metadata));
            }
        }
        if (result.isEmpty()) throw new IllegalArgumentException("Cannot ingest an empty corpus");
        return List.copyOf(result);
    }

    /** Length-delimited text and sorted metadata make the fingerprint deterministic. */
    public String fingerprint(List<Document> chunks) {
        var serialized = new StringBuilder();
        for (var chunk : chunks) {
            append(serialized, chunk.getId());
            append(serialized, chunk.getText());
            new TreeMap<>(chunk.getMetadata()).forEach((key, value) -> {
                append(serialized, key);
                append(serialized, value.toString());
            });
        }
        return hash(serialized.toString());
    }

    private static void append(StringBuilder target, String value) {
        target.append(value.length()).append(':').append(value);
    }

    private static String hash(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }
}
