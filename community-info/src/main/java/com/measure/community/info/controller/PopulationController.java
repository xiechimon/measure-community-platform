package com.measure.community.info.controller;

import com.measure.community.common.model.RetObj;
import com.measure.community.info.model.req.PopulationCreateReq;
import com.measure.community.info.model.req.PopulationQueryReq;
import com.measure.community.info.service.PopulationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Tag(name = "人口信息(信息服务)")
@RestController
@RequestMapping("/api/v1/population")
public class PopulationController {

    @Autowired
    private PopulationService populationService;

    @Operation(summary = "人口信息分页查询")
    @GetMapping("/persons")
    public RetObj listPersons(PopulationQueryReq req) {
        return populationService.pagePersons(req);
    }

    @Operation(summary = "人口信息录入")
    @PostMapping("/persons")
    public RetObj createPerson(@RequestBody PopulationCreateReq req) {
        return populationService.createPerson(req);
    }
}
