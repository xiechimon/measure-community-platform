-- 数智化社区服务平台 组织树管理(X-02,见说明书§1)
-- 建在 measure_community
-- 1) sys_org.id 改自增:V4 用显式种子 id(最大 1003),MySQL MODIFY ... AUTO_INCREMENT 会从 max(id)+1=1004 续,不与种子冲突;id 仍为 V4 定义的 PRIMARY KEY。
ALTER TABLE sys_org MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT;

-- 2) 组织树管理权限种子(id 11-15,V2 占 1-4、V5 占 6-10,无冲突)
INSERT INTO sys_permission (id, code, name, type) VALUES
  (11, 'system:org:query',  '查询组织', 'api'),
  (12, 'system:org:create', '创建组织', 'api'),
  (13, 'system:org:update', '修改组织', 'api'),
  (14, 'system:org:delete', '删除组织', 'api'),
  (15, 'system:org:move',   '移动组织', 'api')
ON DUPLICATE KEY UPDATE code = code;

-- 3) 授予 admin 全部组织管理权限
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r, sys_permission p
WHERE r.code = 'admin' AND p.code IN
  ('system:org:query','system:org:create','system:org:update','system:org:delete','system:org:move')
ON DUPLICATE KEY UPDATE role_id = role_id;
