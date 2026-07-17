package com.measure.community.auth.utils;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtConfigTest {

    @Test
    void prodMissingSecretFailsFast() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");

        assertThrows(IllegalStateException.class, () -> new JwtConfig("", 7200, env));
    }

    @Test
    void devMissingSecretKeepsFallback() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("dev");

        assertDoesNotThrow(() -> new JwtConfig("", 7200, env));
    }
}
