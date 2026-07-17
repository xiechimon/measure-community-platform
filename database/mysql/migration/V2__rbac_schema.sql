-- 数智化社区服务平台 RBAC 权限体系(P2a,见说明书§6)
-- 建在 measure_community,收敛脚手架遗留的 xf-boot-base.xf_user

-- 用户
CREATE TABLE IF NOT EXISTS sys_user (
  id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  username    VARCHAR(64)  NOT NULL COMMENT '登录账号',
  password    VARCHAR(100) NOT NULL COMMENT '密码(BCrypt 哈希)',
  name        VARCHAR(64)           COMMENT '姓名',
  phone       VARCHAR(32)           COMMENT '手机号',
  status      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态:1启用 0停用',
  org_id      BIGINT                COMMENT '所属组织ID(数据范围用,P2b)',
  grid_id     BIGINT                COMMENT '所属网格ID(数据范围用,P2b)',
  create_time DATETIME              COMMENT '创建时间',
  update_time DATETIME              COMMENT '更新时间',
  create_by   VARCHAR(64)           COMMENT '创建人',
  update_by   VARCHAR(64)           COMMENT '更新人',
  PRIMARY KEY (id),
  UNIQUE KEY uk_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户';

-- 角色
CREATE TABLE IF NOT EXISTS sys_role (
  id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '角色ID',
  code        VARCHAR(64)  NOT NULL COMMENT '角色标识,如 admin/grid_worker',
  name        VARCHAR(64)  NOT NULL COMMENT '角色名称',
  data_scope  VARCHAR(16)  NOT NULL DEFAULT 'SELF' COMMENT '数据范围:SELF/GRID/COMMUNITY/STREET/DISTRICT/CUSTOM/ALL(P2b 生效)',
  create_time DATETIME              COMMENT '创建时间',
  update_time DATETIME              COMMENT '更新时间',
  create_by   VARCHAR(64)           COMMENT '创建人',
  update_by   VARCHAR(64)           COMMENT '更新人',
  PRIMARY KEY (id),
  UNIQUE KEY uk_role_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色';

-- 权限点(菜单/按钮/接口)
CREATE TABLE IF NOT EXISTS sys_permission (
  id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '权限ID',
  code        VARCHAR(128) NOT NULL COMMENT '权限标识,如 population:export',
  name        VARCHAR(64)  NOT NULL COMMENT '权限名称',
  type        VARCHAR(16)  NOT NULL DEFAULT 'api' COMMENT '类型:menu/button/api',
  create_time DATETIME              COMMENT '创建时间',
  update_time DATETIME              COMMENT '更新时间',
  create_by   VARCHAR(64)           COMMENT '创建人',
  update_by   VARCHAR(64)           COMMENT '更新人',
  PRIMARY KEY (id),
  UNIQUE KEY uk_perm_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限点';

-- 用户-角色
CREATE TABLE IF NOT EXISTS sys_user_role (
  user_id BIGINT NOT NULL COMMENT '用户ID',
  role_id BIGINT NOT NULL COMMENT '角色ID',
  PRIMARY KEY (user_id, role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色关联';

-- 角色-权限
CREATE TABLE IF NOT EXISTS sys_role_permission (
  role_id       BIGINT NOT NULL COMMENT '角色ID',
  permission_id BIGINT NOT NULL COMMENT '权限ID',
  PRIMARY KEY (role_id, permission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色权限关联';

-- ============ 种子数据:超级管理员 ==========
-- 密码 BCrypt('123456');强度10。
INSERT INTO sys_user (id, username, password, name, status)
VALUES (1, 'admin', '$2a$10$rn3VmUnv6QROpXGO9sn17ufP0CdxGDSnTYp0vNZ9Qj3Jn4uwo4T1O', '超级管理员', 1)
ON DUPLICATE KEY UPDATE username = VALUES(username);

INSERT INTO sys_role (id, code, name, data_scope)
VALUES (1, 'admin', '超级管理员', 'ALL')
ON DUPLICATE KEY UPDATE code = code;

INSERT INTO sys_permission (id, code, name, type) VALUES
  (1, 'system:user:query', '查询用户', 'api'),
  (2, 'population:query',  '查询人口', 'api'),
  (3, 'population:create', '录入人口', 'api'),
  (4, 'population:update', '人口版本更新', 'api')
ON DUPLICATE KEY UPDATE code = code;

INSERT INTO sys_user_role (user_id, role_id) VALUES (1, 1)
ON DUPLICATE KEY UPDATE user_id = user_id;

INSERT INTO sys_role_permission (role_id, permission_id) VALUES (1,1),(1,2),(1,3),(1,4)
ON DUPLICATE KEY UPDATE role_id = role_id;
