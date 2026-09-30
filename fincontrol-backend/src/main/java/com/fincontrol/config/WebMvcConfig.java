package com.fincontrol.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

/**
 * 决策 36：单进程运行形态 —— Spring Boot 直接托管前端构建产物。
 *
 * <h3>背景</h3>
 * 原先「开」需要启动 MySQL + Spring Boot(8080) + Vite dev server(5173) 三方，
 * 「关」也要分头处理，与「即开即用、用完就关」的单机使用诉求不符。
 * 本配置让后端直接提供前端 build 产物，浏览器只访问 <b>8080 单端口</b>：
 * 首页那个 {@code POST /api/system/shutdown} 按钮因此天然成为「一键全关」。
 *
 * <h3>要点</h3>
 * <ul>
 *   <li>静态位置默认指向 {@code fincontrol-frontend/dist}（不复制进 src，避免源码树污染；
 *       dist 已被 .gitignore 忽略）</li>
 *   <li>前端使用 {@code BrowserRouter}，直接刷新 {@code /nav}、{@code /ratio} 等深链会 404，
 *       故需 SPA 兜底：未命中真实文件时回退 {@code index.html}</li>
 *   <li>{@code api/}、{@code actuator/}、{@code swagger-ui} 等后端路径 <b>不参与兜底</b>，
 *       保持其原生 404 / 错误语义，避免把「接口写错」伪装成「返回了一个页面」</li>
 *   <li>dist 不存在时（纯后端开发场景）本配置静默不生效，接口照常</li>
 * </ul>
 *
 * <p>双模式：日常用 build 产物走 8080；前端开发仍可 {@code npm run dev} 走 5173 + Vite proxy，
 * 两种模式互不影响（前端 apiClient 用相对路径 {@code /api}，同源与代理下均可工作）。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private static final Logger log = LoggerFactory.getLogger(WebMvcConfig.class);

    /** 逗号分隔的 Spring 资源位置列表；由启动脚本传入绝对路径以保证与工作目录无关 */
    private final String staticLocations;

    public WebMvcConfig(
            @Value("${fincontrol.frontend.static-locations:classpath:/static/,file:./fincontrol-frontend/dist/}")
            String staticLocations) {
        this.staticLocations = staticLocations;
        log.info("决策36 前端静态资源位置: {}", staticLocations);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String[] locations = staticLocations.split(",");

        registry.addResourceHandler("/**")
                .addResourceLocations(locations)
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        // 根路径与以 / 结尾的路径按入口页处理。
                        // （不能交给 createRelative("") —— 会解析到目录本身，导致渲染目录时报 500）
                        if (resourcePath.isEmpty() || resourcePath.endsWith("/")) {
                            resourcePath = "index.html";
                        }
                        Resource requested = location.createRelative(resourcePath);
                        if (requested.exists() && requested.isReadable() && !isDirectory(requested)) {
                            return requested;
                        }
                        if (isBackendPath(resourcePath)) {
                            // 后端路径不兜底：让不存在的接口保持 404，而不是返回 index.html
                            return null;
                        }
                        // SPA 兜底：/nav、/ratio 等前端路由硬刷新时返回入口页
                        Resource index = location.createRelative("index.html");
                        return (index.exists() && index.isReadable()) ? index : null;
                    }

                    private boolean isDirectory(Resource resource) {
                        try {
                            return resource.getFile().isDirectory();
                        } catch (IOException e) {
                            // 非 file: 协议（如 classpath 打包进 jar）无法判定，按非目录处理
                            return false;
                        }
                    }
                });
    }

    /** 这些前缀属于后端自身，不参与 SPA 兜底 */
    private static boolean isBackendPath(String resourcePath) {
        return resourcePath.startsWith("api/")
                || resourcePath.startsWith("actuator/")
                || resourcePath.startsWith("swagger-ui")
                || resourcePath.startsWith("v3/api-docs")
                || resourcePath.startsWith("error");
    }
}