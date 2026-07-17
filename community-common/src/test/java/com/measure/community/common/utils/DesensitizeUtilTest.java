package com.measure.community.common.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DesensitizeUtilTest {

    @Test
    void idCard_keepsFirst6Last4() {
        assertEquals("330106********1234", DesensitizeUtil.idCard("330106199001011234"));
        assertNull(DesensitizeUtil.idCard(null));
        assertEquals("****", DesensitizeUtil.idCard("12345"));
    }

    @Test
    void phone_keepsFirst3Last4() {
        assertEquals("138****1111", DesensitizeUtil.phone("13800001111"));
        assertNull(DesensitizeUtil.phone(null));
    }

    @Test
    void bankCard_keepsLast4() {
        assertEquals("************3456", DesensitizeUtil.bankCard("6222021234563456"));
    }
}
