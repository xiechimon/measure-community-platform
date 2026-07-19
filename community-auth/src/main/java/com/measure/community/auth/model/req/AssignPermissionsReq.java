package com.measure.community.auth.model.req;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Schema(name = "AssignPermissionsReq", description = "整集设置角色权限请求")
@Data
public class AssignPermissionsReq {
    @Schema(description = "权限点 ID 列表(整集替换,空列表表示清空)")
    private List<Long> permissionIds;
}
