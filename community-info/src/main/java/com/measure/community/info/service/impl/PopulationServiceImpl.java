package com.measure.community.info.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.measure.community.common.enums.SystemStatus;
import com.measure.community.common.exception.BizException;
import com.measure.community.common.utils.DesensitizeUtil;
import com.measure.community.common.utils.UserContextHolder;
import com.measure.community.info.api.model.PopulationCreateReqDto;
import com.measure.community.info.api.model.PopulationDto;
import com.measure.community.info.api.model.PopulationHisDto;
import com.measure.community.info.api.model.PopulationHisPageDto;
import com.measure.community.info.api.model.PopulationPageDto;
import com.measure.community.info.api.model.PopulationVersionUpdateReqDto;
import com.measure.community.info.mapper.PopulationHisMapper;
import com.measure.community.info.mapper.PopulationMapper;
import com.measure.community.info.model.entity.Population;
import com.measure.community.info.model.entity.PopulationHis;
import com.measure.community.info.model.req.PopulationQueryReq;
import com.measure.community.info.service.PopulationService;
import com.measure.community.info.support.HmacUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class PopulationServiceImpl extends ServiceImpl<PopulationMapper, Population> implements PopulationService {

    /** 创建基线历史的变更字段标记 */
    private static final String CREATE_MARK = "__create__";

    @Autowired
    private PopulationHisMapper populationHisMapper;

    @Override
    public PopulationPageDto pagePersons(PopulationQueryReq req) {
        LambdaQueryWrapper<Population> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(req.getType())) {
            qw.eq(Population::getType, req.getType());
        }
        if (StringUtils.hasText(req.getName())) {
            qw.like(Population::getName, req.getName());
        }
        if (StringUtils.hasText(req.getIdCard())) {
            qw.eq(Population::getIdCardHmac, HmacUtil.blindIndex(req.getIdCard()));
        }
        qw.orderByDesc(Population::getCreateTime);
        Page<Population> page = this.page(new Page<>(req.getPage(), req.getSize()), qw);

        boolean unmask = UserContextHolder.hasPermission("population:sensitive:view");
        PopulationPageDto dto = new PopulationPageDto();
        List<PopulationDto> records = page.getRecords().stream().map(p -> toDto(p, unmask)).toList();
        dto.setRecords(records);
        dto.setTotal(page.getTotal());
        dto.setSize(page.getSize());
        dto.setCurrent(page.getCurrent());
        dto.setPages(page.getPages());
        return dto;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createPerson(PopulationCreateReqDto req) {
        if (!StringUtils.hasText(req.getIdCard())) {
            throw new BizException(SystemStatus.BAD_REQUEST, "证件号不能为空");
        }
        String hmac = HmacUtil.blindIndex(req.getIdCard());
        // 注意：数据范围拦截器会给此 count 注入 grid_id 条件（GRID 用户只在本网格查重），
        // 跨网格同证件号不会被此预检拦住 → 由全局 uk_id_card_hmac 唯一约束兜底（
        // GlobalExceptionHandler 映射 DuplicateKey→409）。真正的跨网格去重保证在 DB 约束。
        long exists = this.count(new LambdaQueryWrapper<Population>()
                .eq(Population::getIdCardHmac, hmac));
        if (exists > 0) {
            throw new BizException(SystemStatus.CONFLICT, "该证件号已存在");
        }
        Population p = new Population();
        p.setType(req.getType() == null ? null : req.getType().getValue());
        p.setName(req.getName());
        p.setIdCard(req.getIdCard());
        p.setIdCardHmac(hmac);
        p.setGender(req.getGender());
        p.setPhone(req.getPhone());
        p.setInsuredStatus(req.getInsuredStatus());
        p.setEmploymentStatus(req.getEmploymentStatus());
        p.setOrgId(UserContextHolder.getOrgId());
        p.setGridId(UserContextHolder.getGridId());
        p.setVersion(1);
        this.save(p);
        // 基线历史(v1),保证血缘从创建起完整
        writeHistory(p, CREATE_MARK);
        return p.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Integer updateVersion(Long id, PopulationVersionUpdateReqDto req) {
        Population cur = this.getById(id);
        if (cur == null) {
            throw new BizException(SystemStatus.NOT_FOUND, "人口档案不存在");
        }
        StringBuilder changed = new StringBuilder();
        if (req.getType() != null) {
            cur.setType(req.getType().getValue());
            changed.append("type,");
        }
        if (req.getName() != null) {
            cur.setName(req.getName());
            changed.append("name,");
        }
        if (req.getGender() != null) {
            cur.setGender(req.getGender());
            changed.append("gender,");
        }
        if (req.getPhone() != null) {
            cur.setPhone(req.getPhone());
            changed.append("phone,");
        }
        if (req.getInsuredStatus() != null) {
            cur.setInsuredStatus(req.getInsuredStatus());
            changed.append("insuredStatus,");
        }
        if (req.getEmploymentStatus() != null) {
            cur.setEmploymentStatus(req.getEmploymentStatus());
            changed.append("employmentStatus,");
        }
        if (changed.isEmpty()) {
            throw new BizException(SystemStatus.BAD_REQUEST, "无可更新字段");
        }
        // 乐观锁:updateById 自动 version+1 且 WHERE version=旧值;失败(并发)返回 false
        boolean ok = this.updateById(cur);
        if (!ok) {
            throw new BizException(SystemStatus.CONFLICT, "版本冲突,请重试");
        }
        // updateById 后 cur.version 已被回填为新值
        writeHistory(cur, changed.substring(0, changed.length() - 1));
        return cur.getVersion();
    }

    @Override
    public PopulationHisPageDto listVersions(Long id, long page, long size) {
        if (this.getById(id) == null) {
            throw new BizException(SystemStatus.NOT_FOUND, "人口档案不存在");
        }
        Page<PopulationHis> hisPage = populationHisMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<PopulationHis>()
                        .eq(PopulationHis::getPopulationId, id)
                        .orderByDesc(PopulationHis::getVersion));
        PopulationHisPageDto dto = new PopulationHisPageDto();
        dto.setRecords(hisPage.getRecords().stream().map(this::toHisDto).toList());
        dto.setTotal(hisPage.getTotal());
        dto.setSize(hisPage.getSize());
        dto.setCurrent(hisPage.getCurrent());
        dto.setPages(hisPage.getPages());
        return dto;
    }

    /** 追加一条历史:快照为脱敏后的业务视图,避免 JSON 列泄漏明文 PII。 */
    private void writeHistory(Population p, String changedFields) {
        PopulationHis his = new PopulationHis();
        his.setPopulationId(p.getId());
        his.setVersion(p.getVersion());
        his.setSnapshot(JSON.toJSONString(toDto(p, false)));
        his.setChangedField(changedFields);
        populationHisMapper.insert(his);
    }

    /**
     * 实体 → 展示 DTO。unmask=true 返回明文 idCard/phone(需具备
     * population:sensitive:view 权限),false 走 DesensitizeUtil 打码。
     * 注意:writeHistory 的快照恒传 false,避免历史 JSON 列泄漏明文 PII。
     */
    private PopulationDto toDto(Population p, boolean unmask) {
        PopulationDto d = new PopulationDto();
        d.setId(p.getId());
        d.setType(p.getType());
        d.setName(p.getName());
        d.setIdCard(unmask ? p.getIdCard() : DesensitizeUtil.idCard(p.getIdCard()));
        d.setGender(p.getGender());
        d.setPhone(unmask ? p.getPhone() : DesensitizeUtil.phone(p.getPhone()));
        d.setInsuredStatus(p.getInsuredStatus());
        d.setEmploymentStatus(p.getEmploymentStatus());
        d.setVersion(p.getVersion());
        d.setCreateTime(p.getCreateTime());
        d.setUpdateTime(p.getUpdateTime());
        return d;
    }

    private PopulationHisDto toHisDto(PopulationHis h) {
        PopulationHisDto d = new PopulationHisDto();
        d.setId(h.getId());
        d.setPopulationId(h.getPopulationId());
        d.setVersion(h.getVersion());
        d.setSnapshot(h.getSnapshot());
        d.setChangedField(h.getChangedField());
        d.setCreateTime(h.getCreateTime());
        d.setCreateBy(h.getCreateBy());
        return d;
    }
}
