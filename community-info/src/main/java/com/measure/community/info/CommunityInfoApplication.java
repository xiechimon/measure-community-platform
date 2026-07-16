package com.measure.community.info;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@EnableDiscoveryClient
@ComponentScan(basePackages = {"com.measure.community.common", "com.measure.community.info"})
public class CommunityInfoApplication {
    public static void main(String[] args) {
        SpringApplication.run(CommunityInfoApplication.class, args);
    }
}
