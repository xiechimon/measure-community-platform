-- 数智化社区服务平台 人口归属列 + 数据范围种子(P2b,见说明书§6)
-- 建在 measure_community

-- 人口表补充归属列(数据范围过滤用)
ALTER TABLE t_population
    ADD COLUMN org_id  BIGINT NULL COMMENT '所属组织ID(数据范围)',
    ADD COLUMN grid_id BIGINT NULL COMMENT '所属网格ID(数据范围)';
CREATE INDEX idx_population_grid ON t_population(grid_id);

-- 敏感字段明文查看权限,授予 admin 角色
INSERT INTO sys_permission (id, code, name, type)
VALUES (5, 'population:sensitive:view', '人口敏感字段明文查看', 'api')
ON DUPLICATE KEY UPDATE code = code;

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r, sys_permission p
WHERE r.code = 'admin' AND p.code = 'population:sensitive:view'
ON DUPLICATE KEY UPDATE role_id = role_id;

-- 网格范围角色 + 两个不同网格的用户(密码=123456,与 V2 admin 的 BCrypt 串一致)
INSERT INTO sys_role (id, code, name, data_scope)
VALUES (2, 'gridOfficer', '网格员', 'GRID')
ON DUPLICATE KEY UPDATE code = code;

INSERT INTO sys_user (id, username, password, name, status, grid_id)
VALUES
  (2, 'gridA', '$2a$10$rn3VmUnv6QROpXGO9sn17ufP0CdxGDSnTYp0vNZ9Qj3Jn4uwo4T1O', '网格A员', 1, 1001),
  (3, 'gridB', '$2a$10$rn3VmUnv6QROpXGO9sn17ufP0CdxGDSnTYp0vNZ9Qj3Jn4uwo4T1O', '网格B员', 1, 1002)
ON DUPLICATE KEY UPDATE username = VALUES(username);

INSERT INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id FROM sys_user u, sys_role r
WHERE u.username IN ('gridA', 'gridB') AND r.code = 'gridOfficer'
ON DUPLICATE KEY UPDATE user_id = user_id;

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r, sys_permission p
WHERE r.code = 'gridOfficer' AND p.code IN ('population:query', 'population:create')
ON DUPLICATE KEY UPDATE role_id = role_id;
