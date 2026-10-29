package com.example.ragkit.service;

import com.example.ragkit.entity.KnowledgeChunk;
import com.example.ragkit.repository.KnowledgeChunkRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class KnowledgeChunkStore {

    private final KnowledgeChunkRepository repository;

    public KnowledgeChunkStore(KnowledgeChunkRepository repository) {
        this.repository = repository;
    }

    public boolean existsByDocHash(String docHash) {
        return repository.existsByDocHash(docHash);
    }

    /**
     * 按内容哈希删除。
     * <p>独立事务：调用方（强制覆盖）需要它**先提交**，再去插入新记录。
     * 如果和插入放在同一个事务里，Hibernate 的 flush 顺序是「先 insert 再 delete」，
     * 同 docKey 覆盖时会撞唯一键——和之前那个 duplicate entry 是同一个坑。
     */
    @Transactional
    public void deleteByDocHash(String docHash) {
        repository.deleteByDocHash(docHash);
    }

    @Transactional
    public void replaceChunks(String docKey, List<KnowledgeChunk> entities) {
        repository.deleteByDocKey(docKey);
        repository.flush();
        repository.saveAll(entities);
    }

    public Set<String> allDocHashes() {
        return new HashSet<>(repository.findDistinctDocHash());
    }
}
