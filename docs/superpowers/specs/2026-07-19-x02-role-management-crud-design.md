# X-02 角色管理 CRUD 设计

> 日期：2026-07-19
> 状态：已确认（用户预授权自主交付 X-02 剩余，设计自审自批）
> 上级：`docs/superpowers/specs/2026-07-18-production-ready-tdd-delivery-design.md` §5.2 Wave 1 P2b
> 需求编号：`docs/requirements/backend-requirements.md` **X-02**（RBAC 管理面）

## 1. 背景与范围

前两片交付了 X-02 的数据范围**强制**（扁平 + 层级），但角色/权限/用户-角色关系目前只能靠 SQL 种子。本片让这套 RBAC **可管理**：community-auth 新增角色管理 CRUD（照 community-info 分层）。

- **纳入**（均在网关既有路由 `Path=/api/v1/auth/**` 下，无需改网关）：
  - 角色 CRUD：`GET /api/v1/auth/roles`(分页)、`POST /api/v1/auth/roles`、`PUT /api/v1/auth/roles/{id}`、`DELETE /api/v1/auth/roles/{id}`
  - 权限列表：`GET /api/v1/auth/permissions`（供分配时选择）
  - 角色↔权限：`PUT /api/v1/auth/roles/{id}/permissions`（整集设置）
  - 用户↔角色：`PUT /api/v1/auth/users/{userId}/roles`（整集设置）
- **明确不纳入（留后续片）**：组织级委派（管理员仅在授权组织内授予角色/管理用户）——本片是**全局 admin 级**管理，不加数据范围过滤；授权变更审计（留"组织级委派+审计"片）；组织树节点 CRUD（留组织树片）；CUSTOM 范围（留 CUSTOM 片）。

## 2. 现状（已核实）

- community-auth 有 ServiceImpl 分层（`UserService`/`UserServiceImpl extends ServiceImpl<SysUserMapper,SysUser>`）；`UserController @RequestMapping("/api/v1/auth")`；返回 `RetObj`（common）；`@RequiresPermission`（common）+ `RequiresPermissionAspect` 生效。
- **只有** `SysUser` 实体 + `SysUserMapper`（含 selectRoleCodes/selectPermissionCodes/selectRoleDataScopes/selectOrgPath，`@Select` 注解、无 XML）。`SysRole/SysPermission/SysUserRole/SysRolePermission` 实体与 mapper **不存在**。
- RBAC 表（V2）：`sys_role(id,code,name,data_scope)`、`sys_permission(id,code,name,type)`、`sys_user_role(user_id,role_id 复合PK)`、`sys_role_permission(role_id,permission_id 复合PK)`。种子权限只有 `system:user:query`/`population:query`/`population:create`/`population:update`——**无 `system:role:*`**。
- `DataScope` 六档 ALL/DISTRICT/STREET/COMMUNITY/GRID/SELF（无 CUSTOM 成员，CUSTOM→SELF）。

## 3. 设计

### 3.1 新增实体 + mapper（community-auth）
- `SysRole @TableName("sys_role")`：id/code/name/dataScope + 审计列（`@TableField(fill=...)` 沿用样板，若 sys_role 有 create_time 等）。**注意**：读 V2 CREATE TABLE 确认 sys_role 是否有审计列；无则实体不加，避免 MetaObjectHandler 填不存在的列。
- `SysPermission @TableName("sys_permission")`：id/code/name/type（只读列表用）。
- `SysRoleMapper extends BaseMapper<SysRole>`、`SysPermissionMapper extends BaseMapper<SysPermission>`。
- 连接表复合主键、无 id → **不建 BaseMapper 实体**，用自定义方法：
  - `SysRoleMapper`：`@Delete deleteRolePermissions(roleId)`、`@Insert insertRolePermission(roleId, permId)`、`selectPermissionIdsByRole(roleId)`；
  - `SysUserMapper`（扩）：`@Delete deleteUserRoles(userId)`、`@Insert insertUserRole(userId, roleId)`、`selectRoleIdsByUser(userId)`、`countUsersByRole(roleId)`。

### 3.2 RoleService/RoleServiceImpl/RoleController
- `RoleService extends IService<SysRole>`；`RoleServiceImpl extends ServiceImpl<SysRoleMapper,SysRole>`（`@Service`，注入 SysPermissionMapper/SysUserMapper 做关联操作）。
- `RoleController @RequestMapping("/api/v1/auth")`，方法各带 `@RequiresPermission`：
  - `GET /roles` → `system:role:query`；`GET /permissions` → `system:role:query`
  - `POST /roles` → `system:role:create`；`PUT /roles/{id}` → `system:role:update`；`DELETE /roles/{id}` → `system:role:delete`
  - `PUT /roles/{id}/permissions`、`PUT /users/{userId}/roles` → `system:role:assign`
- 返回 `RetObj.success(...)`；异常走 common `GlobalExceptionHandler`（`BizException(SystemStatus.*)`）。

### 3.3 业务规则（关键取舍）
| 规则 | 取值 | 理由 |
| --- | --- | --- |
| data_scope 校验 | 建/改只接受六档枚举名；CUSTOM/非法 → 400 BAD_REQUEST | CUSTOM 未实现，拒绝防误配 |
| code 唯一 | 建重复 code → 409 CONFLICT（撞 uk_role_code） | |
| 删除保护 | 角色被任一用户绑定 → 409；内置 `admin`(code) 禁删 → 403/409 | 防孤儿/防误删超管 |
| code 不可变 | PUT 只改 name/data_scope，忽略/拒绝 code 变更 | code 是稳定标识 |
| 整集设置 | PUT 权限/角色 = 先删该实体全部关联再插新集合，`@Transactional` | 幂等、语义清晰 |
| 关联校验 | 分配的 permissionId/roleId/userId 必须存在 → 否则 400 | 防悬空外键 |
| 鉴权 | 全部 `@RequiresPermission("system:role:*")`；种子授 admin | 只有超管能管 |
| 组织级过滤 | 本片不做（全局管理） | 留组织级委派片 |

### 3.4 V5 迁移（权限种子）
- `V5__role_management_perms.sql`：新增 `sys_permission` 行 `system:role:query`(id=6)/`system:role:create`(7)/`system:role:update`(8)/`system:role:delete`(9)/`system:role:assign`(10)，type='api'；映射到 admin 角色。显式 id + `ON DUPLICATE KEY UPDATE`（沿用 V3/V4 风格）。`DatabaseMigrationIT` 期望 4→5。

## 4. 接口/契约
- DTO（`model/req`、`model/vo`）：`RoleCreateReq`(code,name,dataScope)、`RoleUpdateReq`(name,dataScope)、`RoleQueryReq`(分页 page/size + 可选 code/name)、`RolePageDto`/`RoleDto`、`AssignPermissionsReq`(List<Long> permissionIds)、`AssignRolesReq`(List<Long> roleId)、`PermissionDto`。用 `@Valid`/`jakarta.validation` 基础校验（code/name 非空）。
- 会话不变；DB：新增 5 条权限种子；无新表（复用 V2 表）。

## 5. 测试与验收（TDD）
- **单元（离线，Mockito）**：`RoleServiceImpl`——data_scope 非法→BizException(BAD_REQUEST)；重复 code→CONFLICT（mock count/exists）；删被绑定角色→CONFLICT；删 admin→拒绝；整集设置=删+插调用序列（verify mapper 调用）；关联不存在→BAD_REQUEST。
- **迁移门禁**：`DatabaseMigrationIT` 4→5，真实 MySQL 应用 V5 幂等。
- **E2E（真实 MySQL 过网关，扩 wave0-smoke.sh，追加 8x 步）**：admin 登录 → 建角色 `smokeRole`(data_scope=GRID) → 设其权限 [population:query] → 建/复用一个用户绑定 smokeRole（或给已有 gridA 加该角色）→ 断言：建角色 200、重复 code 409、非法 data_scope 400、删被绑定角色 409、非授权用户（gridA 无 system:role:*）调 `POST /roles` → 403。全走五级门禁。

## 6. 非目标 / 后续片
- 组织级委派（授权组织内管理）+ 授权变更审计；组织树节点 CRUD；CUSTOM 范围。

## 7. 预计触及文件
- community-auth：`model/entity/SysRole.java`、`SysPermission.java`；`mapper/SysRoleMapper.java`、`SysPermissionMapper.java`、`SysUserMapper.java`(扩)；`service/RoleService.java`、`service/impl/RoleServiceImpl.java`；`controller/RoleController.java`；`model/req/*`、`model/vo/*`。
- database：`migration/V5__role_management_perms.sql`。
- 测试：community-auth 单测、`community-integration-tests/DatabaseMigrationIT`、`scripts/e2e/wave0-smoke.sh`。
