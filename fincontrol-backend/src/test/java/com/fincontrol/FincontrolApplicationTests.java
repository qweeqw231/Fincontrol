package com.fincontrol;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Phase 1a.1 基础设施冒烟测试。
 *
 * <p>目标：验证 Spring 容器在缺少数据源的情况下也能正确装配，
 * 证明 1a.1 仅依赖基础 starter 即可启动（数据源相关的集成测试在 1a.2+ 引入）。
 *
 * <p>排除原因：
 * <ul>
 *   <li>本地 MySQL 默认未启动 —— 此测试不应因数据库不可用而失败</li>
 *   <li>真实集成测试在 Phase 1a.2 测试用例中用 Testcontainers 或 H2 引入</li>
 * </ul>
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
        "spring.autoconfigure.exclude=" +
            "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration," +
            "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration," +
            "com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration"
    }
)
@TestPropertySource(properties = {
    "spring.profiles.active=test",
    "deepseek.api-key=test-placeholder-not-used"
})
class FincontrolApplicationTests {

    @Test
    void contextLoadsWithoutDatabase() {
        // Phase 1a.1 DoD：Spring 容器能装配 = 项目可启动
        // 真正的 /actuator/health + DataSource 集成测试在 Phase 1a.2 引入
    }
}
