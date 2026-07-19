package com.fincontrol;

import com.fincontrol.mapper.AssetRawMapper;
import com.fincontrol.mapper.AssetSnapshotMapper;
import com.fincontrol.mapper.ChatHistoryMapper;
import com.fincontrol.mapper.FundCategoryMapMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

/**
 * Phase 1a.1 基础设施冒烟测试（已升级兼容 1a.2 持久化 bean 链路）。
 *
 * <p>目标：验证 Spring 容器在缺少真实数据源的情况下也能正确装配。
 * 1a.2 起新增 ChatHistoryMapper（依赖 SqlSessionFactory）和 PromptLoaderService（依赖 JdbcTemplate），
 * 这里用 {@link MockBean} 替换，使容器可以在没有真实 MySQL 的情况下启动。
 *
 * <p>真实集成测试在 Phase 1a.2+ 引入（Testcontainers / 本地 MySQL）。
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
    "fincontrol.vision.api-key=placeholder-for-test-only"
})
class FincontrolApplicationTests {

    /** 替换所有 MyBatis-Plus mapper，避免禁用 MybatisPlusAutoConfiguration 后注入失败 */
    @MockBean
    private com.fincontrol.mapper.CategoryMasterMapper categoryMasterMapper;

    @MockBean
    private AssetRawMapper assetRawMapper;

    @MockBean
    private AssetSnapshotMapper assetSnapshotMapper;

    @MockBean
    private ChatHistoryMapper chatHistoryMapper;

    @MockBean
    private FundCategoryMapMapper fundCategoryMapMapper;

    /** 替换 PromptLoaderService 依赖的 JdbcTemplate */
    @MockBean
    private JdbcTemplate jdbcTemplate;

    @Test
    void contextLoadsWithoutDatabase() {
        // Phase 1a.1 / 1a.2 DoD：Spring 容器可装配 + 持有所有 mock 的持久化依赖占位
    }
}
