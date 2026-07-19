package com.measure.community.auth.model.req;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(name = "OrgMoveReq", description = "移动组织节点请求")
@Data
public class OrgMoveReq {
    @Schema(description = "新父节点 ID,移动为根节点时为空", example = "10")
    private Long newParentId;
}
