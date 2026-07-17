package com.measure.community.info.controller;

import com.measure.community.common.annotation.RequiresPermission;
import com.measure.community.common.model.RetObj;
import com.measure.community.info.api.model.PopulationCreateReqDto;
import com.measure.community.info.api.model.PopulationVersionUpdateReqDto;
import com.measure.community.info.model.req.PopulationQueryReq;
import com.measure.community.info.service.PopulationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Tag(name = "人口信息(信息服务)")
@Validated
@RestController
@RequestMapping("/api/v1/population")
public class PopulationController {

    @Autowired
    private PopulationService populationService;

    @Operation(summary = "人口信息分页查询")
    @GetMapping("/persons")
    @RequiresPermission("population:query")
    public RetObj listPersons(PopulationQueryReq req) {
        return RetObj.success(populationService.pagePersons(req));
    }

    @Operation(summary = "人口信息录入")
    @PostMapping("/persons")
    @RequiresPermission("population:create")
    public RetObj createPerson(@Valid @RequestBody PopulationCreateReqDto req) {
        return RetObj.success(populationService.createPerson(req));
    }

    @Operation(summary = "人口信息版本更新(仅追加,不可删除/覆盖)")
    @PostMapping("/persons/{id}/versions")
    @RequiresPermission("population:update")
    public RetObj updatePersonVersion(@PathVariable Long id,
                                      @Valid @RequestBody PopulationVersionUpdateReqDto req) {
        return RetObj.success(populationService.updateVersion(id, req));
    }

    @Operation(summary = "人口变更历史查询")
    @GetMapping("/persons/{id}/versions")
    @RequiresPermission("population:query")
    public RetObj listPersonVersions(@PathVariable Long id,
                                     @RequestParam(defaultValue = "1") long page,
                                     @RequestParam(defaultValue = "10") long size) {
        return RetObj.success(populationService.listVersions(id, page, size));
    }
}
