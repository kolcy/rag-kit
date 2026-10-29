package com.example.ragkit.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

@Component
@Profile("ingest")      // 只在显式激活 ingest profile 时执行
public class IngestRunner implements CommandLineRunner {

    private final KnowledgeIngestService ingestService;

    @Value("${rag.ingest.dir:./docs}")
    private String dir;

    public IngestRunner(KnowledgeIngestService ingestService) {
        this.ingestService = ingestService;
    }

    @Override
    public void run(String... args) throws Exception {
        System.out.println("==== 开始入库：" + dir + " ====");
        IngestResult r = ingestService.ingestDirectory(Path.of(dir));
        System.out.println(r.summary());
        if (!r.failures().isEmpty()) {
            System.out.println("---- 失败明细 ----");
            r.failures().forEach(f -> System.out.println("  " + f));
        }
        System.exit(0);     // 批量任务，跑完就退，不常驻
    }
}