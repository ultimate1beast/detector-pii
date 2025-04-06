package com.cgi.privsense.common.config;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * Driver configuration properties.
 */
@Data
public class DriverProperties {
    /**
     * Directory where downloaded drivers are stored.
     */
    private String directory = "${user.home}/.dbscanner/drivers";

    /**
     * URL of the Maven repository.
     */
    private String repositoryUrl = "https://repo1.maven.org/maven2";

    /**
     * Map of Maven coordinates for each driver.
     */
    private Map<String, String> coordinates = new HashMap<>();
}