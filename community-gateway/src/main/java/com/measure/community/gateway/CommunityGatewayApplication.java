package com.measure.community.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
public class CommunityGatewayApplication {

	public static void main(String[] args) {
		SpringApplication.run(CommunityGatewayApplication.class, args);
	}

}
