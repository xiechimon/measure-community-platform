package com.measure.community.info.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 人口变更历史(t_population_his)。每个版本追加一行,存脱敏后业务快照;
 * 仅追加、不修改不删除,支撑「版本更新 / 历史查询」(§4.1.2 #3/#4)。
 */
@Data
@TableName("t_population_his")
public class PopulationHis {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 关联人口档案 ID */
    @TableField("population_id")
    private Long populationId;
    /** 该行对应的版本号 */
    private Integer version;
    /** 该版本的业务快照(JSON,证件号等已脱敏) */
    private String snapshot;
    /** 本次变更的字段名列表(创建基线记 __create__) */
    @TableField("changed_field")
    private String changedField;
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(value = "create_by", fill = FieldFill.INSERT)
    private String createBy;
}
