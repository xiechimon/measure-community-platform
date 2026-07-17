package com.measure.community.common.utils;

import com.measure.community.common.enums.SystemStatus;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResponseWriterTest {

    @Test
    void writeError_setsStatusAndJsonBody() throws Exception {
        MockHttpServletResponse resp = new MockHttpServletResponse();
        ResponseWriter.writeError(resp, SystemStatus.FORBIDDEN);
        assertEquals(403, resp.getStatus());
        assertTrue(resp.getContentType().contains("application/json"));
        String body = resp.getContentAsString();
        assertTrue(body.contains("\"code\":10002"), body);
        assertTrue(body.contains("无权限访问"), body);
    }
}
