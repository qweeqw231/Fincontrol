package com.fincontrol.ai;

import com.fincontrol.common.BusinessException;
import com.fincontrol.common.ErrorCode;
import com.fincontrol.entity.ChatHistory;
import com.fincontrol.service.VisionModelClient;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 1a.8 AI 路由主入口 — vision + chat 统一 try primary → fallback 链。
 *
 * <p>实现要点（[2026-07-18_phase1a8-work-plan.md §6 切片](#)）：
 * <ul>
 *   <li>vision 路由：imageCount ≤ threshold → minimax primary + 豆包 fallback；imageCount > threshold → 豆包 primary + minimax fallback</li>
 *   <li>vision 缓存：Caffeine TTL 24h / max 1024，key = SHA-256(file)</li>
 *   <li>vision 韧性：resilience4j Retry + CircuitBreaker；5xx / 超时 → 切 fallback</li>
 *   <li>chat 路由：minimax primary + DeepSeek fallback（1a.9 实施）</li>
 *   <li>返回 VisionResult / ChatResult 含 usedProvider + fallbackTriggered → 写 chat_history 监控字段</li>
 * </ul>
 */
@Service
public class AiRouter {

    private static final Logger log = LoggerFactory.getLogger(AiRouter.class);

    private static final String VISION_CACHE_PREFIX = "v2:vision:";

    public enum VisionRoute {
        MINIMAX_PRIMARY,
        DOUBAO_PRIMARY
    }

    private final VisionModelClient visionClient;
    private final TextAiClient textAiClient;
    private final AiProperties properties;

    private final Cache<String, VisionResult> visionCache;
    private final Retry visionRetry;
    private final CircuitBreaker visionCircuitBreaker;
    private final Retry chatRetry;

    @Autowired
    public AiRouter(AiProperties properties,
                    VisionModelClient visionClient,
                    TextAiClient textAiClient) {
        this.properties = properties;
        this.visionClient = visionClient;
        this.textAiClient = textAiClient;

        AiProperties.Router router = properties.getRouter();
        this.visionCache = Caffeine.newBuilder()
                .maximumSize(router.getCacheMaxSize())
                .expireAfterWrite(router.getCacheTtlHours(), TimeUnit.HOURS)
                .build();

        this.visionRetry = Retry.of("vision",
                RetryConfig.custom()
                        .maxAttempts(Math.max(1, router.getVisionRetryAttempts()))
                        .retryExceptions(FallbackTrigger.class)
                        .build());

        this.visionCircuitBreaker = CircuitBreaker.of("vision",
                CircuitBreakerConfig.custom()
                        .slidingWindowSize(router.getVisionCircuitBreakerSlidingWindow())
                        .failureRateThreshold((float) router.getVisionCircuitBreakerFailureRate())
                        .waitDurationInOpenState(Duration.ofSeconds(60))
                        .build());

        this.chatRetry = Retry.of("chat",
                RetryConfig.custom()
                        .maxAttempts(2)
                        .retryExceptions(FallbackTrigger.class)
                        .build());

        log.info("1a.8 AiRouter 启动 imageCountThreshold={} cacheMaxSize={} cacheTtlHours={} visionRetry={} cbSlidingWindow={} cbFailureRate={}",
                router.getImageCountThreshold(), router.getCacheMaxSize(), router.getCacheTtlHours(),
                router.getVisionRetryAttempts(),
                router.getVisionCircuitBreakerSlidingWindow(),
                router.getVisionCircuitBreakerFailureRate());
    }

    public AiRouter(AiProperties properties,
                    VisionModelClient visionClient,
                    TextAiClient textAiClient,
                    Cache<String, VisionResult> visionCache,
                    Retry visionRetry,
                    CircuitBreaker visionCircuitBreaker,
                    Retry chatRetry) {
        this.properties = properties;
        this.visionClient = visionClient;
        this.textAiClient = textAiClient;
        this.visionCache = visionCache;
        this.visionRetry = visionRetry;
        this.visionCircuitBreaker = visionCircuitBreaker;
        this.chatRetry = chatRetry;
    }

    /** 旧单图入口；保留显式 imageCount 以兼容 1a.8 调用和测试。 */
    public VisionResult callVision(File imageFile, String systemPrompt, String userMessage, int imageCount) {
        Objects.requireNonNull(imageFile, "imageFile");
        return callVisionInternal(List.of(imageFile), systemPrompt, userMessage, imageCount);
    }

    /** 1a.10 多图入口：路由 imageCount 与实际文件数始终一致。 */
    public VisionResult callVision(List<File> imageFiles, String systemPrompt, String userMessage) {
        if (imageFiles == null || imageFiles.isEmpty()) {
            throw new IllegalArgumentException("imageFiles 至少包含 1 张图");
        }
        List<File> copy = List.copyOf(imageFiles);
        copy.forEach(file -> Objects.requireNonNull(file, "imageFile"));
        return callVisionInternal(copy, systemPrompt, userMessage, copy.size());
    }

    private VisionResult callVisionInternal(List<File> imageFiles, String systemPrompt,
                                            String userMessage, int imageCount) {
        String cacheKey = VISION_CACHE_PREFIX + orderedImageHashes(imageFiles);
        VisionResult cached = visionCache.getIfPresent(cacheKey);
        if (cached != null) {
            log.info("vision cache hit imageCount={} key={}", imageCount, cacheKey);
            return cached.markCacheHit();
        }

        VisionRoute route = (imageCount <= properties.getRouter().getImageCountThreshold())
                ? VisionRoute.MINIMAX_PRIMARY
                : VisionRoute.DOUBAO_PRIMARY;
        log.info("vision 路由 imageCount={} threshold={} route={}",
                imageCount, properties.getRouter().getImageCountThreshold(), route);

        VisionResult result = callWithFallback(imageFiles, systemPrompt, userMessage, route);

        try {
            visionCache.put(cacheKey, result);
        } catch (Exception e) {
            log.warn("vision cache put failed (non-fatal): {}", e.getMessage());
        }
        return result;
    }

    private VisionResult callWithFallback(List<File> imageFiles, String systemPrompt,
                                          String userMessage, VisionRoute route) {
        boolean isMinimaxPrimary = (route == VisionRoute.MINIMAX_PRIMARY);

        try {
            String primaryContent = retryWithCircuitBreaker(() ->
                    visionClient.callRaw(imageFiles, systemPrompt, userMessage,
                            isMinimaxPrimary ? ApiStyle.OPENAI_CHAT : ApiStyle.OPENAI_RESPONSES));
            return VisionResult.success(
                    isMinimaxPrimary ? ChatHistory.PROVIDER_MINIMAX : ChatHistory.PROVIDER_DOUBAO,
                    false, primaryContent);
        } catch (FallbackTrigger ft) {
            log.warn("vision primary ({}): fallback triggered: {}",
                    isMinimaxPrimary ? "minimax" : "doubao", ft.getMessage());
        } catch (BusinessException be) {
            throw be;
        }

        try {
            String fallbackContent = visionClient.callRaw(imageFiles, systemPrompt, userMessage,
                    isMinimaxPrimary ? ApiStyle.OPENAI_RESPONSES : ApiStyle.OPENAI_CHAT);
            return VisionResult.success(
                    isMinimaxPrimary ? ChatHistory.PROVIDER_DOUBAO : ChatHistory.PROVIDER_MINIMAX,
                    true, fallbackContent);
        } catch (FallbackTrigger ft) {
            log.error("vision fallback ({}): also failed: {}",
                    isMinimaxPrimary ? "doubao" : "minimax", ft.getMessage());
            throw new BusinessException(ErrorCode.VISION_INVALID_JSON,
                    "vision primary + fallback 都失败：primary=" + (isMinimaxPrimary ? "minimax" : "doubao")
                            + " fallback=" + (isMinimaxPrimary ? "doubao" : "minimax")
                            + " lastError=" + ft.getMessage());
        } catch (BusinessException be) {
            throw be;
        }
    }

    /**
     * 1a.8 韧性核心：Retry + CircuitBreaker 装饰 + 5xx/超时→FallbackTrigger 识别。
     */
    private String retryWithCircuitBreaker(Supplier<String> supplier) {
        Supplier<String> decorated = CircuitBreaker.decorateSupplier(visionCircuitBreaker, supplier);
        decorated = Retry.decorateSupplier(visionRetry, decorated);
        try {
            return decorated.get();
        } catch (FallbackTrigger ft) {
            throw ft;
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof FallbackTrigger) {
                throw (FallbackTrigger) cause;
            }
            // 5xx / 超时 → FallbackTrigger（触发 retry 或 切 fallback）
            if (cause instanceof BusinessException) {
                BusinessException be = (BusinessException) cause;
                ErrorCode ec = be.getErrorCode();
                if (ec == ErrorCode.VISION_INVALID_JSON || ec == ErrorCode.VISION_TIMEOUT) {
                    throw new FallbackTrigger("vision 5xx/timeout: " + be.getMessage(), be);
                }
                throw be;
            }
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            throw new RuntimeException(cause);
        }
    }

    public static class FallbackTrigger extends RuntimeException {
        public FallbackTrigger(String message) { super(message); }
        public FallbackTrigger(String message, Throwable cause) { super(message, cause); }
    }

    public ChatResult callChat(String systemPrompt, String userMessage) {
        Objects.requireNonNull(userMessage, "userMessage");
        try {
            Supplier<String> decorated = Retry.decorateSupplier(chatRetry, () ->
                    textAiClient.chat(systemPrompt, userMessage));
            String content = decorated.get();
            return ChatResult.success(ChatHistory.PROVIDER_MINIMAX, false, content);
        } catch (FallbackTrigger ft) {
            log.error("chat primary minimax failed: {}", ft.getMessage());
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "chat primary minimax 失败（DeepSeek fallback 1a.9 实施）：" + ft.getMessage());
        } catch (BusinessException be) {
            throw be;
        }
    }

    /** 有序组合 hash；固定 64 字符单图 hash + 分隔符避免跨页顺序碰撞。 */
    private static String orderedImageHashes(List<File> files) {
        StringBuilder key = new StringBuilder(files.size() * 65);
        for (int i = 0; i < files.size(); i++) {
            if (i > 0) key.append(':');
            key.append(sha256File(files.get(i)));
        }
        return key.toString();
    }

    private static String sha256File(File file) {
        try {
            byte[] bytes = Files.readAllBytes(file.toPath());
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(bytes);
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) sb.append(String.format(Locale.ROOT, "%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 计算失败: " + file.getAbsolutePath(), e);
        }
    }

    public static class VisionResult {
        private final String usedProvider;
        private final boolean fallbackTriggered;
        private final String content;
        private final boolean cacheHit;

        private VisionResult(String usedProvider, boolean fallbackTriggered, String content, boolean cacheHit) {
            this.usedProvider = usedProvider;
            this.fallbackTriggered = fallbackTriggered;
            this.content = content;
            this.cacheHit = cacheHit;
        }

        public static VisionResult success(String provider, boolean fallback, String content) {
            return new VisionResult(provider, fallback, content, false);
        }

        public VisionResult markCacheHit() {
            return new VisionResult(usedProvider, fallbackTriggered, content, true);
        }

        public String usedProvider() { return usedProvider; }
        public boolean fallbackTriggered() { return fallbackTriggered; }
        public String content() { return content; }
        public boolean cacheHit() { return cacheHit; }
    }

    public static class ChatResult {
        private final String usedProvider;
        private final boolean fallbackTriggered;
        private final String content;

        private ChatResult(String usedProvider, boolean fallbackTriggered, String content) {
            this.usedProvider = usedProvider;
            this.fallbackTriggered = fallbackTriggered;
            this.content = content;
        }

        public static ChatResult success(String provider, boolean fallback, String content) {
            return new ChatResult(provider, fallback, content);
        }

        public String usedProvider() { return usedProvider; }
        public boolean fallbackTriggered() { return fallbackTriggered; }
        public String content() { return content; }
    }
}
