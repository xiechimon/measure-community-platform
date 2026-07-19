package com.measure.community.auth.model.req;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(name = "RoleQueryReq", description = "角色分页查询请求")
@Data
public class RoleQueryReq {
    @Schema(description = "角色标识(模糊匹配)")
    private String code;
    @Schema(description = "角色名称(模糊匹配)")
    private String name;
    @Schema(description = "页码", example = "1")
    private Long page = 1L;
    @Schema(description = "页大小", example = "10")
    private Long size = 10L;
}
