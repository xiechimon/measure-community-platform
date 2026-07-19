package com.measure.community.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.measure.community.auth.mapper.SysPermissionMapper;
import com.measure.community.auth.mapper.SysRoleMapper;
import com.measure.community.auth.mapper.SysUserMapper;
import com.measure.community.auth.model.entity.SysRole;
import com.measure.community.auth.model.req.RoleCreateReq;
import com.measure.community.auth.model.req.RoleQueryReq;
import com.measure.community.auth.model.req.RoleUpdateReq;
import com.measure.community.auth.model.entity.SysPermission;
import com.measure.community.auth.model.vo.PermissionDto;
import com.measure.community.auth.model.vo.RoleDto;
import com.measure.community.auth.model.vo.RolePageDto;
import com.measure.community.auth.service.RoleService;
import com.measure.community.common.enums.DataScope;
import com.measure.community.common.enums.SystemStatus;
import com.measure.community.common.exception.BizException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 角色增删改查(P2a §6),以及角色-权限/用户-角色的整集设置(Task 2)。
 */
@Service
public class RoleServiceImpl extends ServiceImpl<SysRoleMapper, SysRole> implements RoleService {

    /** 超级管理员角色标识,禁止删除 */
    private static final String ADMIN_CODE = "admin";

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private SysPermissionMapper sysPermissionMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createRole(RoleCreateReq req) {
        if (!validScope(req.getDataScope())) {
            throw new BizException(SystemStatus.BAD_REQUEST, "数据范围不合法");
        }
        long exists = this.count(new LambdaQueryWrapper<SysRole>().eq(SysRole::getCode, req.getCode()));
        if (exists > 0) {
            throw new BizException(SystemStatus.CONFLICT, "角色标识已存在");
        }
        SysRole role = new SysRole();
        role.setCode(req.getCode());
        role.setName(req.getName());
        role.setDataScope(req.getDataScope());
        this.save(role);
        return role.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateRole(Long id, RoleUpdateReq req) {
        SysRole role = this.getById(id);
        if (role == null) {
            throw new BizException(SystemStatus.NOT_FOUND, "角色不存在");
        }
        if (!validScope(req.getDataScope())) {
            throw new BizException(SystemStatus.BAD_REQUEST, "数据范围不合法");
        }
        // 只允许改 name/dataScope,不改 code
        role.setName(req.getName());
        role.setDataScope(req.getDataScope());
        this.updateById(role);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRole(Long id) {
        SysRole role = this.getById(id);
        if (role == null) {
            throw new BizException(SystemStatus.NOT_FOUND, "角色不存在");
        }
        if (ADMIN_CODE.equals(role.getCode())) {
            throw new BizException(SystemStatus.CONFLICT, "超级管理员角色不可删除");
        }
        if (sysUserMapper.countUsersByRole(id) > 0) {
            throw new BizException(SystemStatus.CONFLICT, "角色已被用户绑定,不可删除");
        }
        this.removeById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignPermissions(Long roleId, List<Long> permissionIds) {
        SysRole role = this.getById(roleId);
        if (role == null) {
            throw new BizException(SystemStatus.NOT_FOUND, "角色不存在");
        }
        if (!CollectionUtils.isEmpty(permissionIds)
                && sysPermissionMapper.selectByIds(permissionIds).size() != permissionIds.size()) {
            throw new BizException(SystemStatus.BAD_REQUEST, "权限点不存在");
        }
        this.baseMapper.deleteRolePermissions(roleId);
        if (!CollectionUtils.isEmpty(permissionIds)) {
            for (Long permId : permissionIds) {
                this.baseMapper.insertRolePermission(roleId, permId);
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignRoles(Long userId, List<Long> roleIds) {
        if (!CollectionUtils.isEmpty(roleIds)
                && this.baseMapper.selectByIds(roleIds).size() != roleIds.size()) {
            throw new BizException(SystemStatus.BAD_REQUEST, "角色不存在");
        }
        sysUserMapper.deleteUserRoles(userId);
        if (!CollectionUtils.isEmpty(roleIds)) {
            for (Long roleId : roleIds) {
                sysUserMapper.insertUserRole(userId, roleId);
            }
        }
    }

    @Override
    public RolePageDto pageRoles(RoleQueryReq req) {
        LambdaQueryWrapper<SysRole> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(req.getCode())) {
            qw.like(SysRole::getCode, req.getCode());
        }
        if (StringUtils.hasText(req.getName())) {
            qw.like(SysRole::getName, req.getName());
        }
        qw.orderByDesc(SysRole::getCreateTime);
        Page<SysRole> page = this.page(new Page<>(req.getPage(), req.getSize()), qw);

        RolePageDto dto = new RolePageDto();
        dto.setRecords(page.getRecords().stream().map(this::toDto).toList());
        dto.setTotal(page.getTotal());
        dto.setSize(page.getSize());
        dto.setCurrent(page.getCurrent());
        dto.setPages(page.getPages());
        return dto;
    }

    @Override
    public List<PermissionDto> listPermissions() {
        return sysPermissionMapper.selectList(null).stream().map(this::toPermissionDto).toList();
    }

    /** dataScope 合法性:六档(ALL/DISTRICT/STREET/COMMUNITY/GRID/SELF)通过,CUSTOM/null/非法值一律拒绝。 */
    private boolean validScope(String dataScope) {
        if (dataScope == null) {
            return false;
        }
        try {
            DataScope.valueOf(dataScope);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private RoleDto toDto(SysRole r) {
        RoleDto d = new RoleDto();
        d.setId(r.getId());
        d.setCode(r.getCode());
        d.setName(r.getName());
        d.setDataScope(r.getDataScope());
        d.setCreateTime(r.getCreateTime());
        d.setUpdateTime(r.getUpdateTime());
        d.setCreateBy(r.getCreateBy());
        d.setUpdateBy(r.getUpdateBy());
        return d;
    }

    private PermissionDto toPermissionDto(SysPermission p) {
        PermissionDto d = new PermissionDto();
        d.setId(p.getId());
        d.setCode(p.getCode());
        d.setName(p.getName());
        d.setType(p.getType());
        return d;
    }
}
