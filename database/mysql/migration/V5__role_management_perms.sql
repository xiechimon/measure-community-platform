-- 数智化社区服务平台 角色管理 CRUD 权限种子(X-02,见说明书§6)
-- 建在 measure_community

INSERT INTO sys_permission (id, code, name, type) VALUES
  (6,  'system:role:query',  '查询角色', 'api'),
  (7,  'system:role:create', '创建角色', 'api'),
  (8,  'system:role:update', '修改角色', 'api'),
  (9,  'system:role:delete', '删除角色', 'api'),
  (10, 'system:role:assign', '分配角色权限', 'api')
ON DUPLICATE KEY UPDATE code = code;

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r, sys_permission p
WHERE r.code = 'admin' AND p.code IN
  ('system:role:query','system:role:create','system:role:update','system:role:delete','system:role:assign')
ON DUPLICATE KEY UPDATE role_id = role_id;
