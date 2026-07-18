package com.measure.community.common.utils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class UserContextHolderScopeTest {
    @AfterEach
    void tearDown() { UserContextHolder.clear(); }

    @Test
    void exposesOrgGridScope() {
        UserContextHolder.set(Map.of("id", "7", "orgId", "10", "gridId", "1001", "dataScope", "GRID"));
        assertEquals(10L, UserContextHolder.getOrgId());
        assertEquals(1001L, UserContextHolder.getGridId());
        assertEquals("GRID", UserContextHolder.getDataScope());
    }
    @Test
    void missingScopeDefaultsSelfAndNullIds() {
        UserContextHolder.set(Map.of("id", "7"));
        assertNull(UserContextHolder.getOrgId());
        assertNull(UserContextHolder.getGridId());
        assertEquals("SELF", UserContextHolder.getDataScope());
    }
    @Test
    void noContextIsNullSafe() {
        assertNull(UserContextHolder.getGridId());
        assertEquals("SELF", UserContextHolder.getDataScope());
    }
}
