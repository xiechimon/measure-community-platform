package com.measure.community.common.aspect;

import com.measure.community.common.annotation.RequiresPermission;
import com.measure.community.common.enums.SystemStatus;
import com.measure.community.common.exception.BizException;
import com.measure.community.common.utils.UserContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RequiresPermissionAspectTest {

    private final RequiresPermissionAspect aspect = new RequiresPermissionAspect();

    @AfterEach
    void clear() {
        UserContextHolder.clear();
    }

    private RequiresPermission rp(RequiresPermission.Logical logical, String... perms) {
        RequiresPermission a = mock(RequiresPermission.class);
        when(a.value()).thenReturn(perms);
        when(a.logical()).thenReturn(logical);
        return a;
    }

    @Test
    void allows_whenHasAllRequired_AND() {
        UserContextHolder.setPermissions(Set.of("population:query", "population:create"));
        assertDoesNotThrow(() -> aspect.check(rp(RequiresPermission.Logical.AND, "population:query")));
    }

    @Test
    void denies_whenMissingOne_AND() {
        UserContextHolder.setPermissions(Set.of("population:query"));
        BizException ex = assertThrows(BizException.class,
                () -> aspect.check(rp(RequiresPermission.Logical.AND, "population:query", "population:export")));
        assertEquals(SystemStatus.FORBIDDEN, ex.getErrorCode());
    }

    @Test
    void allows_whenHasAny_OR() {
        UserContextHolder.setPermissions(Set.of("population:query"));
        assertDoesNotThrow(() -> aspect.check(rp(RequiresPermission.Logical.OR, "population:export", "population:query")));
    }

    @Test
    void denies_whenNoContextPermissions() {
        BizException ex = assertThrows(BizException.class,
                () -> aspect.check(rp(RequiresPermission.Logical.AND, "system:user:query")));
        assertEquals(SystemStatus.FORBIDDEN, ex.getErrorCode());
    }
}
