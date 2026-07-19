package com.measure.community.auth.model.vo;

import lombok.Data;

import java.io.Serializable;

/** 权限点展示 DTO(sys_permission)。 */
@Data
public class PermissionDto implements Serializable {
    private Long id;
    private String code;
    private String name;
    /** 类型:menu/button/api */
    private String type;
}
