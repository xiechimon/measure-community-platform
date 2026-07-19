package com.measure.community.auth.service.impl;

import com.measure.community.auth.mapper.SysPermissionMapper;
import com.measure.community.auth.mapper.SysRoleMapper;
import com.measure.community.auth.mapper.SysUserMapper;
import com.measure.community.auth.model.entity.SysPermission;
import com.measure.community.auth.model.entity.SysRole;
import com.measure.community.auth.model.entity.SysUser;
import com.measure.community.auth.model.req.RoleCreateReq;
import com.measure.community.auth.model.req.RoleQueryReq;
import com.measure.community.auth.model.req.RoleUpdateReq;
import com.measure.community.auth.model.vo.RolePageDto;
import com.measure.community.common.enums.SystemStatus;
import com.measure.community.common.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RoleServiceImplTest {

    private SysRoleMapper roleMapper;
    private SysUserMapper userMapper;
    private SysPermissionMapper permissionMapper;
    private RoleServiceImpl svc;

    @BeforeEach
    void setUp() {
        roleMapper = mock(SysRoleMapper.class);
        userMapper = mock(SysUserMapper.class);
        permissionMapper = mock(SysPermissionMapper.class);
        svc = new RoleServiceImpl();
        ReflectionTestUtils.setField(svc, "baseMapper", roleMapper);
        ReflectionTestUtils.setField(svc, "sysUserMapper", userMapper);
        ReflectionTestUtils.setField(svc, "sysPermissionMapper", permissionMapper);
    }

    @Test
    void createRejectsInvalidDataScope() {
        RoleCreateReq r = new RoleCreateReq();
        r.setCode("x");
        r.setName("X");
        r.setDataScope("CUSTOM");

        BizException ex = assertThrows(BizException.class, () -> svc.createRole(r));
        assertEquals(SystemStatus.BAD_REQUEST, ex.getErrorCode());
    }

    @Test
    void createRejectsGarbageDataScope() {
        RoleCreateReq r = new RoleCreateReq();
        r.setCode("x");
        r.setName("X");
        r.setDataScope("NOT_A_SCOPE");

        BizException ex = assertThrows(BizException.class, () -> svc.createRole(r));
        assertEquals(SystemStatus.BAD_REQUEST, ex.getErrorCode());
    }

    @Test
    void createRejectsDuplicateCode() {
        when(roleMapper.selectCount(any())).thenReturn(1L);

        RoleCreateReq r = new RoleCreateReq();
        r.setCode("dup");
        r.setName("D");
        r.setDataScope("GRID");

        BizException ex = assertThrows(BizException.class, () -> svc.createRole(r));
        assertEquals(SystemStatus.CONFLICT, ex.getErrorCode());
    }

    @Test
    void createSucceedsAndReturnsId() {
        when(roleMapper.selectCount(any())).thenReturn(0L);
        when(roleMapper.insert(any(SysRole.class))).thenAnswer(inv -> {
            SysRole role = inv.getArgument(0);
            role.setId(42L);
            return 1;
        });

        RoleCreateReq r = new RoleCreateReq();
        r.setCode("grid_worker");
        r.setName("网格员");
        r.setDataScope("GRID");

        Long id = svc.createRole(r);
        assertEquals(42L, id);
    }

    @Test
    void updateRejectsWhenRoleNotFound() {
        when(roleMapper.selectById(1L)).thenReturn(null);

        RoleUpdateReq r = new RoleUpdateReq();
        r.setName("X");
        r.setDataScope("GRID");

        BizException ex = assertThrows(BizException.class, () -> svc.updateRole(1L, r));
        assertEquals(SystemStatus.NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void updateRejectsInvalidDataScope() {
        SysRole existing = new SysRole();
        existing.setId(1L);
        existing.setCode("grid_worker");
        existing.setName("网格员");
        existing.setDataScope("GRID");
        when(roleMapper.selectById(1L)).thenReturn(existing);

        RoleUpdateReq r = new RoleUpdateReq();
        r.setName("X");
        r.setDataScope("CUSTOM");

        BizException ex = assertThrows(BizException.class, () -> svc.updateRole(1L, r));
        assertEquals(SystemStatus.BAD_REQUEST, ex.getErrorCode());
    }

    @Test
    void updateSetsOnlyNameAndDataScopeNotCode() {
        SysRole existing = new SysRole();
        existing.setId(1L);
        existing.setCode("grid_worker");
        existing.setName("旧名称");
        existing.setDataScope("SELF");
        when(roleMapper.selectById(1L)).thenReturn(existing);
        when(roleMapper.updateById(any(SysRole.class))).thenReturn(1);

        RoleUpdateReq r = new RoleUpdateReq();
        r.setName("新名称");
        r.setDataScope("COMMUNITY");

        svc.updateRole(1L, r);

        assertEquals("grid_worker", existing.getCode());
        assertEquals("新名称", existing.getName());
        assertEquals("COMMUNITY", existing.getDataScope());
    }

    @Test
    void deleteRejectsAdminRole() {
        SysRole admin = new SysRole();
        admin.setId(1L);
        admin.setCode("admin");
        when(roleMapper.selectById(1L)).thenReturn(admin);

        assertThrows(BizException.class, () -> svc.deleteRole(1L));
    }

    @Test
    void deleteRejectsWhenRoleNotFound() {
        when(roleMapper.selectById(2L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> svc.deleteRole(2L));
        assertEquals(SystemStatus.NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void deleteRemovesNonAdminRole() {
        SysRole role = new SysRole();
        role.setId(2L);
        role.setCode("grid_worker");
        when(roleMapper.selectById(2L)).thenReturn(role);
        when(roleMapper.deleteById(eq(2L))).thenReturn(1);

        assertDoesNotThrow(() -> svc.deleteRole(2L));
        verify(roleMapper).deleteRolePermissions(2L); // 级联清理角色↔权限链接
    }

    @Test
    void pageRoolsReturnsPageDto() {
        SysRole role = new SysRole();
        role.setId(1L);
        role.setCode("grid_worker");
        role.setName("网格员");
        role.setDataScope("GRID");
        when(roleMapper.selectPage(any(), any())).thenAnswer(inv -> {
            com.baomidou.mybatisplus.extension.plugins.pagination.Page<SysRole> page = inv.getArgument(0);
            page.setRecords(java.util.List.of(role));
            page.setTotal(1L);
            return page;
        });

        RoleQueryReq req = new RoleQueryReq();
        req.setPage(1L);
        req.setSize(10L);

        RolePageDto dto = svc.pageRoles(req);

        assertEquals(1L, dto.getTotal());
        assertEquals(1, dto.getRecords().size());
        assertEquals("grid_worker", dto.getRecords().get(0).getCode());
    }

    @Test
    void assignPermissionsRejectsWhenRoleNotFound() {
        when(roleMapper.selectById(2L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class,
                () -> svc.assignPermissions(2L, java.util.List.of(1L, 2L)));
        assertEquals(SystemStatus.NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void assignPermissionsRejectsWhenSomePermissionIdMissing() {
        SysRole r = new SysRole();
        r.setId(2L);
        r.setCode("x");
        when(roleMapper.selectById(2L)).thenReturn(r);
        SysPermission p1 = new SysPermission();
        p1.setId(1L);
        when(permissionMapper.selectByIds(java.util.List.of(1L, 2L))).thenReturn(java.util.List.of(p1));

        BizException ex = assertThrows(BizException.class,
                () -> svc.assignPermissions(2L, java.util.List.of(1L, 2L)));
        assertEquals(SystemStatus.BAD_REQUEST, ex.getErrorCode());
    }

    @Test
    void assignPermissionsReplacesSet() {
        SysRole r = new SysRole();
        r.setId(2L);
        r.setCode("x");
        when(roleMapper.selectById(2L)).thenReturn(r);
        SysPermission p1 = new SysPermission();
        p1.setId(1L);
        SysPermission p2 = new SysPermission();
        p2.setId(2L);
        when(permissionMapper.selectByIds(java.util.List.of(1L, 2L))).thenReturn(java.util.List.of(p1, p2));

        svc.assignPermissions(2L, java.util.List.of(1L, 2L));

        verify(roleMapper).deleteRolePermissions(2L);
        verify(roleMapper).insertRolePermission(2L, 1L);
        verify(roleMapper).insertRolePermission(2L, 2L);
    }

    @Test
    void assignPermissionsWithEmptyListOnlyClears() {
        SysRole r = new SysRole();
        r.setId(2L);
        r.setCode("x");
        when(roleMapper.selectById(2L)).thenReturn(r);

        svc.assignPermissions(2L, java.util.List.of());

        verify(roleMapper).deleteRolePermissions(2L);
        verify(roleMapper, never()).insertRolePermission(any(), any());
    }

    @Test
    void assignRolesRejectsWhenUserNotFound() {
        when(userMapper.selectById(999L)).thenReturn(null);
        BizException ex = assertThrows(BizException.class,
                () -> svc.assignRoles(999L, java.util.List.of(1L)));
        assertEquals(SystemStatus.NOT_FOUND, ex.getErrorCode());
        verify(userMapper, never()).deleteUserRoles(any());
    }

    @Test
    void assignRolesRejectsWhenSomeRoleIdMissing() {
        when(userMapper.selectById(10L)).thenReturn(new SysUser());
        SysRole r1 = new SysRole();
        r1.setId(1L);
        when(roleMapper.selectByIds(java.util.List.of(1L, 2L))).thenReturn(java.util.List.of(r1));

        BizException ex = assertThrows(BizException.class,
                () -> svc.assignRoles(10L, java.util.List.of(1L, 2L)));
        assertEquals(SystemStatus.BAD_REQUEST, ex.getErrorCode());
    }

    @Test
    void assignRolesReplacesSet() {
        when(userMapper.selectById(10L)).thenReturn(new SysUser());
        SysRole r1 = new SysRole();
        r1.setId(1L);
        SysRole r2 = new SysRole();
        r2.setId(2L);
        when(roleMapper.selectByIds(java.util.List.of(1L, 2L))).thenReturn(java.util.List.of(r1, r2));

        svc.assignRoles(10L, java.util.List.of(1L, 2L));

        verify(userMapper).deleteUserRoles(10L);
        verify(userMapper).insertUserRole(10L, 1L);
        verify(userMapper).insertUserRole(10L, 2L);
    }

    @Test
    void assignRolesWithEmptyListOnlyClears() {
        when(userMapper.selectById(10L)).thenReturn(new SysUser());
        svc.assignRoles(10L, java.util.List.of());

        verify(userMapper).deleteUserRoles(10L);
        verify(userMapper, never()).insertUserRole(any(), any());
    }

    @Test
    void deleteRejectsBoundRole() {
        SysRole r = new SysRole();
        r.setId(2L);
        r.setCode("x");
        when(roleMapper.selectById(2L)).thenReturn(r);
        when(userMapper.countUsersByRole(2L)).thenReturn(3L);

        BizException ex = assertThrows(BizException.class, () -> svc.deleteRole(2L));
        assertEquals(SystemStatus.CONFLICT, ex.getErrorCode());
    }

    @Test
    void deleteAllowsUnboundRole() {
        SysRole role = new SysRole();
        role.setId(2L);
        role.setCode("grid_worker");
        when(roleMapper.selectById(2L)).thenReturn(role);
        when(userMapper.countUsersByRole(2L)).thenReturn(0L);
        when(roleMapper.deleteById(eq(2L))).thenReturn(1);

        assertDoesNotThrow(() -> svc.deleteRole(2L));
    }
}
