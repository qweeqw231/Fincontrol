package com.fincontrol;

import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

/**
 * 1a.3 集成测试公共基类。
 *
 * <p>{@code @SpringBootTest} 加载主 Application，{@code @AutoConfigureTestDatabase} 替换为 H2 内存库。
 * <p>{@code @Sql} 在每个测试方法前加载 schema（覆盖前一个测试的残留数据）。
 */
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@ActiveProfiles("test")
@Sql(scripts = "/schema-h2.sql")
public abstract class AbstractIT {
}
