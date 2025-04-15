package com.cgi.privsense.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SpringDocConfig {

    @Bean
    public OpenAPI apiInfo() {
        return new OpenAPI()
                .components(new Components())
                .addServersItem(new Server().url("/").description("Default Server URL"))
                .info(new Info()
                        .title("PrivSense API")
                        .description("API for database scanning and PII detection")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("CGI")
                                .url("https://www.cgi.com")
                                .email("support@privsense.com"))
                        .license(new License()
                                .name("Proprietary")
                                .url("https://www.cgi.com/licenses"))
                );
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

    // If you have any other API groups, add them here
    @Bean
    public GroupedOpenApi actuatorApi() {
        return GroupedOpenApi.builder()
                .group("management")
                .pathsToMatch("/actuator/**")
                .build();
    }
}