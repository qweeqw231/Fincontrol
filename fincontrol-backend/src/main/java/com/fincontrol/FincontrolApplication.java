package com.fincontrol;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * FinControl 后端主类。
 *
 * 配套文档：docs/phase-0-decisions.md
 * 用户决策：Spring Boot 3.3.x + MyBatis-Plus 3.5.9 + Lombok + OkHttp + springdoc-openapi
 *
 * @author 刘博丞
 * @since 1.0.0
 */
@SpringBootApplication
@MapperScan("com.fincontrol.mapper")
public class FincontrolApplication {

    public static void main(String[] args) {
        SpringApplication.run(FincontrolApplication.class, args);
        System.out.println("""
                ============================================
                FinControl Backend Started Successfully
                Swagger UI: http://localhost:8080/swagger-ui.html
                API Docs:   http://localhost:8080/v3/api-docs
                ============================================
                """);
    }
}