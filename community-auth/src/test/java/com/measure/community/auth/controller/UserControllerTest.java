package com.measure.community.auth.controller;

import com.measure.community.auth.model.req.LoginInfoReq;
import com.measure.community.auth.model.vo.LoginUser;
import com.measure.community.auth.service.UserService;
import com.measure.community.common.enums.SystemStatus;
import com.measure.community.common.exception.BizException;
import com.measure.community.common.exception.GlobalExceptionHandler;
import com.measure.community.common.model.RetObj;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 沿用项目约定:Mockito + MockMvc standaloneSetup,不加载 Spring 上下文、不连 Nacos/DB。
 */
@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    UserService userService;

    @InjectMocks
    UserController userController;

    MockMvc mockMvc;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.standaloneSetup(userController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void login_success_returnsTokenData() throws Exception {
        LoginUser lu = new LoginUser();
        lu.setId(1L);
        lu.setAccount("admin");
        lu.setName("管理员");
        lu.setToken("tk-123");
        when(userService.login(any(LoginInfoReq.class))).thenReturn(RetObj.success(lu));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"account\":\"admin\",\"password\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.token").value("tk-123"))
                .andExpect(jsonPath("$.data.account").value("admin"));
    }

    @Test
    void login_badCredentials_returnsUnauthorized() throws Exception {
        when(userService.login(any(LoginInfoReq.class)))
                .thenThrow(new BizException(SystemStatus.UNAUTHORIZED, "账号或密码错误"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"account\":\"admin\",\"password\":\"bad\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(10001))
                .andExpect(jsonPath("$.message").value("账号或密码错误"));
    }

    @Test
    void login_disabledAccount_returnsForbidden() throws Exception {
        when(userService.login(any(LoginInfoReq.class)))
                .thenThrow(new BizException(SystemStatus.FORBIDDEN, "账号已停用"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"account\":\"admin\",\"password\":\"correct\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(10002))
                .andExpect(jsonPath("$.message").value("账号已停用"));
    }

    @Test
    void getUserName_returnsOk() throws Exception {
        mockMvc.perform(get("/api/v1/auth/getUserName"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }
}
