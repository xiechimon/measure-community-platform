package com.measure.community.common.utils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserContextHolderTest {

    @AfterEach
    void clear() {
        UserContextHolder.clear();
    }

    @Test
    void permissions_setGetHas() {
        UserContextHolder.setPermissions(Set.of("population:query"));
        assertTrue(UserContextHolder.hasPermission("population:query"));
        assertFalse(UserContextHolder.hasPermission("population:export"));
    }

    @Test
    void unset_returnsEmpty_notNull() {
        assertTrue(UserContextHolder.getPermissions().isEmpty());
        assertTrue(UserContextHolder.getRoles().isEmpty());
        assertFalse(UserContextHolder.hasPermission("x"));
    }

    @Test
    void clear_removesAll() {
        UserContextHolder.setRoles(Set.of("admin"));
        UserContextHolder.setPermissions(Set.of("a"));
        UserContextHolder.clear();
        assertTrue(UserContextHolder.getRoles().isEmpty());
        assertTrue(UserContextHolder.getPermissions().isEmpty());
    }
}
