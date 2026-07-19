package com.measure.community.auth.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 角色展示 DTO。 */
@Data
public class RoleDto implements Serializable {
    private Long id;
    private String code;
    private String name;
    private String dataScope;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private String createBy;
    private String updateBy;
}
