package com.measure.community.info.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.measure.community.info.support.AesTypeHandler;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName(value = "t_population", autoResultMap = true)
public class Population {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /** 户籍/常住/流动 */
    private String type;
    private String name;
    /** 证件号,AES 加密存储(占位) */
    @TableField(value = "id_card", typeHandler = AesTypeHandler.class)
    private String idCard;
    /** 证件号 HMAC 盲索引,用于唯一/等值精确匹配(见说明书§5) */
    @TableField("id_card_hmac")
    private String idCardHmac;
    private String gender;
    private String phone;
    @TableField("insured_status")
    private String insuredStatus;
    @TableField("employment_status")
    private String employmentStatus;
    private Integer version;
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableField(value = "create_by", fill = FieldFill.INSERT)
    private String createBy;
    @TableField(value = "update_by", fill = FieldFill.INSERT_UPDATE)
    private String updateBy;
}
