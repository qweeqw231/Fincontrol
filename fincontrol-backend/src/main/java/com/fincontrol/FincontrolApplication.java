package com.fincontrol;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * FinControl 后端应用入口.
 *
 * <p>Phase 0 决策 1（API 契约完成）+ Phase 1a.1 骨架落地点。包扫描 {@code com.fincontrol}：
 * <ul>
 *   <li>{@code controller} —— REST 入口（Phase 1a.2~1a.6 创建）</li>
 *   <li>{@code service}    —— 业务编排（Phase 1a.2 起）</li>
 *   <li>{@code mapper}     —— MyBatis-Plus DAO（{@link MapperScan} 显式声明）</li>
 *   <li>{@code entity}     —— ORM 实体（Phase 1a.2 起）</li>
 *   <li>{@code config}     —— 配置类（MyBatis-Plus、OpenAPI、CORS 等，1a.1 已就绪）</li>
 * </ul>
 *
 * <p>启用 {@link EnableAsync} 用于异步日志写入（Phase 1a.4 operations/recent 等）；
 * 启用 {@link EnableScheduling} 用于未来快照日期过期检查（Phase 2+）。
 */
@SpringBootApplication
@MapperScan("com.fincontrol.mapper")
@EnableAsync
@EnableScheduling
public class FincontrolApplication {

    public static void main(String[] args) {
        // JVM 默认时区与 MySQL session.time_zone 保持一致（Asia/Shanghai）
        // 详见 docs/SETUP.md Q6（避免 DATETIME 偏差 8 小时）
        System.setProperty("user.timezone", "Asia/Shanghai");
        SpringApplication.run(FincontrolApplication.class, args);
    }
}
