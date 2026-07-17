package com.measure.community.info.controller;

import com.measure.community.common.enums.SystemStatus;
import com.measure.community.common.exception.BizException;
import com.measure.community.common.exception.GlobalExceptionHandler;
import com.measure.community.common.annotation.RequiresPermission;
import com.measure.community.info.api.model.PopulationCreateReqDto;
import com.measure.community.info.api.model.PopulationHisPageDto;
import com.measure.community.info.api.model.PopulationPageDto;
import com.measure.community.info.api.model.PopulationVersionUpdateReqDto;
import com.measure.community.info.model.req.PopulationQueryReq;
import com.measure.community.info.service.PopulationService;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PopulationControllerTest {

    @Mock
    PopulationService populationService;

    @InjectMocks
    PopulationController populationController;

    MockMvc mockMvc;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.standaloneSetup(populationController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void listPersons_returnsOk() throws Exception {
        when(populationService.pagePersons(any(PopulationQueryReq.class)))
                .thenReturn(new PopulationPageDto());
        mockMvc.perform(get("/api/v1/population/persons").param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void createPerson_returnsOk() throws Exception {
        when(populationService.createPerson(any(PopulationCreateReqDto.class)))
                .thenReturn(1L);
        mockMvc.perform(post("/api/v1/population/persons")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"张三\",\"idCard\":\"3301X\",\"type\":\"户籍\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(1));
    }

    @Test
    void createPerson_duplicate_returns409() throws Exception {
        when(populationService.createPerson(any(PopulationCreateReqDto.class)))
                .thenThrow(new BizException(SystemStatus.CONFLICT, "该证件号已存在"));
        mockMvc.perform(post("/api/v1/population/persons")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"张三\",\"idCard\":\"3301X\",\"type\":\"户籍\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(20004))
                .andExpect(jsonPath("$.message").value("该证件号已存在"));
    }

    @Test
    void updatePersonVersion_returnsNewVersion() throws Exception {
        when(populationService.updateVersion(eq(1L), any(PopulationVersionUpdateReqDto.class)))
                .thenReturn(2);
        mockMvc.perform(post("/api/v1/population/persons/1/versions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"李四\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(2));
    }

    @Test
    void updatePersonVersion_notFound_returns404() throws Exception {
        when(populationService.updateVersion(eq(9L), any(PopulationVersionUpdateReqDto.class)))
                .thenThrow(new BizException(SystemStatus.NOT_FOUND, "人口档案不存在"));
        mockMvc.perform(post("/api/v1/population/persons/9/versions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"李四\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(20002))
                .andExpect(jsonPath("$.message").value("人口档案不存在"));
    }

    @Test
    void listPersonVersions_returnsOk() throws Exception {
        when(populationService.listVersions(anyLong(), anyLong(), anyLong()))
                .thenReturn(new PopulationHisPageDto());
        mockMvc.perform(get("/api/v1/population/persons/1/versions").param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void populationMethods_requireFunctionalPermissions() throws NoSuchMethodException {
        assertPermission("listPersons", new Class<?>[]{PopulationQueryReq.class}, "population:query");
        assertPermission("createPerson", new Class<?>[]{PopulationCreateReqDto.class}, "population:create");
        assertPermission("updatePersonVersion",
                new Class<?>[]{Long.class, PopulationVersionUpdateReqDto.class}, "population:update");
        assertPermission("listPersonVersions",
                new Class<?>[]{Long.class, long.class, long.class}, "population:query");
    }

    private static void assertPermission(String name, Class<?>[] types, String expected)
            throws NoSuchMethodException {
        RequiresPermission annotation = PopulationController.class
                .getMethod(name, types).getAnnotation(RequiresPermission.class);
        assertNotNull(annotation);
        assertArrayEquals(new String[]{expected}, annotation.value());
    }
}
