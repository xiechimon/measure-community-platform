package com.measure.community.common.exception;

import com.measure.community.common.enums.SystemStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BizExceptionTest {

    @Test
    void carriesStatusAndCustomMessage() {
        BizException e = new BizException(SystemStatus.CONFLICT, "该证件号已存在");
        assertEquals(SystemStatus.CONFLICT, e.getStatus());
        assertEquals("该证件号已存在", e.getMessage());
    }

    @Test
    void statusOnly_usesStatusDefaultMessage() {
        BizException e = new BizException(SystemStatus.FORBIDDEN);
        assertEquals(SystemStatus.FORBIDDEN, e.getStatus());
        assertEquals("无权限访问", e.getMessage());
    }
}
