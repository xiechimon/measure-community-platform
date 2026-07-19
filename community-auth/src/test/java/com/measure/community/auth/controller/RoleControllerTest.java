package com.measure.community.auth.controller;

import com.measure.community.auth.model.vo.PermissionDto;
import com.measure.community.auth.model.vo.RolePageDto;
import com.measure.community.auth.service.RoleService;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
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
class RoleControllerTest {

    @Mock
    RoleService roleService;

    @InjectMocks
    RoleController roleController;

    MockMvc mockMvc;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.standaloneSetup(roleController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void pageRoles_returnsOk() throws Exception {
        when(roleService.pageRoles(any())).thenReturn(new RolePageDto());
        mockMvc.perform(get("/api/v1/auth/roles").param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void listPermissions_returnsOk() throws Exception {
        PermissionDto dto = new PermissionDto();
        dto.setId(1L);
        dto.setCode("system:role:query");
        dto.setName("角色查询");
        dto.setType("api");
        when(roleService.listPermissions()).thenReturn(List.of(dto));
        mockMvc.perform(get("/api/v1/auth/permissions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].code").value("system:role:query"));
    }

    @Test
    void createRole_returnsOk() throws Exception {
        when(roleService.createRole(any())).thenReturn(9L);
        mockMvc.perform(post("/api/v1/auth/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"r\",\"name\":\"R\",\"dataScope\":\"GRID\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(9));
    }

    @Test
    void createRole_conflict_returns409() throws Exception {
        when(roleService.createRole(any()))
                .thenThrow(new BizException(SystemStatus.CONFLICT, "角色标识已存在"));
        mockMvc.perform(post("/api/v1/auth/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"admin\",\"name\":\"R\",\"dataScope\":\"GRID\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(20004))
                .andExpect(jsonPath("$.message").value("角色标识已存在"));
    }

    @Test
    void updateRole_returnsOk() throws Exception {
        mockMvc.perform(put("/api/v1/auth/roles/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"R2\",\"dataScope\":\"GRID\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(roleService).updateRole(eq(1L), any());
    }

    @Test
    void deleteRole_returnsOk() throws Exception {
        mockMvc.perform(delete("/api/v1/auth/roles/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(roleService).deleteRole(1L);
    }

    @Test
    void deleteRole_adminForbidden_returns409() throws Exception {
        doThrow(new BizException(SystemStatus.CONFLICT, "超级管理员角色不可删除"))
                .when(roleService).deleteRole(1L);
        mockMvc.perform(delete("/api/v1/auth/roles/1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(20004))
                .andExpect(jsonPath("$.message").value("超级管理员角色不可删除"));
    }

    @Test
    void assignPermissions_returnsOk() throws Exception {
        mockMvc.perform(put("/api/v1/auth/roles/1/permissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissionIds\":[1,2]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(roleService).assignPermissions(eq(1L), anyList());
    }

    @Test
    void assignRoles_returnsOk() throws Exception {
        mockMvc.perform(put("/api/v1/auth/users/5/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleIds\":[1,2]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(roleService).assignRoles(eq(5L), anyList());
    }

    @Test
    void roleMethods_requireFunctionalPermissions() throws NoSuchMethodException {
        assertPermission("pageRoles",
                new Class<?>[]{com.measure.community.auth.model.req.RoleQueryReq.class}, "system:role:query");
        assertPermission("listPermissions", new Class<?>[]{}, "system:role:query");
        assertPermission("createRole",
                new Class<?>[]{com.measure.community.auth.model.req.RoleCreateReq.class}, "system:role:create");
        assertPermission("updateRole",
                new Class<?>[]{Long.class, com.measure.community.auth.model.req.RoleUpdateReq.class},
                "system:role:update");
        assertPermission("deleteRole", new Class<?>[]{Long.class}, "system:role:delete");
        assertPermission("assignPermissions",
                new Class<?>[]{Long.class, com.measure.community.auth.model.req.AssignPermissionsReq.class},
                "system:role:assign");
        assertPermission("assignRoles",
                new Class<?>[]{Long.class, com.measure.community.auth.model.req.AssignRolesReq.class},
                "system:role:assign");
    }

    private static void assertPermission(String name, Class<?>[] types, String expected)
            throws NoSuchMethodException {
        RequiresPermission annotation = RoleController.class
                .getMethod(name, types).getAnnotation(RequiresPermission.class);
        assertNotNull(annotation);
        assertArrayEquals(new String[]{expected}, annotation.value());
    }
}
