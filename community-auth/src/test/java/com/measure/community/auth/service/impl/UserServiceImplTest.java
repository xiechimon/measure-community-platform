package com.measure.community.auth.service.impl;

import com.measure.community.auth.mapper.SysUserMapper;
import com.measure.community.auth.model.entity.SysUser;
import com.measure.community.auth.model.req.LoginInfoReq;
import com.measure.community.common.enums.SystemStatus;
import com.measure.community.common.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserServiceImplTest {

    private UserServiceImpl service;
    private SysUserMapper sysUserMapper;

    @BeforeEach
    void setup() {
        service = new UserServiceImpl();
        sysUserMapper = mock(SysUserMapper.class);
        ReflectionTestUtils.setField(service, "baseMapper", sysUserMapper);
        ReflectionTestUtils.setField(service, "redisTemplate", mock(RedisTemplate.class));
    }

    @Test
    void login_withUnknownAccount_throwsUnauthorized() {
        when(sysUserMapper.selectOne(ArgumentMatchers.any(), ArgumentMatchers.anyBoolean())).thenReturn(null);

        LoginInfoReq req = new LoginInfoReq();
        req.setAccount("admin");
        req.setPassword("bad");

        BizException ex = assertThrows(BizException.class, () -> service.login(req));

        assertEquals(SystemStatus.UNAUTHORIZED, ex.getErrorCode());
    }

    @Test
    void login_withDisabledAccount_throwsForbidden() {
        SysUser user = new SysUser();
        user.setUsername("admin");
        user.setPassword("$2a$10$Qh1nRuS5l9/6zRVs0IfrROck5tzRWTudrvZfXDTK7saT6MrSG3JV.");
        user.setStatus(0);
        when(sysUserMapper.selectOne(ArgumentMatchers.any(), ArgumentMatchers.anyBoolean())).thenReturn(user);

        LoginInfoReq req = new LoginInfoReq();
        req.setAccount("admin");
        req.setPassword("password");

        BizException ex = assertThrows(BizException.class, () -> service.login(req));

        assertEquals(SystemStatus.FORBIDDEN, ex.getErrorCode());
    }
}
