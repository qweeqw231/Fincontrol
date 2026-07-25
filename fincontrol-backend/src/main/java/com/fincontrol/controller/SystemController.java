package com.fincontrol.controller;

import com.fincontrol.common.ApiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 1b.4 PR8 / 决策 35：系统级管理（关闭服务）。
 * <p>本控制器仅暴露一个 {@code POST /api/system/shutdown} 端点，
 * 用于在桌面快捷方式 + HomePage 关闭按钮链路中安全停止 Spring Boot。
 * <ul>
 *   <li>同步返回响应，让前端立即确认（包含 graceMillis 等元数据）</li>
 *   <li>异步（后台线程）触发 {@link SpringApplication#exit}，让 in-flight 请求有时间完成</li>
 *   <li>由 {@code fincontrol.system.shutdown.enabled} 配置控制（默认 true）</li>
 *   <li>grace period（{@code fincontrol.system.shutdown.grace-millis}，默认 800ms）保证 shutdown 干净</li>
 * </ul>
 * <p>不实现任何鉴权（决策 5 阶段 0 已明确本地 MVP 单用户）；
 * 如未来需要远程控制，应在网关层加 token / IP 白名单，而非本控制器。
 */
@RestController
@RequestMapping("/api/system")
public class SystemController {

    @Value("${fincontrol.system.shutdown.enabled:true}")
    private boolean shutdownEnabled;

    @Value("${fincontrol.system.shutdown.grace-millis:800}")
    private long graceMillis;

    private final ApplicationContext ctx;

    public SystemController(ApplicationContext ctx) {
        this.ctx = ctx;
    }

    /**
     * 优雅关闭服务。
     * <p>同步响应 payload：{@code {shuttingDown:true, graceMillis:N, mode:"async-shutdown"}}
     * <p>异步线程会在 {@code graceMillis} 后调用 {@link SpringApplication#exit}，
     * 触发 Spring Boot 完整停机流程（包括 Tomcat shutdown、bean destroy 等）。
     */
    @PostMapping("/shutdown")
    public ApiResponse<Map<String, Object>> shutdown() {
        if (!shutdownEnabled) {
            return ApiResponse.error(403, "shutdown endpoint disabled");
        }
        // 异步线程触发 exit；同步先返回响应（让浏览器在 800ms grace 内拿到 success body）
        Thread shutdownThread = new Thread(() -> {
            try {
                Thread.sleep(graceMillis);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            int exitCode = SpringApplication.exit(ctx, () -> 0);
            // System.exit 在 SpringApplication.exit 内部已经调用，本线程结束即停机
            // 保留日志便于调试
            System.out.println("[fincontrol-shutdown] SpringApplication.exit called, code=" + exitCode);
        }, "fincontrol-shutdown");
        shutdownThread.setDaemon(false);  // 显式非守护线程，确保执行完整退出
        shutdownThread.start();

        Map<String, Object> data = new HashMap<>();
        data.put("shuttingDown", true);
        data.put("graceMillis", graceMillis);
        data.put("mode", "async-shutdown");
        return ApiResponse.success(data);
    }
}
