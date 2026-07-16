package com.measure.community.info.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.measure.community.common.model.RetObj;
import com.measure.community.info.api.model.PopulationCreateReqDto;
import com.measure.community.info.model.entity.Population;
import com.measure.community.info.model.req.PopulationQueryReq;

public interface PopulationService extends IService<Population> {
    RetObj pagePersons(PopulationQueryReq req);
    RetObj createPerson(PopulationCreateReqDto req);
}
