package com.measure.community.common.config;

import com.measure.community.common.utils.UserContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MybatisPlusConfigTest {

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    void noUser_fallsBackToSystem() {
        UserContextHolder.clear();
        assertEquals("system", MybatisPlusConfig.currentAuditUser());
    }

    @Test
    void withUser_returnsUserId() {
        Map<String, String> user = new HashMap<>();
        user.put("id", "42");
        UserContextHolder.set(user);
        assertEquals("42", MybatisPlusConfig.currentAuditUser());
    }
}
