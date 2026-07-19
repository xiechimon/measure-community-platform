package com.measure.community.auth.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 组织/网格层级树节点(sys_org)。物化路径 path 形如 /根/.../自身/,type 为 DISTRICT/STREET/COMMUNITY/GRID。
 * 建表脚本 id 列暂无 AUTO_INCREMENT(V6 补),但实体仍按 IdType.AUTO 声明(见 X-02 任务简报)。
 */
@Data
@TableName("sys_org")
public class SysOrg {
    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("parent_id")
    private Long parentId;
    private String type;
    private String name;
    private String path;
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
