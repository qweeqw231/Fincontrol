package com.fincontrol.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 微观控制金融学知识库（RAG 检索层）。
 *
 * <p>从 {@code classpath:knowledge/knowledge-base.json} 加载 86 条理论正文，
 * 提供基于关键词命中的轻量检索。无外部向量库依赖，适合当前知识规模。
 */
@Service
public class KnowledgeBaseService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseService.class);

    /**
     * 注入 prompt 的单条知识最大字符数。
     *
     * <p>2026-09-30 由 400 上调至 800：全库正文长度中位数为 1018 字符，
     * 400 会把每条知识截掉一半以上，导致关键数据表（如实盘 ZOH 校正的
     * Δm/Δb 与 IC-DRR 数值位于第 401-639 字符）整段丢失。
     * 当前 800 可完整覆盖第 6 章「实盘实证」全部数据表。
     */
    private static final int SNIPPET_MAX_CHARS = 800;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private List<KnowledgeDoc> docs = new ArrayList<>();

    /** 用于切分用户 query 为关键词的正则：空格、标点、数字边界 */
    private static final Pattern TOKEN_SPLIT = Pattern.compile("[\\s,，。、；;：:！!？?\"'\"'（）()\\[\\]【】<>《》—…\\-]+");

    @PostConstruct
    public void load() {
        try (InputStream in = new ClassPathResource("knowledge/knowledge-base.json").getInputStream()) {
            docs = objectMapper.readValue(in, new TypeReference<List<KnowledgeDoc>>() {});
            log.info("KnowledgeBase 加载完成：{} 条知识条目", docs.size());
        } catch (Exception e) {
            log.warn("KnowledgeBase 加载失败（RAG 降级为不注入）：{}", e.getMessage());
            docs = new ArrayList<>();
        }
    }

    /**
     * 关键词检索：对 query 分词后，在每条 doc 的 title+content 中统计命中数，返回 top-N。
     */
    public List<KnowledgeDoc> retrieve(String query, int topN) {
        if (query == null || query.isBlank() || docs.isEmpty()) {
            return List.of();
        }
        Set<String> keywords = tokenize(query);
        if (keywords.isEmpty()) {
            return List.of();
        }

        List<ScoredDoc> scored = new ArrayList<>();
        for (KnowledgeDoc doc : docs) {
            String haystack = (doc.getTitle() + " " + doc.getContent()).toLowerCase();
            int score = 0;
            for (String kw : keywords) {
                if (kw.length() < 2) continue; // 太短的关键词跳过
                if (haystack.contains(kw.toLowerCase())) {
                    // title 命中权重 3，content 命中权重 1
                    if (doc.getTitle().toLowerCase().contains(kw.toLowerCase())) score += 3;
                    else score += 1;
                }
            }
            if (score > 0) {
                scored.add(new ScoredDoc(doc, score));
            }
        }

        scored.sort(Comparator.comparingInt(ScoredDoc::score).reversed());
        return scored.stream()
                .limit(topN)
                .map(ScoredDoc::doc)
                .collect(Collectors.toList());
    }

    /**
     * 将检索结果格式化为注入 system prompt 的文本块。
     */
    public String formatContext(List<KnowledgeDoc> docs) {
        if (docs == null || docs.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (KnowledgeDoc doc : docs) {
            sb.append("【").append(doc.getSection()).append(" ").append(doc.getTitle()).append("】\n");
            String content = doc.getContent();
            if (content.length() > SNIPPET_MAX_CHARS) {
                content = content.substring(0, SNIPPET_MAX_CHARS) + "…";
            }
            sb.append(content).append("\n\n");
        }
        return sb.toString().trim();
    }

    /** 简单中文友好分词：按标点切分，保留连续中文字符和英文单词。 */
    private Set<String> tokenize(String query) {
        Set<String> tokens = new LinkedHashSet<>();
        String[] parts = TOKEN_SPLIT.split(query.trim());
        for (String part : parts) {
            if (part == null || part.isBlank()) continue;
            tokens.add(part.trim());
            // 对长中文片段再做 bigram（二字滑窗），提升短查询命中率
            if (part.length() > 3 && part.matches(".*[\\u4e00-\\u9fa5].*")) {
                for (int i = 0; i < part.length() - 1; i++) {
                    tokens.add(part.substring(i, i + 2));
                }
            }
        }
        return tokens;
    }

    /** 知识条目（与 knowledge-base.json 结构对应）。 */
    public static class KnowledgeDoc {
        private String section;
        private String title;
        private int chapter;
        private String chapterName;
        private String content;

        public String getSection() { return section; }
        public void setSection(String section) { this.section = section; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public int getChapter() { return chapter; }
        public void setChapter(int chapter) { this.chapter = chapter; }
        public String getChapterName() { return chapterName; }
        public void setChapterName(String chapterName) { this.chapterName = chapterName; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
    }

    private record ScoredDoc(KnowledgeDoc doc, int score) {}
}
