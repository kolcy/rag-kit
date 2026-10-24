package com.example.ragkit.service;

import com.example.ragkit.entity.KnowledgeChunk;
import com.example.ragkit.utils.TextNormalizer;
import com.example.ragkit.utils.TextSplitter;
import com.example.ragkit.utils.VectorJson;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class KnowledgeIngestService {

    private final TextSplitter splitter = new TextSplitter();
    private final EmbeddingClient embeddingClient;
    private final KnowledgeChunkStore store;
    private final ObjectMapper objectMapper;

    public KnowledgeIngestService(EmbeddingClient embeddingClient, KnowledgeChunkStore store, ObjectMapper objectMapper) {
        this.embeddingClient = embeddingClient;
        this.store = store;
        this.objectMapper = objectMapper;
    }

    public int ingest(String documentId, String documentTitle,String fullText){
        //先处理文档再切分文档为多段
        String normalized = TextNormalizer.normalize(fullText);
        List<String> chunks = splitter.split(normalized);
        if(chunks.isEmpty()){
            return 0;
        }

        //将多段向量化
        List<List<Double>> vectors = embeddingClient.embed(chunks);
        if (vectors.size() != chunks.size()) {
            throw new IllegalStateException("向量数与片段数不一致：chunks=" + chunks.size() + ", vectors=" + vectors.size());
        }
        List<KnowledgeChunk> entities = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            KnowledgeChunk chunk = new KnowledgeChunk();
            chunk.setDocumentId(documentId);
            chunk.setDocumentTitle(documentTitle);
            chunk.setChunkIndex(i);
            chunk.setContent(chunks.get(i));
            chunk.setEmbedding(VectorJson.toJson(objectMapper,vectors.get(i)));
            entities.add(chunk);
        }
        store.replaceChunks(documentId, entities);
        return entities.size();
    }
}
