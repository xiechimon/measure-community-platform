package com.measure.community.info.controller;

import com.measure.community.common.enums.SystemStatus;
import com.measure.community.common.exception.BizException;
import com.measure.community.common.exception.GlobalExceptionHandler;
import com.measure.community.info.api.model.PopulationCreateReqDto;
import com.measure.community.info.api.model.PopulationPageDto;
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
import static org.mockito.Mockito.when;
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
}
