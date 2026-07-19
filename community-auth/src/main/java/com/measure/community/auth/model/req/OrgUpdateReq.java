package com.measure.community.auth.model.req;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(name = "OrgUpdateReq", description = "更新组织节点请求")
@Data
public class OrgUpdateReq {
    @Schema(description = "节点名称", example = "网格1004")
    private String name;
    @Schema(description = "节点类型:DISTRICT/STREET/COMMUNITY/GRID", example = "GRID")
    private String type;
}
