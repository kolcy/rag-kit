package com.example.ragkit.utils;

/**
 * 文档入库前的文本规范化（切分的前置步骤）。
 *
 * <p>三条规则：
 * <ol>
 *   <li>统一换行符 CRLF/CR -&gt; LF，全角空格转半角，行内连续空白压成一个，去掉每行首尾空白；</li>
 *   <li>行尾是句末标点（。！？； 及半角对应符号）时，认为该行语义已结束，保留换行；
 *       否则认为是排版硬折行，去掉换行，与下一行拼回同一个自然段；</li>
 *   <li>连续空行压缩成一个，整篇首尾空白去掉。</li>
 * </ol>
 *
 * <p>规则 2 是必需的：真实语料结构不统一，有的文档每行固定宽度硬折行（如从 PDF 复制），
 * 有的文档本来就是自然段落。一刀切地保留或删除换行都会损坏其中一类。
 */
public class TextNormalizer {

    private static final String SENTENCE_END = "。！？；!?;";

    private TextNormalizer() {
    }

    public static String normalize(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        String unified = text.replace("\r\n", "\n")
                .replace('\r', '\n')
                .replace('　', ' ')
                .replaceAll("[ \t]+", " ");

        StringBuilder sb = new StringBuilder();
        for (String rawLine : unified.split("\n", -1)) {
            String line = rawLine.strip();
            if (line.isEmpty()) {
                sb.append('\n');
                continue;
            }
            sb.append(line);
            if (SENTENCE_END.indexOf(line.charAt(line.length() - 1)) >= 0) {
                sb.append('\n');
            }
        }

        return sb.toString().replaceAll("\n{2,}", "\n\n").strip();
    }
}
