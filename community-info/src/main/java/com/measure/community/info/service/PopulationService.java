package com.measure.community.info.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.measure.community.info.api.model.PopulationCreateReqDto;
import com.measure.community.info.api.model.PopulationHisPageDto;
import com.measure.community.info.api.model.PopulationPageDto;
import com.measure.community.info.api.model.PopulationVersionUpdateReqDto;
import com.measure.community.info.model.entity.Population;
import com.measure.community.info.model.req.PopulationQueryReq;

public interface PopulationService extends IService<Population> {
    PopulationPageDto pagePersons(PopulationQueryReq req);

    Long createPerson(PopulationCreateReqDto req);

    /** 版本更新:应用变更字段,乐观锁自增版本,追加历史快照,返回新版本号。 */
    Integer updateVersion(Long id, PopulationVersionUpdateReqDto req);

    /** 变更历史分页查询(按版本倒序)。 */
    PopulationHisPageDto listVersions(Long id, long page, long size);
}
