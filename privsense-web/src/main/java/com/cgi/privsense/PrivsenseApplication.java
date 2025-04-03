package com.cgi.privsense;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.jdbc.JdbcRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.ComponentScan;

/**
 * Main application class for PrivSense.
 */
@SpringBootApplication(
        scanBasePackages = {"com.cgi.privsense"},
        exclude = {DataSourceAutoConfiguration.class,
                JdbcRepositoriesAutoConfiguration.class}
)
@ComponentScan(basePackages = {"com.cgi.privsense"})
@ConfigurationPropertiesScan(basePackages = {"com.cgi.privsense"})
@EnableCaching
public class PrivsenseApplication {

    /**
     * Application entry point.
     *
     * @param args Command line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(PrivsenseApplication.class, args);
    }
}