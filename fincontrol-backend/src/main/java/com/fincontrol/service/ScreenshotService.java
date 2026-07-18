package com.fincontrol.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincontrol.ai.AiRouter;
import com.fincontrol.common.BusinessException;
import com.fincontrol.common.CategoryEnum;
import com.fincontrol.common.ErrorCode;
import com.fincontrol.dto.screenshot.*;
import com.fincontrol.entity.ChatHistory;
import com.fincontrol.mapper.ChatHistoryMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 截图 / 解析 / 重新解析 业务编排（Phase 1a.2，1a.8 升级）。
 *
 * <ul>
 *   <li>upload —— 文件存到磁盘 + 返回 fileId + URL</li>
 *   <li>parse / reparse —— 通过 {@link AiRouter} 调用 vision 模型（minimax primary + 豆包 fallback / cache / retry / CB）
 *       — 1a.8 起统一入口，{@code usedProvider} + {@code fallbackTriggered} 写入 chat_history 审计</li>
 *   <li>1a.8 v3：parse 成功后写 OCR 真实数据日志到 {@code docs/test-records/ocr-results/{date}/}（gitignored）</li>
 *   <li>失败时写 assistant 错误记录（含 provider 字段）</li>
 * </ul>
 *
 * <p>错误码：[api-contract.md §11](#)
 */
@Service
public class ScreenshotService {

    private static final Logger log = LoggerFactory.getLogger(ScreenshotService.class);
    private static final DateTimeFormatter FILE_TS_FMT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS");
    private static final DateTimeFormatter ISO_FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final String DEFAULT_OCR_LOG_PATH = "docs/test-records/ocr-results";

    private final FileStorageService storage;
    private final VisionModelClient visionModelClient; // 用于 extractFirstJsonObject 抽 JSON
    private final AiRouter aiRouter;                    // 1a.8 vision 入口
    private final PromptLoaderService promptLoader;
    private final ChatHistoryMapper chatHistoryMapper;
    private final ObjectMapper objectMapper;            // 1a.8 v3: OCR 写盘
    private final FundCategoryResolver fundCategoryResolver; // 1a.8.8 决策 8：类别归一化
    private final Path ocrLogRoot;

    /** 1a.8 路由 imageCount 上下文：1 张图 parse → imageCount=1；批量 4 张 → imageCount=4。 */
    private static final int DEFAULT_IMAGE_COUNT = 1;

    public ScreenshotService(FileStorageService storage,
                             VisionModelClient visionModelClient,
                             AiRouter aiRouter,
                             PromptLoaderService promptLoader,
                             ChatHistoryMapper chatHistoryMapper,
                             ObjectMapper objectMapper,
                             FundCategoryResolver fundCategoryResolver,
                             @Value("${fincontrol.ocr.log-path:" + DEFAULT_OCR_LOG_PATH + "}") String ocrLogPath) {
        this.storage = storage;
        this.visionModelClient = visionModelClient;
        this.aiRouter = aiRouter;
        this.promptLoader = promptLoader;
        this.chatHistoryMapper = chatHistoryMapper;
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.fundCategoryResolver = fundCategoryResolver;
        this.ocrLogRoot = resolveOcrLogRoot(ocrLogPath);
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
            writeOcrLog(req.getFileId(), usedProvider, fallbackTriggered, raw, null, e);
            persistAssistantError(conversationId, req.getUserId(), e, raw, usedProvider, fallbackTriggered);
            throw e;
        }

        ParsedAsset asset = mapToParsedAsset(conversationId, raw, json, req.getUserId());

        // 1a.8 v3: OCR 真实数据日志写盘
        writeOcrLog(req.getFileId(), usedProvider, fallbackTriggered, raw, asset, null);

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
            writeOcrLog(fileId, usedProvider, fallbackTriggered, raw, null, e);
            persistAssistantError(req.getConversationId(), userMsg.getUserId(), e, raw, usedProvider, fallbackTriggered);
            throw e;
        }

        ParsedAsset asset = mapToParsedAsset(req.getConversationId(), raw, json, userMsg.getUserId());

        // 1a.8 v3: OCR 真实数据日志写盘
        writeOcrLog(fileId, usedProvider, fallbackTriggered, raw, asset, null);

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
    // 1a.8 v3: OCR 真实数据日志写盘
    // ===================================================================

    /**
     * OCR 写盘：保存每次 parse 的 raw response + parsed JSON + timestamp + usedProvider 到
     * {@code docs/test-records/ocr-results/{date}/}（gitignored）。
     * <p>用于人工对照 standard 真实数据 + 后续 v3 prompt 改进。
     */
    private void writeOcrLog(String fileId, String usedProvider, boolean fallbackTriggered,
                             String raw, ParsedAsset asset, BusinessException error) {
        try {
            LocalDateTime now = LocalDateTime.now();
            Path ocrDir = ocrLogRoot.resolve(LocalDate.now().toString());
            Files.createDirectories(ocrDir);

            String prefix = now.format(FILE_TS_FMT)
                    + "_" + safeFileNamePart(fileId)
                    + "_" + safeFileNamePart(usedProvider) + "_";
            Path ocrFile = Files.createTempFile(ocrDir, prefix, ".json");

            Map<String, Object> ocr = new LinkedHashMap<>();
            ocr.put("timestamp", now.format(ISO_FMT));
            ocr.put("file_id", fileId);
            ocr.put("used_provider", usedProvider);
            ocr.put("fallback_triggered", fallbackTriggered);
            ocr.put("raw_response", raw);
            if (asset != null) {
                Map<String, Object> parsedMap = new LinkedHashMap<>();
                parsedMap.put("conversation_id", asset.getConversationId());
                parsedMap.put("snapshot_date", asset.getSnapshotDate());
                parsedMap.put("total_asset", asset.getTotalAsset());
                parsedMap.put("categories", asset.getCategories());
                parsedMap.put("matched_funds", asset.getMatchedFunds());
                ocr.put("parsed", parsedMap);
            } else if (error != null) {
                ocr.put("error_code", error.getErrorCode().getCode());
                ocr.put("error_message", error.getMessage());
            }
            Files.write(ocrFile, objectMapper.writeValueAsBytes(ocr));
            log.info("OCR 日志已写入: {}", ocrFile);
        } catch (Exception ex) {
            log.warn("OCR 日志写盘失败（不阻塞主流程）: {}", ex.getMessage());
        }
    }

    /**
     * 相对配置路径以仓库根（包含 .git 的最近父目录）为基准；部署环境没有 .git 时回退到 JVM CWD。
     * 绝对路径保持不变，便于通过 FINCONTROL_OCR_LOG_PATH 显式覆盖。
     */
    static Path resolveOcrLogRoot(String configuredPath) {
        String value = configuredPath == null || configuredPath.isBlank()
                ? DEFAULT_OCR_LOG_PATH
                : configuredPath.trim();
        Path configured = Paths.get(value);
        if (configured.isAbsolute()) {
            return configured.normalize();
        }

        Path cwd = Paths.get("").toAbsolutePath().normalize();
        for (Path candidate = cwd; candidate != null; candidate = candidate.getParent()) {
            if (Files.isDirectory(candidate.resolve(".git"))) {
                return candidate.resolve(configured).normalize();
            }
        }
        return cwd.resolve(configured).normalize();
    }

    private static String safeFileNamePart(String value) {
        if (value == null || value.isBlank()) return "unknown";
        return value.replaceAll("[^A-Za-z0-9._-]", "_");
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

    private ParsedAsset mapToParsedAsset(String conversationId, String raw, JsonNode json, Long userId) {
        ParsedAsset out = new ParsedAsset();
        out.setConversationId(conversationId);
        out.setSnapshotDate(firstText(json, "snapshot_date", "date"));
        out.setTotalAsset(decimalOrNull(json, "total_asset"));
        out.setSixCategoriesTotal(decimalOrNull(json, "six_categories_total"));
        out.setBalanceFund(decimalOrNull(json, "balance_fund"));

        List<ParsedAsset.CategoryBlock> blocks = new ArrayList<>();

        // Strategy A
        JsonNode categories = json.path("categories");
        if (categories.isArray() && categories.size() > 0) {
            for (JsonNode c : categories) {
                ParsedAsset.CategoryBlock block = new ParsedAsset.CategoryBlock();
                String rawCat = textOrNull(c, "category_name");
                // 1a.8.8：块类别名归一化（QDII → 海外权益类 等）
                String canonical = CategoryEnum.fromAlias(rawCat);
                block.setCategoryName(canonical != null ? canonical : (rawCat == null ? "其他" : rawCat));
                JsonNode funds = c.path("funds");
                List<ParsedAsset.FundLine> lines = new ArrayList<>();
                if (funds.isArray()) {
                    for (JsonNode f : funds) {
                        ParsedAsset.FundLine line = new ParsedAsset.FundLine();
                        line.setFundName(textOrNull(f, "fund_name"));
                        line.setAmount(decimalOrNull(f, "amount"));
                        line.setProfit(firstDecimal(f, "holding_profit", "profit"));
                        line.setHoldingProfit(firstDecimal(f, "holding_profit", "profit"));
                        line.setCumulativeProfit(firstDecimal(f, "cumulative_profit", "holding_profit", "profit"));
                        // 1a.8.8：调 resolver 拿 isUserConfirmed + 覆盖 block（如果 user_correct 不一致）
                        applyResolver(line, block, rawCat, userId);
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
                    // 兼容历史 minimax schema（name/category/holding_pnl）与 v2 prompt 实际 schema
                    // （fund_name/category_name/profit）。
                    String cat = firstText(h, "category", "category_name");
                    if (cat == null || cat.isBlank()) cat = "其他";
                    byCategory.computeIfAbsent(cat, k -> new ArrayList<>()).add(h);
                }
                JsonNode summary = json.path("category_summary");
                for (Map.Entry<String, List<JsonNode>> entry : byCategory.entrySet()) {
                    ParsedAsset.CategoryBlock block = new ParsedAsset.CategoryBlock();
                    String rawCat = entry.getKey();
                    String canonical = CategoryEnum.fromAlias(rawCat);
                    block.setCategoryName(canonical != null ? canonical : rawCat);
                    List<ParsedAsset.FundLine> lines = new ArrayList<>();
                    for (JsonNode h : entry.getValue()) {
                        ParsedAsset.FundLine line = new ParsedAsset.FundLine();
                        line.setFundName(firstText(h, "name", "fund_name"));
                        line.setAmount(decimalOrNull(h, "amount"));
                        line.setProfit(firstDecimal(h, "holding_pnl", "holding_profit", "profit"));
                        line.setHoldingProfit(firstDecimal(h, "holding_pnl", "holding_profit", "profit"));
                        line.setCumulativeProfit(firstDecimal(h, "cumulative_profit", "holding_pnl", "holding_profit", "profit"));
                        applyResolver(line, block, rawCat, userId);
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

    /**
     * 1a.8.8：调 resolver 归一化单只基金；user_correct 命中且 canonical 与 block 不同则覆盖 block。
     */
    private void applyResolver(ParsedAsset.FundLine line,
                               ParsedAsset.CategoryBlock block,
                               String rawCategory,
                               Long userId) {
        if (line.getFundName() == null || userId == null) {
            line.setIsUserConfirmed(false);
            return;
        }
        try {
            FundCategoryResolver.ResolvedCategory resolved =
                    fundCategoryResolver.resolve(line.getFundName(), rawCategory, userId);
            line.setIsUserConfirmed(resolved.isUserConfirmed());
            if (resolved.isUserConfirmed()
                    && resolved.canonicalName() != null
                    && !resolved.canonicalName().equals(block.getCategoryName())) {
                log.warn("1a.8.8 user_correct 覆盖 block category: fund={} block={} → user={}",
                        line.getFundName(), block.getCategoryName(), resolved.canonicalName());
                block.setCategoryName(resolved.canonicalName());
            }
        } catch (Exception e) {
            log.warn("resolver 调用失败，fallback: fund={} err={}", line.getFundName(), e.getMessage());
            line.setIsUserConfirmed(false);
        }
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isMissingNode() || v.isNull() ? null : v.asText();
    }

    private static String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = textOrNull(node, field);
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }

    private static BigDecimal decimalOrNull(JsonNode node, String field) {
        JsonNode v = node.path(field);
        if (v.isMissingNode() || v.isNull() || !v.isNumber()) return null;
        return new BigDecimal(v.asText());
    }

    private static BigDecimal firstDecimal(JsonNode node, String... fields) {
        for (String field : fields) {
            BigDecimal value = decimalOrNull(node, field);
            if (value != null) return value;
        }
        return null;
    }
}
