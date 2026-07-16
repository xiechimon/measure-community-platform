package com.measure.community.info.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.measure.community.common.model.RetObj;
import com.measure.community.info.api.model.PopulationCreateReqDto;
import com.measure.community.info.api.model.PopulationDto;
import com.measure.community.info.api.model.PopulationPageDto;
import com.measure.community.info.mapper.PopulationMapper;
import com.measure.community.info.model.entity.Population;
import com.measure.community.info.model.req.PopulationQueryReq;
import com.measure.community.info.service.PopulationService;
import com.measure.community.info.support.HmacUtil;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class PopulationServiceImpl extends ServiceImpl<PopulationMapper, Population> implements PopulationService {

    @Override
    public RetObj pagePersons(PopulationQueryReq req) {
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
        Page<Population> page = this.page(new Page<>(req.getPageNo(), req.getPageSize()), qw);

        PopulationPageDto dto = new PopulationPageDto();
        List<PopulationDto> records = page.getRecords().stream().map(this::toDto).toList();
        dto.setRecords(records);
        dto.setTotal(page.getTotal());
        dto.setSize(page.getSize());
        dto.setCurrent(page.getCurrent());
        dto.setPages(page.getPages());
        return RetObj.success(dto);
    }

    @Override
    public RetObj createPerson(PopulationCreateReqDto req) {
        if (!StringUtils.hasText(req.getIdCard())) {
            return RetObj.error("证件号不能为空");
        }
        String hmac = HmacUtil.blindIndex(req.getIdCard());
        long exists = this.count(new LambdaQueryWrapper<Population>()
                .eq(Population::getIdCardHmac, hmac));
        if (exists > 0) {
            return RetObj.error("该证件号已存在");
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
        p.setVersion(1);
        this.save(p);
        return RetObj.success(p.getId());
    }

    /** 实体 → 展示 DTO,证件号脱敏 */
    private PopulationDto toDto(Population p) {
        PopulationDto d = new PopulationDto();
        d.setId(p.getId());
        d.setType(p.getType());
        d.setName(p.getName());
        d.setIdCard(maskIdCard(p.getIdCard()));
        d.setGender(p.getGender());
        d.setPhone(p.getPhone());
        d.setInsuredStatus(p.getInsuredStatus());
        d.setEmploymentStatus(p.getEmploymentStatus());
        d.setVersion(p.getVersion());
        d.setCreateTime(p.getCreateTime());
        d.setUpdateTime(p.getUpdateTime());
        return d;
    }

    private String maskIdCard(String v) {
        if (v == null || v.length() < 8) return v;
        return v.substring(0, 4) + "********" + v.substring(v.length() - 4);
    }
}
