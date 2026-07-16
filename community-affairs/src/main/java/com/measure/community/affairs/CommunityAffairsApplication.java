package com.measure.community.affairs;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@EnableDiscoveryClient
@ComponentScan(basePackages = {"com.measure.community.common", "com.measure.community.affairs"})
public class CommunityAffairsApplication {
    public static void main(String[] args) {
        SpringApplication.run(CommunityAffairsApplication.class, args);
    }
}
