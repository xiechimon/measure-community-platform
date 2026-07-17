package com.measure.community.common.filter;

import com.measure.community.common.constant.CommonConstant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RequestHeaderFilterTest {

    @RestController
    static class PingController {
        @GetMapping("/ping")
        public String ping() {
            return "pong";
        }
    }

    MockMvc mockMvc;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.standaloneSetup(new PingController())
                .addFilters(new RequestHeaderFilter())
                .build();
    }

    @Test
    void missingInternalAuth_returns403AndRetObj() throws Exception {
        mockMvc.perform(get("/ping"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(10002));
    }

    @Test
    void correctInternalAuth_passesThrough() throws Exception {
        mockMvc.perform(get("/ping")
                        .header(CommonConstant.X_INTERNAL_AUTH, CommonConstant.SECRET_KEY))
                .andExpect(status().isOk());
    }
}
