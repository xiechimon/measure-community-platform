package com.measure.community.auth.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统用户(sys_user)。密码存 BCrypt 哈希;org_id/grid_id 供数据范围(P2b)。
 */
@Data
@TableName("sys_user")
public class SysUser {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    /** BCrypt 哈希 */
    private String password;
    private String name;
    private String phone;
    /** 1启用 0停用 */
    private Integer status;
    @TableField("org_id")
    private Long orgId;
    @TableField("grid_id")
    private Long gridId;
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableField(value = "create_by", fill = FieldFill.INSERT)
    private String createBy;
    @TableField(value = "update_by", fill = FieldFill.INSERT_UPDATE)
    private String updateBy;
}
