package com.example.ragkit.controller;

import com.example.ragkit.service.KnowledgeIngestService;
import com.example.ragkit.service.RagQaService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 最小可用的 HTTP 入口，方便 clone 下来直接 curl 验证。
 */
@RestController
@RequestMapping("/api")
public class RagController {

    private final RagQaService qaService;
    private final KnowledgeIngestService ingestService;

    public RagController(RagQaService qaService, KnowledgeIngestService ingestService) {
        this.qaService = qaService;
        this.ingestService = ingestService;
    }

    /**
     * 提问。
     *
     * <pre>curl -X POST http://localhost:8085/api/ask \
     *      -H "Content-Type: application/json" \
     *      -d '{"question":"国家奖学金多少钱？"}'</pre>
     */
    @PostMapping("/ask")
    public Map<String, Object> ask(@RequestBody Map<String, String> body) {
        RagQaService.AskResult result = qaService.ask(body.get("question"));
        return Map.of(
                "answer", result.answer(),
                "hit", result.hit(),
                "degraded", result.degraded()
        );
    }

    /**
     * 灌入一篇文档：规范化 → 切块 → 批量向量化 → 事务化入库。
     *
     * <pre>curl -X POST http://localhost:8085/api/ingest \
     *      -H "Content-Type: application/json" \
     *      -d '{"docKey":"doc-001","text":"正文全文"}'</pre>
     *
     * @return 实际入库的片段数
     */
    @PostMapping("/ingest")
    public Map<String, Object> ingest(@RequestBody Map<String, String> body) {
        boolean force = "true".equals(body.get("force"));
        KnowledgeIngestService.IngestOutcome r = ingestService.ingest(
                body.get("docKey"), body.get("text"), force);
        return Map.of("skipped", r.skipped(), "chunks", r.chunks());
    }
}
