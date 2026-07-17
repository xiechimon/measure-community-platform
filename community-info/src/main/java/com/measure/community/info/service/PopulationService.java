package com.measure.community.info.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.measure.community.info.api.model.PopulationCreateReqDto;
import com.measure.community.info.api.model.PopulationPageDto;
import com.measure.community.info.model.entity.Population;
import com.measure.community.info.model.req.PopulationQueryReq;

public interface PopulationService extends IService<Population> {
    PopulationPageDto pagePersons(PopulationQueryReq req);
    Long createPerson(PopulationCreateReqDto req);
}
