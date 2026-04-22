package com.spotlink;

import com.spotlink.core.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class SpotLinkApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpotLinkApplication.class, args);
    }
}
