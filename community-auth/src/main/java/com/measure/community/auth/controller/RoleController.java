package com.measure.community.auth.controller;

import com.measure.community.auth.model.req.AssignPermissionsReq;
import com.measure.community.auth.model.req.AssignRolesReq;
import com.measure.community.auth.model.req.RoleCreateReq;
import com.measure.community.auth.model.req.RoleQueryReq;
import com.measure.community.auth.model.req.RoleUpdateReq;
import com.measure.community.auth.service.RoleService;
import com.measure.community.common.annotation.RequiresPermission;
import com.measure.community.common.model.RetObj;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/**
 * 角色管理控制器(X-02):角色增删改查 + 角色-权限/用户-角色的整集设置 + 权限点列表。
 */
@Tag(name = "角色管理控制器")
@RestController
@RequestMapping("/api/v1/auth")
public class RoleController {

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @Operation(summary = "角色分页查询")
    @GetMapping("/roles")
    @RequiresPermission("system:role:query")
    public RetObj pageRoles(RoleQueryReq req) {
        return RetObj.success(roleService.pageRoles(req));
    }

    @Operation(summary = "权限点列表")
    @GetMapping("/permissions")
    @RequiresPermission("system:role:query")
    public RetObj listPermissions() {
        return RetObj.success(roleService.listPermissions());
    }

    @Operation(summary = "创建角色")
    @PostMapping("/roles")
    @RequiresPermission("system:role:create")
    public RetObj createRole(@Valid @RequestBody RoleCreateReq req) {
        return RetObj.success(roleService.createRole(req));
    }

    @Operation(summary = "更新角色(仅 name/dataScope)")
    @PutMapping("/roles/{id}")
    @RequiresPermission("system:role:update")
    public RetObj updateRole(@PathVariable Long id, @Valid @RequestBody RoleUpdateReq req) {
        roleService.updateRole(id, req);
        return RetObj.success();
    }

    @Operation(summary = "删除角色")
    @DeleteMapping("/roles/{id}")
    @RequiresPermission("system:role:delete")
    public RetObj deleteRole(@PathVariable Long id) {
        roleService.deleteRole(id);
        return RetObj.success();
    }

    @Operation(summary = "整集设置角色权限")
    @PutMapping("/roles/{id}/permissions")
    @RequiresPermission("system:role:assign")
    public RetObj assignPermissions(@PathVariable Long id, @RequestBody AssignPermissionsReq req) {
        roleService.assignPermissions(id, req.getPermissionIds());
        return RetObj.success();
    }

    @Operation(summary = "整集设置用户角色")
    @PutMapping("/users/{userId}/roles")
    @RequiresPermission("system:role:assign")
    public RetObj assignRoles(@PathVariable Long userId, @RequestBody AssignRolesReq req) {
        roleService.assignRoles(userId, req.getRoleIds());
        return RetObj.success();
    }
}
