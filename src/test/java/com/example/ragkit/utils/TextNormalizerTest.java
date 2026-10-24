package com.example.ragkit.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TextNormalizerTest {

    @Test
    @DisplayName("CRLF 统一为 LF，结果里不留 \\r")
    void normalize_crlf() {
        String result = TextNormalizer.normalize("第一行。\r\n第二行。\r\n");

        assertFalse(result.contains("\r"), "CRLF 必须被清掉");
        assertEquals("第一行。\n第二行。", result);
    }

    @Test
    @DisplayName("硬折行：行尾没有句末标点，与下一行拼回同一段")
    void normalize_hardWrap() {
        String result = TextNormalizer.normalize("为进一步优化课程体系，提高教学质\n量，培养学生能力。");

        assertEquals("为进一步优化课程体系，提高教学质量，培养学生能力。", result);
        assertFalse(result.contains("\n"), "硬折行应被合并，不该留下换行");
    }

    @Test
    @DisplayName("自然段落：行尾是句号，换行要保留")
    void normalize_keepParagraph() {
        String result = TextNormalizer.normalize("第一条 学生应当遵守校规。\n第二条 学生应当按时上课。");

        assertEquals("第一条 学生应当遵守校规。\n第二条 学生应当按时上课。", result);
    }

    @Test
    @DisplayName("连续空行压缩成一个，首尾空白去掉")
    void normalize_blankLines() {
        String result = TextNormalizer.normalize("\n\n标题\n\n\n\n正文。\n\n\n");

        assertEquals("标题\n\n正文。", result);
    }

    @Test
    @DisplayName("全角空格与行内连续空格都变成一个半角空格")
    void normalize_spaces() {
        assertEquals("学生 手册", TextNormalizer.normalize("学生　手册"));
        assertEquals("学生 手册", TextNormalizer.normalize("学生   手册"));
    }

    @Test
    @DisplayName("null 与空白串都返回空串")
    void normalize_empty() {
        assertEquals("", TextNormalizer.normalize(null));
        assertEquals("", TextNormalizer.normalize(""));
        assertEquals("", TextNormalizer.normalize("   \n\n  "));
    }
}
