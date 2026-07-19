package com.measure.community.auth.service.impl;

import com.measure.community.auth.mapper.SysOrgMapper;
import com.measure.community.auth.model.entity.SysOrg;
import com.measure.community.auth.model.req.OrgCreateReq;
import com.measure.community.common.enums.SystemStatus;
import com.measure.community.common.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrgServiceImplTest {

    private SysOrgMapper orgMapper;
    private OrgServiceImpl svc;

    @BeforeEach
    void setUp() {
        orgMapper = mock(SysOrgMapper.class);
        svc = new OrgServiceImpl();
        ReflectionTestUtils.setField(svc, "baseMapper", orgMapper);
    }

    @Test
    void createRejectsInvalidType() {
        OrgCreateReq r = new OrgCreateReq();
        r.setName("x");
        r.setType("BOGUS");
        assertEquals(SystemStatus.BAD_REQUEST, assertThrows(BizException.class, () -> svc.createOrg(r)).getErrorCode());
    }

    @Test
    void createRejectsNullType() {
        OrgCreateReq r = new OrgCreateReq();
        r.setName("x"); // type 为 null
        assertEquals(SystemStatus.BAD_REQUEST, assertThrows(BizException.class, () -> svc.createOrg(r)).getErrorCode());
    }

    @Test
    void createRejectsMissingParent() {
        OrgCreateReq r = new OrgCreateReq();
        r.setName("x");
        r.setType("GRID");
        r.setParentId(99L);
        when(orgMapper.selectById(99L)).thenReturn(null);
        assertEquals(SystemStatus.BAD_REQUEST, assertThrows(BizException.class, () -> svc.createOrg(r)).getErrorCode());
    }

    @Test
    void createComputesPathFromParent() {
        OrgCreateReq r = new OrgCreateReq();
        r.setName("网格X");
        r.setType("GRID");
        r.setParentId(10L);
        SysOrg parent = new SysOrg();
        parent.setId(10L);
        parent.setPath("/1/5/10/");
        when(orgMapper.selectById(10L)).thenReturn(parent);
        // insert 回填 id(模拟 MyBatis 行为:save 后实体有 id)
        when(orgMapper.insert(any(SysOrg.class))).thenAnswer(inv -> {
            ((SysOrg) inv.getArgument(0)).setId(1004L);
            return 1;
        });
        svc.createOrg(r);
        verify(orgMapper).updateById(argThat((SysOrg o) -> "/1/5/10/1004/".equals(o.getPath())));
    }
}
