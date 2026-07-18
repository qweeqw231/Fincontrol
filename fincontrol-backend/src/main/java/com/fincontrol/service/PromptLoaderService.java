package com.fincontrol.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fincontrol.common.BusinessException;
import com.fincontrol.common.ErrorCode;
import com.fincontrol.entity.ChatHistory;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 从 prompt_versions 表加载 prompt（带内存缓存）。
 *
 * <p>Phase 1 启动时预热 screenshot_parser / ai_assistant / intent_classifier 三个版本到缓存；
 * 后续调用 {@link #get(String)} 直接命中内存。
 */
@Service
public class PromptLoaderService {

    private static final Logger log = LoggerFactory.getLogger(PromptLoaderService.class);

    private final JdbcTemplate jdbc;
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public PromptLoaderService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostConstruct
    public void warmUp() {
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT prompt_name, prompt_content FROM prompt_versions ORDER BY prompt_name, id DESC");
            cache.clear();
            for (Map<String, Object> row : rows) {
                String name = (String) row.get("prompt_name");
                String content = (String) row.get("prompt_content");
                // 结果按同名 prompt 的 id DESC 排序；第一条才是最新版本，旧版本不得反向覆盖。
                cache.putIfAbsent(name, content);
            }
            log.info("PromptLoader 预热完成：加载 {} 个 prompt（screenshot_parser / ai_assistant / intent_classifier 等）", cache.size());
        } catch (Exception e) {
            log.warn("PromptLoader 预热失败（运行时不阻塞，使用 DB 即时查询兜底）: {}", e.getMessage());
        }
    }

    public String get(String promptName) {
        String cached = cache.get(promptName);
        if (cached != null) return cached;
        try {
            List<String> rows = jdbc.queryForList(
                    "SELECT prompt_content FROM prompt_versions WHERE prompt_name = ? ORDER BY id DESC LIMIT 1",
                    String.class, promptName);
            if (rows.isEmpty()) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "prompt_versions 表无此 prompt: " + promptName);
            }
            String content = rows.get(0);
            cache.put(promptName, content);
            return content;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.DATABASE_ERROR, "读取 prompt 失败: " + e.getMessage());
        }
    }

    /** 仅供测试使用 */
    public void putForTest(String name, String content) {
        cache.put(name, content);
    }
}
