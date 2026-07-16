-- 数智化社区服务平台 骨架样板建表脚本
CREATE DATABASE IF NOT EXISTS measure_community DEFAULT CHARACTER SET utf8mb4;
USE measure_community;

CREATE TABLE IF NOT EXISTS t_population (
  id                BIGINT       NOT NULL COMMENT '人口档案ID',
  type              VARCHAR(16)  NOT NULL COMMENT '类型:户籍/常住/流动',
  name              VARCHAR(64)  NOT NULL COMMENT '姓名',
  id_card           VARCHAR(255) NOT NULL COMMENT '证件号(AES-256密文存储,见说明书§5)',
  id_card_hmac      VARCHAR(64)  NOT NULL COMMENT '证件号HMAC盲索引,用于唯一/等值精确匹配(见说明书§5)',
  gender            VARCHAR(8)            COMMENT '性别',
  phone             VARCHAR(32)           COMMENT '联系电话',
  insured_status    VARCHAR(16)           COMMENT '参保状态',
  employment_status VARCHAR(16)           COMMENT '就业状态',
  version           INT          NOT NULL DEFAULT 1 COMMENT '版本号',
  create_time       DATETIME              COMMENT '创建时间',
  update_time       DATETIME              COMMENT '更新时间',
  create_by         VARCHAR(64)           COMMENT '创建人',
  update_by         VARCHAR(64)           COMMENT '更新人',
  PRIMARY KEY (id),
  UNIQUE KEY uk_id_card_hmac (id_card_hmac)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='人口信息(证件号密文+盲索引)';

CREATE TABLE IF NOT EXISTS t_population_his (
  id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '历史记录ID',
  population_id BIGINT       NOT NULL COMMENT '人口档案ID',
  version       INT          NOT NULL COMMENT '版本号',
  snapshot      JSON                  COMMENT '变更快照',
  changed_field VARCHAR(255)          COMMENT '变更字段',
  create_time   DATETIME              COMMENT '变更时间',
  create_by     VARCHAR(64)           COMMENT '操作人',
  PRIMARY KEY (id),
  KEY idx_population (population_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='人口变更历史(供版本更新接口使用)';
