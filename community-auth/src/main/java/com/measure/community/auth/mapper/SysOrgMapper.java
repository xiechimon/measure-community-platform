package com.measure.community.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.measure.community.auth.model.entity.SysOrg;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SysOrgMapper extends BaseMapper<SysOrg> {

    /** 批量重写子树物化路径:将 oldPath 前缀替换为 newPath(移动节点时,子孙节点整体重算)。 */
    @Update("UPDATE sys_org SET path = CONCAT(#{newPath}, SUBSTRING(path, CHAR_LENGTH(#{oldPath})+1)) WHERE path LIKE CONCAT(#{oldPath}, '%')")
    void updateSubtreePath(@Param("oldPath") String oldPath, @Param("newPath") String newPath);

    /** 更新节点的父节点 ID。 */
    @Update("UPDATE sys_org SET parent_id = #{parentId} WHERE id = #{id}")
    void updateParent(@Param("id") Long id, @Param("parentId") Long parentId);

    /** 统计直接子节点数(删除保护:存在子节点不可删)。 */
    @Select("SELECT COUNT(*) FROM sys_org WHERE parent_id = #{parentId}")
    long countChildren(Long parentId);

    /** 统计引用该组织/网格节点的用户数(sys_user.org_id/grid_id)。 */
    @Select("SELECT COUNT(*) FROM sys_user WHERE org_id = #{orgId} OR grid_id = #{orgId}")
    long countUserOrgRefs(Long orgId);

    /** 统计引用该组织/网格节点的人口档案数(t_population.org_id/grid_id)。 */
    @Select("SELECT COUNT(*) FROM t_population WHERE org_id = #{orgId} OR grid_id = #{orgId}")
    long countPopulationRefs(Long orgId);
}
