package com.example.ragkit.service;

import java.util.List;

//批量入库的结果
public record IngestResult (
        int inserted,
        int skipped,
        int failed,
        int chunkCount,
        long time,
        List<String> failures
){
    public String summary(){
        return String.format("新增 %d 篇，跳过 %d 篇，失败 %d 篇，切分块 %d 个，耗时 %.1f 秒",
                inserted, skipped, failed, chunkCount, time / 1000.0);
    }
}
