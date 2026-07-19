package com.measure.community.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.measure.community.auth.model.entity.SysRole;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SysRoleMapper extends BaseMapper<SysRole> {

    /** 清空角色已绑定的全部权限(整集替换前置操作) */
    @Delete("DELETE FROM sys_role_permission WHERE role_id = #{roleId}")
    void deleteRolePermissions(@Param("roleId") Long roleId);

    /** 绑定单条角色-权限关系 */
    @Insert("INSERT INTO sys_role_permission(role_id, permission_id) VALUES(#{roleId}, #{permId})")
    void insertRolePermission(@Param("roleId") Long roleId, @Param("permId") Long permId);
}
