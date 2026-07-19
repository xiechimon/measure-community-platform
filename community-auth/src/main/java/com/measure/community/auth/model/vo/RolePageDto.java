package com.measure.community.auth.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/** 角色分页结果。 */
@Data
public class RolePageDto implements Serializable {
    private List<RoleDto> records;
    private Long total;
    private Long size;
    private Long current;
    private Long pages;
}
