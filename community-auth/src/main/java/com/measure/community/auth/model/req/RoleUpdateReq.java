package com.measure.community.auth.model.req;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(name = "RoleUpdateReq", description = "更新角色请求(不改角色标识 code)")
@Data
public class RoleUpdateReq {
    @Schema(description = "角色名称", example = "网格员")
    private String name;
    @Schema(description = "数据范围:SELF/GRID/COMMUNITY/STREET/DISTRICT/ALL", example = "GRID")
    private String dataScope;
}
