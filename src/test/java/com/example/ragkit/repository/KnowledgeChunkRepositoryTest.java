package com.example.ragkit.repository;

import com.example.ragkit.entity.KnowledgeChunk;
import com.example.ragkit.utils.HashUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.annotation.Rollback;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase
@Rollback
public class KnowledgeChunkRepositoryTest {
    @Autowired
    private KnowledgeChunkRepository repository;

    private KnowledgeChunk sampleChunk;

    /** 64 位十六进制，和真实入库的 hash 同构；用常量避免两处写岔。 */
    private static final String DOC_HASH = HashUtil.sha256("学生手册正文");

    private KnowledgeChunk makeChunk(String docKey, int chunkIndex, String content) {
        KnowledgeChunk c = new KnowledgeChunk();
        c.setDocKey(docKey);
        c.setDocHash(DOC_HASH);
        c.setChunkIndex(chunkIndex);
        c.setContent(content);
        c.setEmbedding("[0.1,0.2,0.3,0.4]");
        return c;                          // createdAt 不设，交给 @PrePersist
    }

    @BeforeEach
    void setUp() {
        sampleChunk = makeChunk("xshsc-001", 0, "一等奖学金 5000 元，二等 3000 元，三等 1000 元。");
    }

    @Test
    @DisplayName("保存并按主键查询：字段全部回填正确")
    void saveAndFindById() {
        KnowledgeChunk saved = repository.save(sampleChunk);
        assertNotNull(saved.getId(), "save 之后自增主键应该有值");
        Optional<KnowledgeChunk> found = repository.findById(saved.getId());
        assertTrue(found.isPresent(), "存进去的数据应该能按主键查到");
        KnowledgeChunk c = found.get();
        assertEquals("xshsc-001", c.getDocKey());
        assertEquals(DOC_HASH,   c.getDocHash());
        assertEquals(0,            c.getChunkIndex());
        assertEquals("一等奖学金 5000 元，二等 3000 元，三等 1000 元。", c.getContent());
        assertEquals("[0.1,0.2,0.3,0.4]", c.getEmbedding());
        assertNotNull(c.getCreatedAt(), "createdAt 应由 @PrePersist 自动填充");
    }

    @Test
    @DisplayName("按 docKey 查询：返回该文档全部 chunk 且按 chunkIndex 升序")
    void findBydocKeyReturnsOrderedChunks() {
        // ===== Given：同一个文档 3 条，chunkIndex 故意乱序 1、0、2 =====
        repository.save(makeChunk("xshsc-001", 1, "二等奖学金 3000 元。"));
        repository.save(makeChunk("xshsc-001", 0, "一等奖学金 5000 元。"));
        repository.save(makeChunk("xshsc-001", 2, "三等奖学金 1000 元。"));
        repository.save(makeChunk("doc-other", 0, "我是干扰项，别混进来。"));

        // ===== When =====
        List<KnowledgeChunk> result = repository.findByDocKeyOrderByChunkIndexAsc("xshsc-001");

        // ===== Then 1：只要这个文档的 3 条 =====
        assertEquals(3, result.size(), "只应返回该文档自己的 chunk");

        // ===== Then 2：结果必须是 0、1、2 —— 证明排序是 SQL 层做的 =====
        assertEquals(0, result.get(0).getChunkIndex());
        assertEquals(1, result.get(1).getChunkIndex());
        assertEquals(2, result.get(2).getChunkIndex());

        // ===== Then 3：干扰项确实不在结果里 =====
        assertTrue(result.stream().noneMatch(x -> "doc-other".equals(x.getDocKey())),
                "其他文档的数据不应出现");
    }

    @Test
    @DisplayName("按不存在的 docKey 查询：返回空集合，不抛异常")
    void findBydocKeyReturnsEmptyWhenAbsent() {
        // ===== When：查一个压根不存在的 id =====
        List<KnowledgeChunk> result = repository.findByDocKeyOrderByChunkIndexAsc("不存在的文档");

        // ===== Then：不许 null，必须是空集合 =====
        assertNotNull(result, "查不到也必须返回集合，禁止返回 null");
        assertTrue(result.isEmpty(), "不存在的 docKey 应返回空集合");
    }
}
