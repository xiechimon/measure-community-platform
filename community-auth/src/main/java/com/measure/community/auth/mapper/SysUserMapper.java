package com.measure.community.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.measure.community.auth.model.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    /** 查用户拥有的角色码 */
    @Select("""
            SELECT r.code FROM sys_role r
            JOIN sys_user_role ur ON ur.role_id = r.id
            WHERE ur.user_id = #{userId}
            """)
    List<String> selectRoleCodes(@Param("userId") Long userId);

    /** 查用户拥有的权限点码(经角色) */
    @Select("""
            SELECT DISTINCT p.code FROM sys_permission p
            JOIN sys_role_permission rp ON rp.permission_id = p.id
            JOIN sys_user_role ur ON ur.role_id = rp.role_id
            WHERE ur.user_id = #{userId}
            """)
    List<String> selectPermissionCodes(@Param("userId") Long userId);

    /** 查用户拥有角色对应的数据范围码(经角色,可能多条,取最宽) */
    @Select("""
            SELECT r.data_scope FROM sys_role r
            JOIN sys_user_role ur ON ur.role_id = r.id
            WHERE ur.user_id = #{userId}
            """)
    List<String> selectRoleDataScopes(@Param("userId") Long userId);

    /** 查组织节点的物化路径(如 /1/5/10/),供登录写入 orgPath 展开层级数据范围 */
    @Select("SELECT path FROM sys_org WHERE id = #{orgId}")
    String selectOrgPath(@Param("orgId") Long orgId);
}
