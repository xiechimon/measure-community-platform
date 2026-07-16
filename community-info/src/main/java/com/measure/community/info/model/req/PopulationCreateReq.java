package com.measure.community.info.model.req;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(name = "PopulationCreateReq", description = "人口信息录入请求")
@Data
public class PopulationCreateReq {
    @Schema(description = "类型:户籍/常住/流动", example = "户籍")
    private String type;
    @Schema(description = "姓名", example = "张三")
    private String name;
    @Schema(description = "证件号")
    private String idCard;
    private String gender;
    private String phone;
    private String insuredStatus;
    private String employmentStatus;
}
