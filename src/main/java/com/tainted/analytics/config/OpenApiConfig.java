package com.tainted.analytics.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI analyticsOpenApi() {
        return new OpenAPI().info(new Info()
                .title("analytics-service API")
                .version("0.1.0")
                .description("Kafka 기반 감정 시계열 집계 및 무드 조회 API"));
    }
}
