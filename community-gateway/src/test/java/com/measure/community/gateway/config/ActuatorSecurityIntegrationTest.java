package com.measure.community.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(
        classes = ActuatorSecurityIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.config.location=classpath:/actuator-security-test.yml"
        })
class ActuatorSecurityIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void healthIsPublic() {
        webTestClient.get().uri("/actuator/health")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void nonHealthActuatorPathsRequireAuthentication() {
        webTestClient.get().uri("/actuator/info")
                .exchange()
                .expectStatus().isUnauthorized();
        webTestClient.get().uri("/actuator")
                .exchange()
                .expectStatus().isUnauthorized();
        webTestClient.get().uri("/actuator/env")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void ordinaryRequestIsNotShortCircuitedBySecurity() {
        webTestClient.get().uri("/not-a-route")
                .exchange()
                .expectStatus().isNotFound();
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import(SecurityConfig.class)
    static class TestApplication {
    }
}
