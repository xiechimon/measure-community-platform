package com.measure.community.common.model;

import com.measure.community.common.enums.SystemStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RetObjTest {

    @Test
    void success_hasCode200() {
        RetObj<String> r = RetObj.success("x");
        assertEquals(200, r.getCode());
        assertEquals("请求成功", r.getMessage());
        assertEquals("x", r.getData());
    }

    @Test
    void errorWithStatusAndMessage_usesStatusCodeAndCustomMessage() {
        RetObj<?> r = RetObj.error(SystemStatus.CONFLICT, "该证件号已存在");
        assertEquals(20004, r.getCode());
        assertEquals("该证件号已存在", r.getMessage());
    }

    @Test
    void errorWithString_defaultsToInternalError() {
        RetObj<?> r = RetObj.error("boom");
        assertEquals(50000, r.getCode());
        assertEquals("boom", r.getMessage());
    }
}
