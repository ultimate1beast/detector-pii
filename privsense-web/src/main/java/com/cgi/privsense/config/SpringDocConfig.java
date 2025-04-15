package com.cgi.privsense.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SpringDocConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("PrivSense API")
                        .version("1.0.0")
                        .description("API for database scanning and PII detection")
                        .contact(new Contact()
                                .name("CGI")
                                .url("https://www.cgi.com")
                                .email("support@privsense.com"))
                        .license(new License()
                                .name("Proprietary")
                                .url("https://www.cgi.com/licenses")))
                .components(new Components())
                .addServersItem(new Server().url("/").description("Default Server URL"));
    }

    @Bean
    public GroupedOpenApi dbScannerApi() {
        return GroupedOpenApi.builder()
                .group("dbscanner")
                .packagesToScan("com.cgi.privsense.dbscanner.api")
                .pathsToMatch("/api/scanner/**")
                .build();
    }

    @Bean
    public GroupedOpenApi piiDetectorApi() {
        return GroupedOpenApi.builder()
                .group("piidetector")
                .packagesToScan("com.cgi.privsense.piidetector.api")
                .pathsToMatch("/api/pii/**")
                .build();
    }

    @Bean
    public GroupedOpenApi actuatorApi() {
        return GroupedOpenApi.builder()
                .group("management")
                .pathsToMatch("/actuator/**")
                .build();
    }
}