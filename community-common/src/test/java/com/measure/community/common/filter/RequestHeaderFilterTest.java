package com.measure.community.common.filter;

import com.measure.community.common.constant.CommonConstant;
import com.measure.community.common.utils.UserContextHolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.containsString;

class RequestHeaderFilterTest {

    @RestController
    static class PingController {
        @GetMapping("/ping")
        public String ping() {
            return "pong";
        }

        /** 回显当前上下文权限,用于验证 X-UserInfo 解析传播 */
        @GetMapping("/whoami")
        public String whoami() {
            return "perms=" + String.join(",", UserContextHolder.getPermissions())
                    + ";roles=" + String.join(",", UserContextHolder.getRoles());
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

    @Test
    void xUserInfo_rolesAndPermissions_propagateToContext() throws Exception {
        String userJson = "{\"id\":\"1\",\"name\":\"admin\","
                + "\"roles\":[\"admin\"],"
                + "\"permissions\":[\"system:user:query\",\"population:query\"]}";
        String b64 = Base64.getEncoder().encodeToString(userJson.getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(get("/whoami")
                        .header(CommonConstant.X_INTERNAL_AUTH, CommonConstant.SECRET_KEY)
                        .header(CommonConstant.X_USERINFO, b64))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("system:user:query")))
                .andExpect(content().string(containsString("roles=admin")));
    }
}
