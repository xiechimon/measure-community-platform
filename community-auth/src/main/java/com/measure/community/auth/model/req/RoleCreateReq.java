package com.measure.community.auth.model.req;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(name = "RoleCreateReq", description = "创建角色请求")
@Data
public class RoleCreateReq {
    @Schema(description = "角色标识,唯一,如 grid_worker", example = "grid_worker")
    private String code;
    @Schema(description = "角色名称", example = "网格员")
    private String name;
    @Schema(description = "数据范围:SELF/GRID/COMMUNITY/STREET/DISTRICT/ALL", example = "GRID")
    private String dataScope;
}
