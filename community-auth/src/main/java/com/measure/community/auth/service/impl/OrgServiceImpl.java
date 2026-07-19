package com.measure.community.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.measure.community.auth.mapper.SysOrgMapper;
import com.measure.community.auth.model.entity.SysOrg;
import com.measure.community.auth.model.req.OrgCreateReq;
import com.measure.community.auth.model.req.OrgUpdateReq;
import com.measure.community.auth.model.vo.OrgDto;
import com.measure.community.auth.service.OrgService;
import com.measure.community.common.enums.SystemStatus;
import com.measure.community.common.exception.BizException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Set;

/**
 * 组织/网格层级树节点增删改查(X-02 §1),物化路径 path 由父节点 path + 自身 id 拼接。
 */
@Service
public class OrgServiceImpl extends ServiceImpl<SysOrgMapper, SysOrg> implements OrgService {

    private static final Set<String> TYPES = Set.of("DISTRICT", "STREET", "COMMUNITY", "GRID");

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createOrg(OrgCreateReq req) {
        if (req.getType() == null || !TYPES.contains(req.getType())) {
            throw new BizException(SystemStatus.BAD_REQUEST, "节点类型不合法");
        }
        if (!StringUtils.hasText(req.getName())) {
            throw new BizException(SystemStatus.BAD_REQUEST, "节点名称不能为空");
        }
        SysOrg parent = null;
        if (req.getParentId() != null) {
            parent = this.getById(req.getParentId());
            if (parent == null) {
                throw new BizException(SystemStatus.BAD_REQUEST, "父节点不存在");
            }
        }
        SysOrg o = new SysOrg();
        o.setParentId(req.getParentId());
        o.setType(req.getType());
        o.setName(req.getName());
        // 占位 path,save 后回填 id 再计算真实物化路径
        o.setPath("/tmp/");
        this.save(o);
        o.setPath((parent == null ? "/" : parent.getPath()) + o.getId() + "/");
        this.updateById(o);
        return o.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateOrg(Long id, OrgUpdateReq req) {
        SysOrg o = this.getById(id);
        if (o == null) {
            throw new BizException(SystemStatus.NOT_FOUND, "组织节点不存在");
        }
        if (!StringUtils.hasText(req.getName())) {
            throw new BizException(SystemStatus.BAD_REQUEST, "节点名称不能为空");
        }
        if (req.getType() == null || !TYPES.contains(req.getType())) {
            throw new BizException(SystemStatus.BAD_REQUEST, "节点类型不合法");
        }
        // 只允许改 name/type,不改 parent/path
        o.setName(req.getName());
        o.setType(req.getType());
        this.updateById(o);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void moveOrg(Long id, Long newParentId) {
        SysOrg node = this.getById(id);
        if (node == null) {
            throw new BizException(SystemStatus.NOT_FOUND, "组织节点不存在");
        }
        if (newParentId != null && newParentId.equals(id)) {
            throw new BizException(SystemStatus.BAD_REQUEST, "不能移动到自身下");
        }
        SysOrg np = null;
        if (newParentId != null) {
            np = this.getById(newParentId);
            if (np == null) {
                throw new BizException(SystemStatus.BAD_REQUEST, "新父节点不存在");
            }
            if (np.getPath().startsWith(node.getPath())) {
                throw new BizException(SystemStatus.BAD_REQUEST, "不能移动到自身子树下");
            }
        }
        String newPath = (np == null ? "/" : np.getPath()) + id + "/";
        this.baseMapper.updateSubtreePath(node.getPath(), newPath);
        this.baseMapper.updateParent(id, newParentId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteOrg(Long id) {
        SysOrg node = this.getById(id);
        if (node == null) {
            throw new BizException(SystemStatus.NOT_FOUND, "组织节点不存在");
        }
        if (this.baseMapper.countChildren(id) > 0) {
            throw new BizException(SystemStatus.CONFLICT, "存在子节点");
        }
        if (this.baseMapper.countUserOrgRefs(id) > 0 || this.baseMapper.countPopulationRefs(id) > 0) {
            throw new BizException(SystemStatus.CONFLICT, "节点被用户或人口引用");
        }
        this.removeById(id);
    }

    @Override
    public List<OrgDto> listOrgs() {
        return this.list(new LambdaQueryWrapper<SysOrg>().orderByAsc(SysOrg::getPath))
                .stream().map(this::toDto).toList();
    }

    private OrgDto toDto(SysOrg o) {
        OrgDto d = new OrgDto();
        d.setId(o.getId());
        d.setParentId(o.getParentId());
        d.setType(o.getType());
        d.setName(o.getName());
        d.setPath(o.getPath());
        d.setCreateTime(o.getCreateTime());
        return d;
    }
}
