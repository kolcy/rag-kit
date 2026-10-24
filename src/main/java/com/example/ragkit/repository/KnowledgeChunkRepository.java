package com.example.ragkit.repository;
import com.example.ragkit.entity.KnowledgeChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface KnowledgeChunkRepository extends JpaRepository<KnowledgeChunk, Long> {
    List<KnowledgeChunk> findByDocumentIdOrderByChunkIndexAsc(String documentId);
    void  deleteByDocumentId(String documentId);
}
