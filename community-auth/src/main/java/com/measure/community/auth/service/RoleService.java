package com.measure.community.auth.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.measure.community.auth.model.entity.SysRole;
import com.measure.community.auth.model.req.RoleCreateReq;
import com.measure.community.auth.model.req.RoleQueryReq;
import com.measure.community.auth.model.req.RoleUpdateReq;
import com.measure.community.auth.model.vo.RolePageDto;

import java.util.List;

/**
 * 角色增删改查(P2a §6)。
 */
public interface RoleService extends IService<SysRole> {

    /** 创建角色:校验数据范围合法 + code 唯一,返回新角色 ID。 */
    Long createRole(RoleCreateReq req);

    /** 更新角色:仅可改 name/dataScope,不改 code。 */
    void updateRole(Long id, RoleUpdateReq req);

    /** 删除角色:admin 角色禁止删除,且已绑定用户的角色禁止删除。 */
    void deleteRole(Long id);

    /** 分页查询角色(code/name 模糊匹配)。 */
    RolePageDto pageRoles(RoleQueryReq req);

    /** 整集设置角色拥有的权限点(先清空再按新集合插入,空集合即清空)。 */
    void assignPermissions(Long roleId, List<Long> permissionIds);

    /** 整集设置用户拥有的角色(先清空再按新集合插入,空集合即清空)。 */
    void assignRoles(Long userId, List<Long> roleIds);
}
