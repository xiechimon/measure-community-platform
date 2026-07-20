package com.measure.community.auth.controller;

import com.measure.community.auth.model.vo.OrgDto;
import com.measure.community.auth.service.OrgService;
import com.measure.community.common.annotation.RequiresPermission;
import com.measure.community.common.enums.SystemStatus;
import com.measure.community.common.exception.BizException;
import com.measure.community.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class OrgControllerTest {

    @Mock
    OrgService orgService;

    @InjectMocks
    OrgController orgController;

    MockMvc mockMvc;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.standaloneSetup(orgController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void list_returnsOk() throws Exception {
        OrgDto dto = new OrgDto();
        dto.setId(1L);
        dto.setName("网格1004");
        dto.setType("GRID");
        dto.setPath("/1/");
        when(orgService.listOrgs()).thenReturn(List.of(dto));
        mockMvc.perform(get("/api/v1/auth/orgs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].name").value("网格1004"));
    }

    @Test
    void createOrg_returnsOk() throws Exception {
        when(orgService.createOrg(any())).thenReturn(9L);
        mockMvc.perform(post("/api/v1/auth/orgs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parentId\":1,\"type\":\"GRID\",\"name\":\"网格1004\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(9));
    }

    @Test
    void createOrg_parentNotFound_returns400() throws Exception {
        // 真实契约(见 OrgServiceImpl.createOrg / spec §3.2):父节点不存在为 BAD_REQUEST(400)
        when(orgService.createOrg(any()))
                .thenThrow(new BizException(SystemStatus.BAD_REQUEST, "父节点不存在"));
        mockMvc.perform(post("/api/v1/auth/orgs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parentId\":999,\"type\":\"GRID\",\"name\":\"网格1004\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(20001))
                .andExpect(jsonPath("$.message").value("父节点不存在"));
    }

    @Test
    void updateOrg_returnsOk() throws Exception {
        mockMvc.perform(put("/api/v1/auth/orgs/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"网格1005\",\"type\":\"GRID\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(orgService).updateOrg(eq(1L), any());
    }

    @Test
    void moveOrg_returnsOk() throws Exception {
        mockMvc.perform(put("/api/v1/auth/orgs/1/move")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newParentId\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(orgService).moveOrg(1L, 2L);
    }

    @Test
    void moveOrg_cycle_returns409() throws Exception {
        doThrow(new BizException(SystemStatus.CONFLICT, "不能移动到自身子树内"))
                .when(orgService).moveOrg(1L, 2L);
        mockMvc.perform(put("/api/v1/auth/orgs/1/move")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newParentId\":2}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(20004))
                .andExpect(jsonPath("$.message").value("不能移动到自身子树内"));
    }

    @Test
    void deleteOrg_returnsOk() throws Exception {
        mockMvc.perform(delete("/api/v1/auth/orgs/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(orgService).deleteOrg(1L);
    }

    @Test
    void deleteOrg_hasChildren_returns409() throws Exception {
        doThrow(new BizException(SystemStatus.CONFLICT, "存在子节点,不可删除"))
                .when(orgService).deleteOrg(1L);
        mockMvc.perform(delete("/api/v1/auth/orgs/1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(20004))
                .andExpect(jsonPath("$.message").value("存在子节点,不可删除"));
    }

    @Test
    void orgMethods_requireFunctionalPermissions() throws NoSuchMethodException {
        assertPermission("list", new Class<?>[]{}, "system:org:query");
        assertPermission("create",
                new Class<?>[]{com.measure.community.auth.model.req.OrgCreateReq.class}, "system:org:create");
        assertPermission("update",
                new Class<?>[]{Long.class, com.measure.community.auth.model.req.OrgUpdateReq.class},
                "system:org:update");
        assertPermission("move",
                new Class<?>[]{Long.class, com.measure.community.auth.model.req.OrgMoveReq.class},
                "system:org:move");
        assertPermission("delete", new Class<?>[]{Long.class}, "system:org:delete");
    }

    private static void assertPermission(String name, Class<?>[] types, String expected)
            throws NoSuchMethodException {
        RequiresPermission annotation = OrgController.class
                .getMethod(name, types).getAnnotation(RequiresPermission.class);
        assertNotNull(annotation);
        assertArrayEquals(new String[]{expected}, annotation.value());
    }
}
