package com.measure.community.service.controller;

import com.measure.community.common.model.RetObj;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "社区服务(骨架占位)")
@RestController
@RequestMapping("/api/v1/service")
public class ServiceController {
    @Operation(summary = "健康探针")
    @GetMapping("/ping")
    public RetObj ping() {
        return RetObj.success("community-service ok");
    }
}
