package com.measure.community.common.config;

import com.measure.community.common.constant.CommonConstant;
import feign.RequestTemplate;
import org.junit.jupiter.api.Test;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FeignConfigTest {

    @Test
    void configuredSecretIsAddedToInternalAuthHeader() {
        RequestTemplate template = new RequestTemplate();

        new FeignConfig("unit-test-internal-secret").apply(template);

        Collection<String> headerValues = template.headers().get(CommonConstant.X_INTERNAL_AUTH);
        assertEquals(java.util.List.of("unit-test-internal-secret"), java.util.List.copyOf(headerValues));
    }

    @Test
    void configuredSecretReplacesExistingInternalAuthHeader() {
        RequestTemplate template = new RequestTemplate();
        template.header(CommonConstant.X_INTERNAL_AUTH, "spoofed-secret");

        new FeignConfig("unit-test-internal-secret").apply(template);

        Collection<String> headerValues = template.headers().get(CommonConstant.X_INTERNAL_AUTH);
        assertEquals(java.util.List.of("unit-test-internal-secret"), java.util.List.copyOf(headerValues));
    }

    @Test
    void blankConfiguredSecretFailsFast() {
        assertThrows(IllegalStateException.class, () -> new FeignConfig(" "));
    }
}
