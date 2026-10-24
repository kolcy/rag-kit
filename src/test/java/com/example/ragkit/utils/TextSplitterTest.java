package com.example.ragkit.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TextSplitterTest {
    @Test
    @DisplayName("空串：返回空列表")
    void split_emptyText() {
        TextSplitter splitter = new TextSplitter();
        List<String> chunks = splitter.split("");
        assertTrue(chunks.isEmpty());
    }

    @Test
    @DisplayName("正常长文本：切成多段，首尾与原文对齐")
    void testSplit() {
        String unit = "第一章 学生应当遵守学校规章制度并按时参加教育教学活动。";  // 25 字
        String text = unit.repeat(80);           // 2000 字
        TextSplitter splitter = new TextSplitter();

        // when
        List<String> chunks = splitter.split(text);

        // then
        assertTrue(chunks.size() > 1, "2000 字应该切成多段");
        assertEquals(400, chunks.get(0).length(), "第一段应满 400 字");
        assertTrue(text.startsWith(chunks.get(0)), "第一段必须是原文开头");
        assertTrue(text.endsWith(chunks.get(chunks.size() - 1)), "最后一段必须是原文结尾");
    }

    @Test
    @DisplayName("非法参数：构造器抛异常")
    void constructor_invalidArgs_throws() {
        assertThrows(IllegalArgumentException.class, () -> new TextSplitter(50, 100));
    }
}