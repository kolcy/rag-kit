package com.example.ragkit.repository;
import com.example.ragkit.entity.KnowledgeChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface KnowledgeChunkRepository extends JpaRepository<KnowledgeChunk, Long> {

    List<KnowledgeChunk> findByDocKeyOrderByChunkIndexAsc(String docKey);

    void  deleteByDocKey(String docKey);

    /*一次性取出已入库的所有哈希，避免每个文档查一次库*/
    @Query("select distinct c.docHash from KnowledgeChunk c")
    List<String> findDistinctDocHash();

    boolean existsByDocHash(String docHash);

    /* 按内容哈希删除：强制覆盖时用来清掉"同一份内容、改了文件名"的历史残留 */
    void deleteByDocHash(String docHash);
}
