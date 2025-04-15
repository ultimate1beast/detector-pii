package com.cgi.privsense.common.config;

import com.cgi.privsense.common.config.properties.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
    DatabaseProperties.class,
    PiiDetectionProperties.class
})
public class PrivSenseConfigurationEnabler {
    // This class enables the configuration properties
}