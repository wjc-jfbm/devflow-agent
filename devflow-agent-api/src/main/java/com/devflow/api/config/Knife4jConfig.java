package com.devflow.api.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Knife4j / Swagger API 文档配置
 * 访问地址: http://localhost:8080/doc.html
 */
@Configuration
public class Knife4jConfig {

    @Bean
    public OpenAPI devFlowOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("DevFlow Agent API")
                        .description("Multi-Agent 协作开发流水线 - 从 GitHub Issue 到 PR 全自动化")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("DevFlow Team")
                                .email("devflow@example.com")));
    }
}
