package com.measure.community.auth.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.measure.community.auth.model.entity.SysOrg;
import com.measure.community.auth.model.req.OrgCreateReq;
import com.measure.community.auth.model.req.OrgUpdateReq;
import com.measure.community.auth.model.vo.OrgDto;

import java.util.List;

/**
 * 组织/网格层级树节点增删改查(X-02 §1)。
 */
public interface OrgService extends IService<SysOrg> {

    /** 创建组织节点:校验 type 合法、name 非空、parentId(若有)存在,按父节点 path 追加自身 id 计算物化路径,返回新节点 ID。 */
    Long createOrg(OrgCreateReq req);

    /** 更新组织节点:仅可改 name/type,不改 parent/path。 */
    void updateOrg(Long id, OrgUpdateReq req);

    /** 按 path 升序列出全部组织节点。 */
    List<OrgDto> listOrgs();

    /** 移动组织节点到新父节点下:重算自身及整棵子树的物化路径,防止移动到自身子树内形成环。newParentId 为空表示移动为根节点。 */
    void moveOrg(Long id, Long newParentId);

    /** 删除组织节点:存在子节点或被用户/人口档案引用时拒绝。 */
    void deleteOrg(Long id);
}
