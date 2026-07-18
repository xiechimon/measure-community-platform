package com.measure.community.auth.service.impl;

import com.measure.community.auth.mapper.SysUserMapper;
import com.measure.community.auth.model.entity.SysUser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class UserServiceImplScopeTest {
    @Test
    void loginUserCarriesBroadestScopeAndOrgGrid() {
        SysUserMapper mapper = mock(SysUserMapper.class);
        SysUser u = new SysUser();
        u.setId(7L); u.setUsername("gridA"); u.setName("网格A"); u.setOrgId(10L); u.setGridId(1001L);
        when(mapper.selectRoleDataScopes(7L)).thenReturn(List.of("SELF", "GRID"));
        // buildLoginUser 是从 SysUser + mapper 组装 LoginUser 的方法（本任务提取/复用）
        UserServiceImpl svc = new UserServiceImpl();
        org.springframework.test.util.ReflectionTestUtils.setField(svc, "baseMapper", mapper);
        when(mapper.selectRoleCodes(7L)).thenReturn(List.of("gridOfficer"));
        when(mapper.selectPermissionCodes(7L)).thenReturn(List.of("population:query"));

        var lu = svc.buildLoginUser(u);

        assertEquals(10L, lu.getOrgId());
        assertEquals(1001L, lu.getGridId());
        assertEquals("GRID", lu.getDataScope());
    }
}
