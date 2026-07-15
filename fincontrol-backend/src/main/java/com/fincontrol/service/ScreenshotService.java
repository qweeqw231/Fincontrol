package com.fincontrol.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fincontrol.common.BusinessException;
import com.fincontrol.common.ErrorCode;
import com.fincontrol.dto.screenshot.*;
import com.fincontrol.entity.ChatHistory;
import com.fincontrol.mapper.ChatHistoryMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 截图 / 解析 / 重新解析 业务编排（Phase 1a.2）。
 *
 * <ul>
 *   <li>upload —— 文件存到磁盘 + 返回 fileId + URL。</li>
 *   <li>parse  —— 调用 DeepSeek → 抽 JSON → 写 chat_history（user + assistant）。失败时也写 assistant 错误记录。</li>
 *   <li>reparse —— 从历史 user 消息找回 fileId → 重新解析（[P0-4.4](#)）。</li>
 * </ul>
 *
 * 错误码映射：[api-contract.md §11](#)。
 */
@Service
public class ScreenshotService {

    private static final Logger log = LoggerFactory.getLogger(ScreenshotService.class);

    private final FileStorageService storage;
    private final DeepSeekClient deepSeekClient;
    private final PromptLoaderService promptLoader;
    private final ChatHistoryMapper chatHistoryMapper;

    public ScreenshotService(FileStorageService storage,
                             DeepSeekClient deepSeekClient,
                             PromptLoaderService promptLoader,
                             ChatHistoryMapper chatHistoryMapper) {
        this.storage = storage;
        this.deepSeekClient = deepSeekClient;
        this.promptLoader = promptLoader;
        this.chatHistoryMapper = chatHistoryMapper;
    }

    // ===================================================================
    // 1a.4 POST /api/screenshot/upload
    // ===================================================================

    @Transactional
    public ScreenshotUploadResponse upload(MultipartFile file) {
        try {
            FileStorageService.StoredFile stored = storage.store(file);
            return new ScreenshotUploadResponse(
                    stored.fileId(),
                    stored.fileUrl(),
                    new Date().toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()
            );
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "文件保存失败: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_SNAPSHOT_DATE, e.getMessage());
        }
    }

    // ===================================================================
    // 1a.5 POST /api/screenshot/parse
    // ===================================================================

    /**
     * 主入口。返回 ParsedAsset（成功）或抛 BusinessException（3001/3002/3003）。
     *
     * <p>写 chat_history 的两条记录（user + assistant）；失败时仅写 assistant 错误记录。
     */
    @Transactional
    public ParsedAsset parse(ScreenshotParseRequest req) {
        if (req.getFileId() == null || req.getFileId().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_SNAPSHOT_DATE, "fileId 必填");
        }
        Path imagePath = storage.resolveByFileId(req.getFileId());
        if (imagePath == null) {
            throw new BusinessException(ErrorCode.INVALID_SNAPSHOT_DATE, "fileId 不存在或已过期: " + req.getFileId());
        }

        String conversationId = "conv-" + req.getFileId();
        // 1) 写 user 消息
        ChatHistory userMsg = new ChatHistory();
        userMsg.setUserId(req.getUserId());
        userMsg.setConversationId(conversationId);
        userMsg.setRole(ChatHistory.ROLE_USER);
        userMsg.setContent(req.getFileId());
        userMsg.setConversationType(ChatHistory.CONVERSATION_TYPE_SCREENSHOT_PARSE);
        chatHistoryMapper.insert(userMsg);

        // 2) 调 DeepSeek
        String systemPrompt = promptLoader.get("screenshot_parser");
        String raw;
        try {
            raw = deepSeekClient.callRaw(imagePath.toFile(), systemPrompt,
                    "请解析以下支付宝资产截图");
        } catch (BusinessException e) {
            // 失败时 assistant 也写一行（[P0-1.4](#) 失败记录），便于 parse-logs 展示
            persistAssistantError(conversationId, req.getUserId(), e);
            throw e;
        }

        // 3) 抽 JSON
        JsonNode json;
        try {
            json = deepSeekClient.extractFirstJsonObject(raw);
        } catch (BusinessException e) {
            persistAssistantError(conversationId, req.getUserId(), e, raw);
            throw e;
        }

        ParsedAsset asset = mapToParsedAsset(conversationId, raw, json);

        // 4) 0 基金检查
        boolean zeroFunds = asset.getCategories() == null
                || asset.getCategories().isEmpty()
                || asset.getCategories().stream().allMatch(c -> c.getFunds() == null || c.getFunds().isEmpty());
        if (zeroFunds) {
            BusinessException e = new BusinessException(ErrorCode.DEEPSEEK_ZERO_FUNDS,
                    "DeepSeek 返回 0 只基金（conversationId=" + conversationId + "）",
                    new DeepSeekErrorData(conversationId, "zero_funds", raw));
            persistAssistantError(conversationId, req.getUserId(), e);
            throw e;
        }

        // 5) 成功：写 assistant JSON
        ChatHistory assistantMsg = new ChatHistory();
        assistantMsg.setUserId(req.getUserId());
        assistantMsg.setConversationId(conversationId);
        assistantMsg.setRole(ChatHistory.ROLE_ASSISTANT);
        assistantMsg.setContent(raw);
        assistantMsg.setConversationType(ChatHistory.CONVERSATION_TYPE_SCREENSHOT_PARSE);
        chatHistoryMapper.insert(assistantMsg);

        return asset;
    }

    // ===================================================================
    // 1a.6 POST /api/screenshot/reparse（[P0-4.4](#)）
    // ===================================================================

    @Transactional
    public ParsedAsset reparse(ScreenshotReparseRequest req) {
        if (req.getConversationId() == null || req.getConversationId().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_SNAPSHOT_DATE, "conversationId 必填");
        }
        ChatHistory userMsg = chatHistoryMapper.selectByConversationIdOrderByCreatedAt(req.getConversationId())
                .stream()
                .filter(m -> ChatHistory.ROLE_USER.equals(m.getRole()))
                .findFirst()
                .orElse(null);
        if (userMsg == null) {
            throw new BusinessException(ErrorCode.SNAPSHOT_NOT_FOUND,
                    "未找到 conversation 对应的原始截图: " + req.getConversationId());
        }
        String fileId = userMsg.getContent();
        Path imagePath = storage.resolveByFileId(fileId);
        if (imagePath == null) {
            throw new BusinessException(ErrorCode.INVALID_SNAPSHOT_DATE, "原图已过期: fileId=" + fileId);
        }

        String systemPrompt = promptLoader.get("screenshot_parser");
        String raw;
        try {
            raw = deepSeekClient.callRaw(imagePath.toFile(), systemPrompt,
                    "请重新解析以下支付宝资产截图");
        } catch (BusinessException e) {
            persistAssistantError(req.getConversationId(), userMsg.getUserId(), e);
            throw e;
        }

        JsonNode json;
        try {
            json = deepSeekClient.extractFirstJsonObject(raw);
        } catch (BusinessException e) {
            persistAssistantError(req.getConversationId(), userMsg.getUserId(), e, raw);
            throw e;
        }

        ParsedAsset asset = mapToParsedAsset(req.getConversationId(), raw, json);

        boolean zeroFunds = asset.getCategories() == null
                || asset.getCategories().isEmpty()
                || asset.getCategories().stream().allMatch(c -> c.getFunds() == null || c.getFunds().isEmpty());
        if (zeroFunds) {
            BusinessException e = new BusinessException(ErrorCode.DEEPSEEK_ZERO_FUNDS,
                    "DeepSeek 返回 0 只基金（reparse conversationId=" + req.getConversationId() + "）",
                    new DeepSeekErrorData(req.getConversationId(), "zero_funds", raw));
            persistAssistantError(req.getConversationId(), userMsg.getUserId(), e);
            throw e;
        }

        ChatHistory assistantMsg = new ChatHistory();
        assistantMsg.setUserId(userMsg.getUserId());
        assistantMsg.setConversationId(req.getConversationId());
        assistantMsg.setRole(ChatHistory.ROLE_ASSISTANT);
        assistantMsg.setContent(raw);
        assistantMsg.setConversationType(ChatHistory.CONVERSATION_TYPE_SCREENSHOT_PARSE);
        chatHistoryMapper.insert(assistantMsg);

        return asset;
    }

    // ===================================================================
    // 内部辅助
    // ===================================================================

    private void persistAssistantError(String conversationId, Long userId, BusinessException e) {
        persistAssistantError(conversationId, userId, e, null);
    }

    private void persistAssistantError(String conversationId, Long userId, BusinessException e, String raw) {
        ChatHistory errMsg = new ChatHistory();
        errMsg.setUserId(userId);
        errMsg.setConversationId(conversationId);
        errMsg.setRole(ChatHistory.ROLE_ASSISTANT);
        String content;
        if (raw != null && !raw.isBlank()) {
            content = "[error code=" + e.getErrorCode().getCode() + "] " + e.getMessage() + "\n---raw---\n" + raw;
        } else {
            content = "[error code=" + e.getErrorCode().getCode() + "] " + e.getMessage();
        }
        errMsg.setContent(content);
        errMsg.setConversationType(ChatHistory.CONVERSATION_TYPE_SCREENSHOT_PARSE);
        try {
            chatHistoryMapper.insert(errMsg);
        } catch (Exception ex) {
            log.warn("写入失败 assistant 记录失败（不阻塞主流程）: {}", ex.getMessage());
        }
    }

    private ParsedAsset mapToParsedAsset(String conversationId, String raw, JsonNode json) {
        ParsedAsset out = new ParsedAsset();
        out.setConversationId(conversationId);
        out.setSnapshotDate(textOrNull(json, "snapshot_date"));
        out.setTotalAsset(decimalOrNull(json, "total_asset"));
        out.setSixCategoriesTotal(decimalOrNull(json, "six_categories_total"));
        out.setBalanceFund(decimalOrNull(json, "balance_fund"));
        out.setAiMarkdownReport(raw);

        JsonNode categories = json.path("categories");
        List<ParsedAsset.CategoryBlock> blocks = new ArrayList<>();
        if (categories.isArray()) {
            for (JsonNode c : categories) {
                ParsedAsset.CategoryBlock block = new ParsedAsset.CategoryBlock();
                block.setCategoryName(textOrNull(c, "category_name"));
                JsonNode funds = c.path("funds");
                List<ParsedAsset.FundLine> lines = new ArrayList<>();
                if (funds.isArray()) {
                    for (JsonNode f : funds) {
                        ParsedAsset.FundLine line = new ParsedAsset.FundLine();
                        line.setFundName(textOrNull(f, "fund_name"));
                        line.setAmount(decimalOrNull(f, "amount"));
                        line.setProfit(decimalOrNull(f, "profit"));
                        lines.add(line);
                    }
                }
                block.setFunds(lines);
                block.setCategoryTotal(decimalOrNull(c, "category_total"));
                block.setCategoryPercentage(decimalOrNull(c, "category_percentage"));
                block.setTargetRatio(decimalOrNull(c, "target_ratio"));
                block.setDeviation(decimalOrNull(c, "deviation"));
                blocks.add(block);
            }
        }
        out.setCategories(blocks);

        // matched / unmatched funds：聚合自 categories[].funds
        List<String> matched = new ArrayList<>();
        for (ParsedAsset.CategoryBlock block : blocks) {
            if (block.getFunds() != null) {
                for (ParsedAsset.FundLine fl : block.getFunds()) {
                    if (fl.getFundName() != null) matched.add(fl.getFundName());
                }
            }
        }
        out.setMatchedFunds(matched);
        out.setUnmatchedFunds(Collections.emptyList());

        // 同时把 matchedFunds 直接挂在 json 上，便于 parse-logs 派生
        return out;
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isMissingNode() || v.isNull() ? null : v.asText();
    }

    private static BigDecimal decimalOrNull(JsonNode node, String field) {
        JsonNode v = node.path(field);
        if (v.isMissingNode() || v.isNull() || !v.isNumber()) return null;
        return new BigDecimal(v.asText());
    }
}
