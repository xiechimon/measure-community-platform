package com.measure.community.auth.controller;

import com.measure.community.auth.model.req.OrgCreateReq;
import com.measure.community.auth.model.req.OrgMoveReq;
import com.measure.community.auth.model.req.OrgUpdateReq;
import com.measure.community.auth.service.OrgService;
import com.measure.community.common.annotation.RequiresPermission;
import com.measure.community.common.model.RetObj;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

/**
 * 组织树管理控制器(X-02):组织节点的增删改查与移动。
 */
@Tag(name = "组织树管理控制器")
@RestController
@RequestMapping("/api/v1/auth")
public class OrgController {

    private final OrgService orgService;

    public OrgController(OrgService orgService) {
        this.orgService = orgService;
    }

    @Operation(summary = "组织树列表")
    @GetMapping("/orgs")
    @RequiresPermission("system:org:query")
    public RetObj list() {
        return RetObj.success(orgService.listOrgs());
    }

    @Operation(summary = "创建组织节点")
    @PostMapping("/orgs")
    @RequiresPermission("system:org:create")
    public RetObj create(@RequestBody OrgCreateReq req) {
        return RetObj.success(orgService.createOrg(req));
    }

    @Operation(summary = "更新组织节点(仅 name/type)")
    @PutMapping("/orgs/{id}")
    @RequiresPermission("system:org:update")
    public RetObj update(@PathVariable Long id, @RequestBody OrgUpdateReq req) {
        orgService.updateOrg(id, req);
        return RetObj.success();
    }

    @Operation(summary = "移动组织节点到新父节点")
    @PutMapping("/orgs/{id}/move")
    @RequiresPermission("system:org:move")
    public RetObj move(@PathVariable Long id, @RequestBody OrgMoveReq req) {
        orgService.moveOrg(id, req.getNewParentId());
        return RetObj.success();
    }

    @Operation(summary = "删除组织节点")
    @DeleteMapping("/orgs/{id}")
    @RequiresPermission("system:org:delete")
    public RetObj delete(@PathVariable Long id) {
        orgService.deleteOrg(id);
        return RetObj.success();
    }
}
