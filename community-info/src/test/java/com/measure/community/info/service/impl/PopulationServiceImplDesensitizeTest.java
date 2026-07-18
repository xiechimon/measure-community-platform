package com.measure.community.info.service.impl;

import com.measure.community.common.utils.UserContextHolder;
import com.measure.community.info.model.entity.Population;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class PopulationServiceImplDesensitizeTest {
    @AfterEach
    void tearDown() { UserContextHolder.clear(); }

    private Population sample() {
        Population p = new Population();
        p.setId(1L); p.setName("张三"); p.setIdCard("330106199001011234"); p.setPhone("13800138000");
        return p;
    }

    @Test
    void unmaskTrueReturnsPlaintext() {
        PopulationServiceImpl svc = new PopulationServiceImpl();
        Object dto = ReflectionTestUtils.invokeMethod(svc, "toDto", sample(), true);
        assertEquals("330106199001011234", ReflectionTestUtils.getField(dto, "idCard"));
        assertEquals("13800138000", ReflectionTestUtils.getField(dto, "phone"));
    }

    @Test
    void unmaskFalseMasks() {
        PopulationServiceImpl svc = new PopulationServiceImpl();
        Object dto = ReflectionTestUtils.invokeMethod(svc, "toDto", sample(), false);
        assertNotEquals("330106199001011234", ReflectionTestUtils.getField(dto, "idCard"));
        assertTrue(((String) ReflectionTestUtils.getField(dto, "idCard")).contains("*"));
    }
}
