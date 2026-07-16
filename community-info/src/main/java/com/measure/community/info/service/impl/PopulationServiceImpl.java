package com.measure.community.info.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.measure.community.common.model.RetObj;
import com.measure.community.info.mapper.PopulationMapper;
import com.measure.community.info.model.entity.Population;
import com.measure.community.info.model.req.PopulationCreateReq;
import com.measure.community.info.model.req.PopulationQueryReq;
import com.measure.community.info.service.PopulationService;
import com.measure.community.info.support.HmacUtil;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class PopulationServiceImpl extends ServiceImpl<PopulationMapper, Population> implements PopulationService {

    @Override
    public RetObj pagePersons(PopulationQueryReq req) {
        LambdaQueryWrapper<Population> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(req.getType())) {
            qw.eq(Population::getType, req.getType());
        }
        // 姓名模糊匹配;证件号为密文列,无法 LIKE,只能经盲索引等值匹配(见§5)
        if (StringUtils.hasText(req.getName())) {
            qw.like(Population::getName, req.getName());
        }
        if (StringUtils.hasText(req.getIdCard())) {
            qw.eq(Population::getIdCardHmac, HmacUtil.blindIndex(req.getIdCard()));
        }
        qw.orderByDesc(Population::getCreateTime);
        Page<Population> page = this.page(new Page<>(req.getPageNo(), req.getPageSize()), qw);
        return RetObj.success(page);
    }

    @Override
    public RetObj createPerson(PopulationCreateReq req) {
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
        p.setType(req.getType());
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
}
