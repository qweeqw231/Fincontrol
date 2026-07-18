package com.fincontrol.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fincontrol.ai.AiRouter;
import com.fincontrol.ai.ApiStyle;
import com.fincontrol.common.BusinessException;
import com.fincontrol.common.ErrorCode;
import com.fincontrol.dto.screenshot.*;
import com.fincontrol.entity.ChatHistory;
import com.fincontrol.mapper.ChatHistoryMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.*;

/**
 * 截图 / 解析 / 重新解析 业务编排（Phase 1a.2，1a.8 升级）。
 *
 * <ul>
 *   <li>upload —— 文件存到磁盘 + 返回 fileId + URL</li>
 *   <li>parse / reparse —— 通过 {@link AiRouter} 调用 vision 模型（minimax primary + 豆包 fallback / cache / retry / CB）
 *       — 1a.8 起统一入口，{@code usedProvider} + {@code fallbackTriggered} 写入 chat_history 审计</li>
 *   <li>失败时写 assistant 错误记录（含 provider 字段）</li>
 * </ul>
 *
 * <p>错误码：[api-contract.md §11](#)
 */
@Service
public class ScreenshotService {

    private static final Logger log = LoggerFactory.getLogger(ScreenshotService.class);

    private final FileStorageService storage;
    private final VisionModelClient visionModelClient; // 用于 extractFirstJsonObject 抽 JSON
    private final AiRouter aiRouter;                    // 1a.8 vision 入口
    private final PromptLoaderService promptLoader;
    private final ChatHistoryMapper chatHistoryMapper;

    /** 1a.8 路由 imageCount 上下文：1 张图 parse → imageCount=1；批量 4 张 → imageCount=4。 */
    private static final int DEFAULT_IMAGE_COUNT = 1;

    public ScreenshotService(FileStorageService storage,
                             VisionModelClient visionModelClient,
                             AiRouter aiRouter,
                             PromptLoaderService promptLoader,
                             ChatHistoryMapper chatHistoryMapper) {
        this.storage = storage;
        this.visionModelClient = visionModelClient;
        this.aiRouter = aiRouter;
        this.promptLoader = promptLoader;
        this.chatHistoryMapper = chatHistoryMapper;
    }

    // 1a.4 upload
    public ScreenshotUploadResponse upload(MultipartFile file) {
        try {
            FileStorageService.StoredFile stored = storage.store(file);
            return new ScreenshotUploadResponse(
                    stored.fileId(),
                    stored.fileUrl(),
                    new Date().toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDateTime());
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "文件保存失败: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_SNAPSHOT_DATE, e.getMessage());
        }
    }

    // 1a.5 parse
    public ParsedAsset parse(ScreenshotParseRequest req) {
        if (req.getFileId() == null || req.getFileId().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_SNAPSHOT_DATE, "fileId 必填");
        }
        Path imagePath = storage.resolveByFileId(req.getFileId());
        if (imagePath == null) {
            throw new BusinessException(ErrorCode.INVALID_SNAPSHOT_DATE, "fileId 不存在或已过期: " + req.getFileId());
        }

        String conversationId = "conv-" + req.getFileId();

        // 1) user 消息
        ChatHistory userMsg = new ChatHistory();
        userMsg.setUserId(req.getUserId());
        userMsg.setConversationId(conversationId);
        userMsg.setRole(ChatHistory.ROLE_USER);
        userMsg.setContent(req.getFileId());
        userMsg.setConversationType(ChatHistory.CONVERSATION_TYPE_SCREENSHOT_PARSE);
        chatHistoryMapper.insert(userMsg);

        // 2) 通过 AiRouter 调 vision（minimax primary + 豆包 fallback / cache / retry / CB）
        String systemPrompt = promptLoader.get("screenshot_parser");
        AiRouter.VisionResult visionResult;
        try {
            visionResult = aiRouter.callVision(imagePath.toFile(), systemPrompt,
                    "请解析以下支付宝资产截图", DEFAULT_IMAGE_COUNT);
        } catch (BusinessException be) {
            persistAssistantError(conversationId, req.getUserId(), be, null);
            throw be;
        }

        String raw = visionResult.content();
        String usedProvider = visionResult.usedProvider();
        boolean fallbackTriggered = visionResult.fallbackTriggered();
        log.info("1a.8 vision 调用结束：usedProvider={} fallback={} cacheHit={}",
                usedProvider, fallbackTriggered, visionResult.cacheHit());

        // 3) 抽 JSON
        JsonNode json;
        try {
            json = visionModelClient.extractFirstJsonObject(raw);
        } catch (BusinessException e) {
            persistAssistantError(conversationId, req.getUserId(), e, raw, usedProvider, fallbackTriggered);
            throw e;
        }

        ParsedAsset asset = mapToParsedAsset(conversationId, raw, json);

        // 4) 0 基金
        boolean zeroFunds = asset.getCategories() == null
                || asset.getCategories().isEmpty()
                || asset.getCategories().stream().allMatch(c -> c.getFunds() == null || c.getFunds().isEmpty());
        if (zeroFunds) {
            BusinessException e = new BusinessException(ErrorCode.VISION_ZERO_FUNDS,
                    "视觉模型返回 0 只基金（conversationId=" + conversationId + "）",
                    new VisionErrorData(conversationId, "zero_funds", raw));
            persistAssistantError(conversationId, req.getUserId(), e, raw, usedProvider, fallbackTriggered);
            throw e;
        }

        // 5) 写 assistant 成功记录（含 1a.8 监控字段）
        ChatHistory assistantMsg = new ChatHistory();
        assistantMsg.setUserId(req.getUserId());
        assistantMsg.setConversationId(conversationId);
        assistantMsg.setRole(ChatHistory.ROLE_ASSISTANT);
        assistantMsg.setContent(raw);
        assistantMsg.setConversationType(ChatHistory.CONVERSATION_TYPE_SCREENSHOT_PARSE);
        assistantMsg.setUsedProvider(usedProvider);
        assistantMsg.setFallbackTriggered(fallbackTriggered);
        chatHistoryMapper.insert(assistantMsg);

        return asset;
    }

    // 1a.6 reparse
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
        AiRouter.VisionResult visionResult;
        try {
            visionResult = aiRouter.callVision(imagePath.toFile(), systemPrompt,
                    "请重新解析以下支付宝资产截图", DEFAULT_IMAGE_COUNT);
        } catch (BusinessException be) {
            persistAssistantError(req.getConversationId(), userMsg.getUserId(), be, null);
            throw be;
        }

        String raw = visionResult.content();
        String usedProvider = visionResult.usedProvider();
        boolean fallbackTriggered = visionResult.fallbackTriggered();

        JsonNode json;
        try {
            json = visionModelClient.extractFirstJsonObject(raw);
        } catch (BusinessException e) {
            persistAssistantError(req.getConversationId(), userMsg.getUserId(), e, raw, usedProvider, fallbackTriggered);
            throw e;
        }

        ParsedAsset asset = mapToParsedAsset(req.getConversationId(), raw, json);

        boolean zeroFunds = asset.getCategories() == null
                || asset.getCategories().isEmpty()
                || asset.getCategories().stream().allMatch(c -> c.getFunds() == null || c.getFunds().isEmpty());
        if (zeroFunds) {
            BusinessException e = new BusinessException(ErrorCode.VISION_ZERO_FUNDS,
                    "视觉模型返回 0 只基金（reparse conversationId=" + req.getConversationId() + "）",
                    new VisionErrorData(req.getConversationId(), "zero_funds", raw));
            persistAssistantError(req.getConversationId(), userMsg.getUserId(), e, raw, usedProvider, fallbackTriggered);
            throw e;
        }

        ChatHistory assistantMsg = new ChatHistory();
        assistantMsg.setUserId(userMsg.getUserId());
        assistantMsg.setConversationId(req.getConversationId());
        assistantMsg.setRole(ChatHistory.ROLE_ASSISTANT);
        assistantMsg.setContent(raw);
        assistantMsg.setConversationType(ChatHistory.CONVERSATION_TYPE_SCREENSHOT_PARSE);
        assistantMsg.setUsedProvider(usedProvider);
        assistantMsg.setFallbackTriggered(fallbackTriggered);
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
        persistAssistantError(conversationId, userId, e, raw, null, false);
    }

    /** 1a.8：失败记录也携带 provider 信息（即便失败，primary 已知）。 */
    private void persistAssistantError(String conversationId, Long userId, BusinessException e,
                                       String raw, String usedProvider, boolean fallbackTriggered) {
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
        errMsg.setUsedProvider(usedProvider);
        errMsg.setFallbackTriggered(fallbackTriggered);
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

        List<ParsedAsset.CategoryBlock> blocks = new ArrayList<>();

        // Strategy A
        JsonNode categories = json.path("categories");
        if (categories.isArray() && categories.size() > 0) {
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
        // Strategy B：minimax 默认 holdings + category_summary
        else {
            JsonNode holdings = json.path("holdings");
            if (holdings.isArray() && holdings.size() > 0) {
                Map<String, List<JsonNode>> byCategory = new LinkedHashMap<>();
                for (JsonNode h : holdings) {
                    String cat = textOrNull(h, "category");
                    if (cat == null || cat.isBlank()) cat = "其他";
                    byCategory.computeIfAbsent(cat, k -> new ArrayList<>()).add(h);
                }
                JsonNode summary = json.path("category_summary");
                for (Map.Entry<String, List<JsonNode>> entry : byCategory.entrySet()) {
                    ParsedAsset.CategoryBlock block = new ParsedAsset.CategoryBlock();
                    block.setCategoryName(entry.getKey());
                    List<ParsedAsset.FundLine> lines = new ArrayList<>();
                    for (JsonNode h : entry.getValue()) {
                        ParsedAsset.FundLine line = new ParsedAsset.FundLine();
                        line.setFundName(textOrNull(h, "name"));
                        line.setAmount(decimalOrNull(h, "amount"));
                        line.setProfit(decimalOrNull(h, "holding_pnl"));
                        lines.add(line);
                    }
                    block.setFunds(lines);
                    JsonNode s = summary.path(entry.getKey());
                    if (s.isObject()) {
                        block.setCategoryTotal(decimalOrNull(s, "total_amount"));
                        block.setCategoryPercentage(decimalOrNull(s, "total_ratio"));
                        block.setDeviation(decimalOrNull(s, "total_holding_pnl"));
                    }
                    blocks.add(block);
                }
            }
        }
        out.setCategories(blocks);

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
