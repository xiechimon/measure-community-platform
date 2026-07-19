-- 数智化社区服务平台 组织/网格层级树 + 社区范围种子(P2b 续,见说明书§6)
-- 建在 measure_community

-- 组织/网格层级树(物化路径)
CREATE TABLE IF NOT EXISTS sys_org (
  id          BIGINT       PRIMARY KEY,
  parent_id   BIGINT       NULL COMMENT '父节点,根为 NULL',
  type        VARCHAR(16)  NOT NULL COMMENT 'DISTRICT/STREET/COMMUNITY/GRID',
  name        VARCHAR(64)  NOT NULL,
  path        VARCHAR(255) NOT NULL COMMENT '物化祖先路径 /根/.../自身/',
  create_time DATETIME     NULL,
  update_time DATETIME     NULL,
  KEY idx_org_path (path)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='组织/网格层级';

INSERT INTO sys_org (id, parent_id, type, name, path) VALUES
  (1,    NULL, 'DISTRICT',  '示例区',   '/1/'),
  (5,    1,    'STREET',    '示例街道', '/1/5/'),
  (10,   5,    'COMMUNITY', '社区甲',   '/1/5/10/'),
  (11,   5,    'COMMUNITY', '社区乙',   '/1/5/11/'),
  (1001, 10,   'GRID',      '网格1001', '/1/5/10/1001/'),
  (1003, 10,   'GRID',      '网格1003', '/1/5/10/1003/'),
  (1002, 11,   'GRID',      '网格1002', '/1/5/11/1002/')
ON DUPLICATE KEY UPDATE path = VALUES(path);

-- 第一片 gridA/gridB 绑定到各自 GRID 节点
UPDATE sys_user SET org_id = 1001 WHERE username = 'gridA';
UPDATE sys_user SET org_id = 1002 WHERE username = 'gridB';

-- 新增 GRID 用户 gridC(网格1003) 复用 gridOfficer 角色(密码=123456,与 V2 admin 的 BCrypt 串一致)
INSERT INTO sys_user (id, username, password, name, status, grid_id, org_id)
VALUES (4, 'gridC', '$2a$10$rn3VmUnv6QROpXGO9sn17ufP0CdxGDSnTYp0vNZ9Qj3Jn4uwo4T1O', '网格C员', 1, 1003, 1003)
ON DUPLICATE KEY UPDATE username = VALUES(username);

INSERT INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id FROM sys_user u, sys_role r WHERE u.username='gridC' AND r.code='gridOfficer'
ON DUPLICATE KEY UPDATE user_id = user_id;

-- 社区范围角色 communityOfficer + 用户 commX(社区甲=10)
INSERT INTO sys_role (id, code, name, data_scope)
VALUES (3, 'communityOfficer', '社区管理员', 'COMMUNITY')
ON DUPLICATE KEY UPDATE code = code;

INSERT INTO sys_user (id, username, password, name, status, org_id)
VALUES (5, 'commX', '$2a$10$rn3VmUnv6QROpXGO9sn17ufP0CdxGDSnTYp0vNZ9Qj3Jn4uwo4T1O', '社区甲管理员', 1, 10)
ON DUPLICATE KEY UPDATE username = VALUES(username);

INSERT INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id FROM sys_user u, sys_role r WHERE u.username='commX' AND r.code='communityOfficer'
ON DUPLICATE KEY UPDATE user_id = user_id;

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r, sys_permission p
WHERE r.code='communityOfficer' AND p.code IN ('population:query','population:create')
ON DUPLICATE KEY UPDATE role_id = role_id;
