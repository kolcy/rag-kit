package com.example.ragkit.service;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;

import static com.example.ragkit.utils.HashUtil.sha256;

@Component
public class DocumentLoader {

    public List<DocumentSource> loadAll(Path root) throws IOException {
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("不是目录：" + root);
        }
        List<DocumentSource> result = new ArrayList<>();
        try(Stream<Path> stream = Files.walk(root)){
            List<Path> files = stream
                    .filter(Files::isRegularFile)
                    .filter(p ->{
                        String n = p.getFileName().toString().toLowerCase();
                        return n.endsWith(".txt") || n.endsWith(".md");
                    })
                    .sorted()
                    .toList();
            for (Path p : files) {
                String text = readText(p);
                if(text.isBlank()) {
                    continue;//空文件直接略过
                }
                String key = root.relativize(p).toString().replace('\\','/');
                result.add(new DocumentSource(key,text,sha256(text)));
            }
        }
        return result;
    }

    /** 政府站点下载的 txt 常常是 GBK，UTF-8 解析失败就回退。 */
    private static String readText(Path p) throws IOException {
        byte[] bytes = Files.readAllBytes(p);
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException e) {
            return new String(bytes, Charset.forName("GBK"));
        }
    }

}
