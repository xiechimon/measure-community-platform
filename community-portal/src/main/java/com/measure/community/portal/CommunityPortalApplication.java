package com.measure.community.portal;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@EnableDiscoveryClient
@ComponentScan(basePackages = {"com.measure.community.common", "com.measure.community.portal"})
public class CommunityPortalApplication {
    public static void main(String[] args) {
        SpringApplication.run(CommunityPortalApplication.class, args);
    }
}
