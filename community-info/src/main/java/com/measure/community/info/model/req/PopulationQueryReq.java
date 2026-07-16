package com.measure.community.info.model.req;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(name = "PopulationQueryReq", description = "人口分页查询请求")
@Data
public class PopulationQueryReq {
    @Schema(description = "类型:户籍/常住/流动")
    private String type;
    @Schema(description = "姓名(模糊匹配)")
    private String name;
    @Schema(description = "证件号(经盲索引等值精确匹配)")
    private String idCard;
    @Schema(description = "页码", example = "1")
    private Long pageNo = 1L;
    @Schema(description = "页大小", example = "10")
    private Long pageSize = 10L;
}
