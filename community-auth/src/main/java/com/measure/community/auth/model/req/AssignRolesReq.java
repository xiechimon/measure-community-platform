package com.measure.community.auth.model.req;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Schema(name = "AssignRolesReq", description = "整集设置用户角色请求")
@Data
public class AssignRolesReq {
    @Schema(description = "角色 ID 列表(整集替换,空列表表示清空)")
    private List<Long> roleIds;
}
