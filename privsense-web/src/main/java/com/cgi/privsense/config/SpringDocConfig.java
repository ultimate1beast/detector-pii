package com.cgi.privsense.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SpringDocConfig {

    @Bean
    public OpenAPI apiInfo() {
        return new OpenAPI()
                .info(new Info()
                        .title("PrivSense API")
                        .description("API for database scanning and PII detection")
                        .version("1.0.0"));
    }

    @Bean
    public GroupedOpenApi dbScannerApi() {
        return GroupedOpenApi.builder()
                .group("dbscanner")
                .packagesToScan("com.cgi.privsense.dbscanner.api")
                .pathsToMatch("/api/scanner/**")
                .build();
    }
}