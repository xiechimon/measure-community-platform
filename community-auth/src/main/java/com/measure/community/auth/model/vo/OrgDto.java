package com.measure.community.auth.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 组织节点展示 DTO。 */
@Data
public class OrgDto implements Serializable {
    private Long id;
    private Long parentId;
    private String type;
    private String name;
    private String path;
    private LocalDateTime createTime;
}
