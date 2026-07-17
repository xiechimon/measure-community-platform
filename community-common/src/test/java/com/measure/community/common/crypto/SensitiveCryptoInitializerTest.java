package com.measure.community.common.crypto;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SensitiveCryptoInitializerTest {

    @Test
    void prodMissingEitherKeyFailsFast() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");

        assertThrows(IllegalStateException.class,
                () -> new SensitiveCryptoInitializer("", "", env));
    }

    @Test
    void devMissingKeysKeepsFallback() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("dev");

        assertDoesNotThrow(() -> new SensitiveCryptoInitializer("", "", env));
    }
}
