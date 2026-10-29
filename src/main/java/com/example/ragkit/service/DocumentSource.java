package com.example.ragkit.service;

/**
 * 待入库的原始文档
 * @param docKey 用来判断是新增还是覆盖旧文档
 * @param content
 * @param docHash 用于判断内容是否为新
 */
public record DocumentSource(String docKey,String content,String docHash) {
}
