package com.cgi.privsense.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;

import java.util.Properties;

@Configuration
public class PropertyMappingConfig {

    @Bean
    public static PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer() {
        PropertySourcesPlaceholderConfigurer configurer = new PropertySourcesPlaceholderConfigurer();

        Properties mappings = new Properties();
        mappings.setProperty("piidetector.ner.service.url", "${privsense.piiDetector.ner.service.url}");
        mappings.setProperty("piidetector.ner.trust.all.certs", "${privsense.piiDetector.ner.trust.all.certs}");

        configurer.setProperties(mappings);
        return configurer;
    }
}