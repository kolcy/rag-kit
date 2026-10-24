package com.example.ragkit.service;

import com.example.ragkit.entity.KnowledgeChunk;
import com.example.ragkit.repository.KnowledgeChunkRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class KnowledgeChunkStore {

    private final KnowledgeChunkRepository repository;

    public KnowledgeChunkStore(KnowledgeChunkRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void replaceChunks(String documentId, List<KnowledgeChunk> entities) {
        repository.deleteByDocumentId(documentId);
        repository.saveAll(entities);
    }
}
