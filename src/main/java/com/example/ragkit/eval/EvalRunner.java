package com.example.ragkit.eval;

import com.example.ragkit.service.KnowledgeRetrieveService;
import com.example.ragkit.service.ScoredChunk;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 检索质量评测。
 *
 * <p>只评检索、不评生成：对每条用例调 {@link KnowledgeRetrieveService#retrieve}，
 * 判断 Top-K 里有没有"期望的那一块"。生成质量另外评，那个慢且主观。
 *
 * <p>运行方式（两个 profile 都要带，local 提供数据库连接）：
 * <pre>mvn spring-boot:run "-Dspring-boot.run.profiles=local,eval"</pre>
 *
 * <p>数据文件默认 {@code src/test/resources/eval-set.csv}，格式见该文件表头。
 */
@Component
@Profile("eval")        // 只在显式激活 eval profile 时执行，否则 @SpringBootTest 会跟着跑
public class EvalRunner implements CommandLineRunner {

    private final KnowledgeRetrieveService retrieveService;

    /**
     * 评测集位置。默认取 classpath 下的 {@code eval-set.csv}（即 src/main/resources/eval-set.csv）。
     * 如果这里给的是一个存在的文件系统路径，就优先用它，方便临时换一份题库。
     */
    @Value("${rag.eval.file:eval-set.csv}")
    private String evalFile;

    @Value("${rag.eval.top-k:3}")
    private int topK;

    /**
     * 判定"有效召回"用的阈值。
     * <p>必须和 {@code RagQaService.SCORE_THRESHOLD} 保持一致，否则这个数会失真。
     * 两边各写一份是隐患——以后把 RagQaService 里那个常量也改成读配置，只留一处。
     */
    @Value("${rag.eval.threshold:0.55}")
    private double threshold;

    /** 逐条打印 Top-K 明细。用例多的时候输出很长，默认关 */
    @Value("${rag.eval.verbose:false}")
    private boolean verbose;

    public EvalRunner(KnowledgeRetrieveService retrieveService) {
        this.retrieveService = retrieveService;
    }

    @Override
    public void run(String... args) throws Exception {
        List<EvalCase> cases = load(evalFile);
        if (cases.isEmpty()) {
            System.out.println("评测集是空的。先在 " + evalFile + " 里按表头填几条用例再跑。");
            System.exit(0);
            return;
        }

        int posTotal = 0, posRank = 0, posValid = 0;
        int negTotal = 0, negPass = 0;
        Map<String, int[]> byCat = new LinkedHashMap<>();   // 类型 -> {正样本数, 排序命中, 有效命中}
        List<String> misses = new ArrayList<>();

        for (EvalCase c : cases) {
            List<ScoredChunk> top = retrieveService.retrieve(c.question(), topK);

            if (c.isNegative()) {
                negTotal++;
                // 负样本：Top-K 的分数全都不该达到阈值
                boolean clean = top.stream().allMatch(s -> s.score() < threshold);
                if (clean) {
                    negPass++;
                } else if (!verbose) {
                    double max = top.stream().mapToDouble(ScoredChunk::score).max().orElse(0);
                    misses.add(String.format(
                            "  %-4s %-6s %s%n        负样本却命中了，最高分 %.2f ≥ 阈值 %.2f",
                            c.id(), c.category(), c.question(), max, threshold));
                }
                if (verbose) {
                    printCase(c, top, clean, "负样本：应当全部低于阈值");
                }
                continue;
            }

            posTotal++;
            int[] cat = byCat.computeIfAbsent(c.category(), k -> new int[3]);
            cat[0]++;

            int idx = -1;
            for (int i = 0; i < top.size(); i++) {
                if (matches(top.get(i), c)) { idx = i; break; }
            }
            if (idx >= 0) { posRank++; cat[1]++; }

            boolean valid = idx >= 0 && top.get(idx).score() >= threshold;
            if (valid) {
                posValid++; cat[2]++;
            } else if (!verbose) {
                String detail = top.stream()
                        .map(s -> s.chunk().getDocKey() + "(" + String.format("%.2f", s.score()) + ")")
                        .collect(Collectors.joining(" | "));
                String reason = (idx >= 0)
                        ? "排序命中但分数 " + String.format("%.2f", top.get(idx).score())
                          + " 低于阈值 " + String.format("%.2f", threshold) + "，被过滤掉了"
                        : "Top-" + topK + " 里没有期望内容";
                misses.add(String.format(
                        "  %-4s %-6s %s%n        期望 %s / 关键词「%s」%n        %s%n        Top-%d: %s",
                        c.id(), c.category(), c.question(),
                        blank(c.expectDoc()) ? "(不限文档)" : c.expectDoc(),
                        blank(c.expectKeyword()) ? "(无)" : c.expectKeyword(),
                        reason, topK, detail));
            }

            if (verbose) {
                String why = idx < 0 ? "Top-" + topK + " 里没有期望内容"
                        : (valid ? "命中，排第 " + (idx + 1) + "，分数过阈值"
                                 : "排序命中但分数低于阈值，被过滤掉了");
                printCase(c, top, valid, why);
            }
        }

        print(posTotal, posRank, posValid, negTotal, negPass, byCat, misses);
        System.exit(0);
    }

    /* ---------------- 逐条明细 ---------------- */

    private void printCase(EvalCase c, List<ScoredChunk> top, boolean pass, String why) {
        System.out.println();
        System.out.printf("%s  [%s]  %s%n", c.id(), c.category(), c.question());
        System.out.printf("    判定：%s　%s%n", pass ? "✓ 通过" : "✗ 未通过", why);
        if (!blank(c.expectKeyword())) {
            System.out.printf("    期望：%s / 关键词「%s」%n",
                    blank(c.expectDoc()) ? "(不限文档)" : c.expectDoc(), c.expectKeyword());
        }
        for (int i = 0; i < top.size(); i++) {
            ScoredChunk s = top.get(i);
            String mark = (s.score() >= threshold) ? " " : "·";   // · 表示未过阈值
            System.out.printf("    %s %d. %.3f  %s%n         %s%n",
                    mark, i + 1, s.score(),
                    s.chunk().getDocKey(),
                    snippet(s.chunk().getContent()));
        }
    }

    /** 取片段前若干字，压成一行 */
    private static String snippet(String content) {
        if (content == null) {
            return "";
        }
        String flat = content.replaceAll("\\s+", " ").trim();
        return flat.length() <= 56 ? flat : flat.substring(0, 56) + "…";
    }

    /* ---------------- 判定 ---------------- */

    private static boolean matches(ScoredChunk scored, EvalCase c) {
        String content = scored.chunk().getContent();
        if (content == null || !content.contains(c.expectKeyword())) {
            return false;
        }
        // expectDoc 可以只写文件名的一部分，比如 "2024 关于调整国家奖助学金"
        return blank(c.expectDoc()) || scored.chunk().getDocKey().contains(c.expectDoc());
    }

    /* ---------------- 输出 ---------------- */

    private void print(int posTotal, int posRank, int posValid,
                       int negTotal, int negPass,
                       Map<String, int[]> byCat, List<String> misses) {

        System.out.println();
        System.out.println("=== 检索评测 ===");
        System.out.printf("用例 %d 条（正样本 %d / 负样本 %d）  Top-K=%d  阈值=%.2f%n%n",
                posTotal + negTotal, posTotal, negTotal, topK, threshold);

        if (posTotal > 0) {
            System.out.printf("Recall@%d    %d/%d  %.1f%%     只看排序，不看阈值%n",
                    topK, posRank, posTotal, pct(posRank, posTotal));
            System.out.printf("有效召回      %d/%d  %.1f%%     过阈值后的真实表现%n",
                    posValid, posTotal, pct(posValid, posTotal));
        }
        if (negTotal > 0) {
            System.out.printf("负样本拒答    %d/%d  %.1f%%%n", negPass, negTotal, pct(negPass, negTotal));
        }

        if (!byCat.isEmpty()) {
            System.out.println();
            System.out.println("按类型：");
            byCat.forEach((cat, v) -> System.out.printf("  %-8s %2d/%-2d  %5.1f%%   有效 %2d/%-2d%n",
                    cat, v[1], v[0], pct(v[1], v[0]), v[2], v[0]));
        }

        if (!misses.isEmpty()) {
            System.out.println();
            System.out.println("未命中明细：");
            misses.forEach(System.out::println);
        } else {
            System.out.println();
            System.out.println("全部命中。恭喜——但先确认语料和题目是不是太简单了。");
        }
        System.out.println();
    }

    private static double pct(int a, int b) {
        return b == 0 ? 0 : a * 100.0 / b;
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    /* ---------------- 读 CSV ---------------- */

    private static List<EvalCase> load(String location) throws IOException {
        List<String> lines = readLines(location);
        if (lines.isEmpty()) {
            return List.of();
        }
        List<EvalCase> out = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {       // 跳过表头
            String line = lines.get(i);
            if (line == null || line.isBlank() || line.stripLeading().startsWith("#")) {
                continue;
            }
            List<String> f = splitCsv(line);
            if (f.size() < 3) {
                continue;
            }
            EvalCase c = new EvalCase(at(f, 0), at(f, 1), at(f, 2), at(f, 3), at(f, 4));
            if (c.isUsable()) {
                out.add(c);
            }
        }
        return out;
    }

    /** 先当文件系统路径找，找不到再从 classpath 找 */
    private static List<String> readLines(String location) throws IOException {
        Path p = Path.of(location);
        if (Files.exists(p)) {
            return Files.readAllLines(p, StandardCharsets.UTF_8);
        }
        ClassPathResource res = new ClassPathResource(location);
        if (!res.exists()) {
            System.out.println("找不到评测文件：" + location
                    + "（既不是存在的文件，也不在 classpath 上）");
            return List.of();
        }
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(res.getInputStream(), StandardCharsets.UTF_8))) {
            List<String> lines = new ArrayList<>();
            String line;
            while ((line = r.readLine()) != null) {
                if (!lines.isEmpty() || !line.isEmpty()) {   // 丢掉文件开头的空行
                    lines.add(line.startsWith("\uFEFF") ? line.substring(1) : line);  // 去 BOM
                }
            }
            return lines;
        }
    }

    private static String at(List<String> f, int i) {
        return i < f.size() ? f.get(i).trim() : "";
    }

    /** 支持双引号包字段：问题里有英文逗号时，整段用引号括起来 */
    private static List<String> splitCsv(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                quoted = !quoted;
                continue;
            }
            if (ch == ',' && !quoted) {
                out.add(sb.toString());
                sb.setLength(0);
                continue;
            }
            sb.append(ch);
        }
        out.add(sb.toString());
        return out;
    }
}
