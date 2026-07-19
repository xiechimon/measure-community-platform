package com.measure.community.auth.model.req;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(name = "OrgCreateReq", description = "创建组织节点请求")
@Data
public class OrgCreateReq {
    @Schema(description = "父节点 ID,根节点为空", example = "10")
    private Long parentId;
    @Schema(description = "节点类型:DISTRICT/STREET/COMMUNITY/GRID", example = "GRID")
    private String type;
    @Schema(description = "节点名称", example = "网格1004")
    private String name;
}
