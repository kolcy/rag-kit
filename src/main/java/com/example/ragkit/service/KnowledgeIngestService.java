package com.example.ragkit.service;

import com.example.ragkit.entity.KnowledgeChunk;
import com.example.ragkit.utils.HashUtil;
import com.example.ragkit.utils.TextNormalizer;
import com.example.ragkit.utils.TextSplitter;
import com.example.ragkit.utils.VectorJson;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class KnowledgeIngestService {

    private final TextSplitter splitter = new TextSplitter();
    private final EmbeddingClient embeddingClient;
    private final KnowledgeChunkStore store;
    private final ObjectMapper objectMapper;
    private final DocumentLoader documentLoader;

    public record IngestOutcome(boolean skipped, int chunks) {}

    public IngestOutcome ingest(String docKey, String text, boolean force) {
        String hash = HashUtil.sha256(text);

        if (!force) {
            if (store.existsByDocHash(hash)) {
                return new IngestOutcome(true, 0);
            }
        } else {
            // 强制覆盖：先按内容把旧记录清掉（包括"同一份内容但文件名改了"的历史残留），
            // 再插入。独立事务，删除先提交，不会和 insert 抢顺序。
            store.deleteByDocHash(hash);
        }

        return new IngestOutcome(false, ingestOne(new DocumentSource(docKey, text, hash)));
    }

    public KnowledgeIngestService(EmbeddingClient embeddingClient, KnowledgeChunkStore store, ObjectMapper objectMapper, DocumentLoader documentLoader) {
        this.embeddingClient = embeddingClient;
        this.store = store;
        this.objectMapper = objectMapper;
        this.documentLoader = documentLoader;
    }

    public IngestResult ingestDirectory(Path root) throws IOException {
        long start = System.currentTimeMillis();

        List<DocumentSource> docs = documentLoader.loadAll(root);
        Set<String> existing = store.allDocHashes();

        int inserted = 0, skipped = 0, chunkCount = 0;
        List<String> failures = new ArrayList<>();

        for (DocumentSource doc : docs) {
            if (existing.contains(doc.docHash())) {
                skipped++;
                continue;
            }
            try {
                chunkCount += ingestOne(doc);
                inserted++;
                existing.add(doc.docHash());
            } catch (Exception e) {
                // 单篇失败不中断整批
                failures.add(doc.docKey() + " -> " + e.getMessage());
            }
        }

        return new IngestResult(inserted, skipped, failures.size(), chunkCount,
                System.currentTimeMillis() - start, failures);
    }

    public int ingest(String docKey, String fullText) {
        return ingestOne(new DocumentSource(docKey, fullText, HashUtil.sha256(fullText)));
    }

    private int ingestOne(DocumentSource doc){
        String normalized = TextNormalizer.normalize(doc.content());
        List<String> chunks = splitter.split(normalized);
        if(chunks.isEmpty()){
            return 0;
        }

        List<KnowledgeChunk> entities = new ArrayList<>();
        final int BATCH = 16;//一次发送16块，避免请求超时

        for (int i = 0; i < chunks.size(); i+=BATCH) {
        List<String> slice = chunks.subList(i, Math.min(i+BATCH, chunks.size()));
        List<List<Double>> vectors = embeddingClient.embed(slice);
        if (vectors == null || vectors.size() != slice.size()) {
            throw new IllegalStateException(
                    "向量数与片段数不一致：chunks=" + slice.size() + ", vectors="
                            + (vectors == null ? "null" : vectors.size()));
        }
            for (int j = 0; j < slice.size(); j++) {
                KnowledgeChunk c = new KnowledgeChunk();
                c.setDocKey(doc.docKey());
                c.setDocHash(doc.docHash());
                c.setChunkIndex(i+j);
                c.setContent(slice.get(j));
                c.setEmbedding(VectorJson.toJson(objectMapper,vectors.get(j)));
                entities.add(c);
            }
        }
        store.replaceChunks(doc.docKey(), entities);
        return entities.size();
    }
}
