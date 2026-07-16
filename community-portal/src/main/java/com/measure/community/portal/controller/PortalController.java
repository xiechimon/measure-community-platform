package com.measure.community.portal.controller;

import com.measure.community.common.model.RetObj;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "首页门户(骨架占位)")
@RestController
@RequestMapping("/api/v1/portal")
public class PortalController {
    @Operation(summary = "健康探针")
    @GetMapping("/ping")
    public RetObj ping() {
        return RetObj.success("community-portal ok");
    }
}
