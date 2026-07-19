# X-02 组织树管理 CRUD 设计

> 日期：2026-07-19
> 状态：已确认（用户预授权自主交付 X-02 剩余，设计自审自批）
> 需求编号：`docs/requirements/backend-requirements.md` **X-02**（组织/网格数据范围的管理面 · 组织树）
> 前置：Wave 1 第二片（`sys_org` 物化路径树，V4 建表）；第三片（角色管理 CRUD，community-auth 分层样板）

## 1. 背景与范围

`sys_org` 层级树（V4）目前只能靠 SQL 种子维护。本片让组织树**可管理**：community-auth 新增组织节点 CRUD + 移动（含子树物化路径重算）。全局 admin 级。

- **纳入**（`/api/v1/auth/**` 既有网关路由下，无需改网关）：
  - `GET /api/v1/auth/orgs` — 列出全部节点（按 path 排序，含 path 字段，客户端可建树）
  - `POST /api/v1/auth/orgs` — 建节点（parent 下；计算 path）
  - `PUT /api/v1/auth/orgs/{id}` — 改 name/type（不改 parent/path）
  - `PUT /api/v1/auth/orgs/{id}/move` — 移动到新父节点（重算本节点+全部后代 path）
  - `DELETE /api/v1/auth/orgs/{id}` — 删节点（有子节点或被引用则拒绝）
- **明确不纳入（留后续片）**：CUSTOM 自定义范围；组织级授权委派 + 授权变更审计；严格父子层级校验（DISTRICT>STREET>COMMUNITY>GRID 的强制父类型）——本片只校验 type ∈ 四档，不强制父子层级顺序。

## 2. 现状（已核实）

- `sys_org`（V4）：`id BIGINT PK(无 AUTO_INCREMENT)`、`parent_id BIGINT NULL`、`type VARCHAR(16)`、`name VARCHAR(64)`、`path VARCHAR(255)`（`/根/.../自身/`）、`create_time/update_time`、`KEY idx_org_path(path)`。V4 种子显式 id：1(区)/5(街道)/10,11(社区)/1001,1003(社区10网格)/1002(社区11网格)。
- **无 `SysOrg` 实体/mapper**。sys_org 被读取处：`SysUserMapper.selectOrgPath`（登录）、`PopulationDataPermissionHandler` 的 `grid_id IN (SELECT id FROM sys_org WHERE type='GRID' AND path LIKE '<orgPath>%')`（数据范围拦截器）。
- community-auth 分层样板：第三片 role CRUD（entity/mapper/service/service.impl/controller/model.req/model.vo，`@RequiresPermission`，`RetObj`，`BizException(SystemStatus,msg)`，`GlobalExceptionHandler` 兜底，MockMvc standaloneSetup 单测）。
- 迁移到 V5；本片新增 V6。

## 3. 设计

### 3.1 id 生成（V6 ALTER）
- `sys_org.id` 改 `AUTO_INCREMENT`（MySQL 从 `max(id)+1=1004` 起续，不与种子冲突）。`SysOrg` 实体 `@TableId(type=IdType.AUTO)`。
- **create 两步（path 依赖自增 id）**：`insert(parent_id,type,name,path=占位)` → MyBatis-Plus 回填 id → `update path = <父path或"/"> + id + "/"`。同一 `@Transactional`。

### 3.2 create
- 校验：`type ∈ {DISTRICT,STREET,COMMUNITY,GRID}`（否则 400）；`name` 非空（400）；`parentId` 为 null（建根）或该父节点存在（否则 400）。
- path 计算：`parent==null ? "/"+id+"/" : parent.getPath()+id+"/"`（父 path 已以 `/` 结尾）。
- 返回新 id。

### 3.3 update
- `getById`→无→NOT_FOUND；改 `name`（非空校验）与 `type`（四档校验）；**不改 parent_id/path**（移动走 move）。`updateById`。

### 3.4 move（难点：子树 path 重算 + 防环）
- `PUT /orgs/{id}/move` body `{newParentId}`（null=移为根）。
- 校验：节点存在；`newParentId != id`（不能移到自己下，400）；newParent 为 null 或存在；**防环**：newParent 不能是本节点的后代——即 `newParent.path` 不能以本节点 `path` 为前缀（`newParent.path LIKE node.path%` → 400 "不能移动到自身子树下"）。
- 重算（`@Transactional`）：
  - `oldPath = node.path`；`newPath = (newParent==null?"/":newParent.path) + id + "/"`。
  - **批量重写本节点+全部后代**：`UPDATE sys_org SET path = CONCAT(#{newPath}, SUBSTRING(path, CHAR_LENGTH(#{oldPath})+1)) WHERE path LIKE CONCAT(#{oldPath}, '%')`（本节点 path=oldPath→newPath；后代 path 前缀 oldPath 替换为 newPath，保留后缀）。
  - `UPDATE sys_org SET parent_id = #{newParentId} WHERE id = #{id}`。
- **关键决策（会话 orgPath 过期）**：某用户的组织节点或其祖先被移动后,该用户会话里的 `orgPath` 变陈旧,直到**下次登录刷新**。本片只保证 **DB 一致**;不主动失效会话(会话失效需 Redis token 联动,复杂,留后续)。DB 与拦截器子查询始终按最新 path,故新登录即正确。已知限制,文档标注。

### 3.5 delete
- `getById`→无→NOT_FOUND。**拒绝删除**(409)当:
  - 有子节点(`countChildren(id)>0`,即 `parent_id=id` 的行);或
  - 被引用:`sys_user.org_id=id` 或 `sys_user.grid_id=id` 或 `t_population.org_id=id` 或 `t_population.grid_id=id`(任一 count>0)。
- 无子无引用 → `removeById`。不级联(安全)。
- **关键决策**:不允许删根/被 V4 种子引用的节点(gridA/gridB/gridC/commX 绑定的 1001/1002/1003/10),自然被"被引用"规则拦住。

### 3.6 @RequiresPermission + V6 权限种子
- 端点门:`system:org:query`(GET)/`create`/`update`/`delete`/`move`。
- `V6__org_management.sql`:`ALTER TABLE sys_org MODIFY id BIGINT AUTO_INCREMENT`;新增 `sys_permission` id 11-15 = `system:org:query/create/update/delete/move`(type 'api')授 admin。`ON DUPLICATE KEY`。`DatabaseMigrationIT` 5→6。

## 4. 接口/契约
- DTO(`model/req`/`model/vo`):`OrgCreateReq`(parentId?,type,name)、`OrgUpdateReq`(name,type)、`OrgMoveReq`(newParentId?)、`OrgDto`(id,parentId,type,name,path,createTime)。GET 返回 `List<OrgDto>` 按 path 排序。
- 会话不变(orgPath 仍登录时写,move 后下次登录刷新)。DB:V6 ALTER + 5 权限种子。

## 5. 测试与验收(TDD)
- **单元(离线,Mockito)**:`OrgServiceImpl`——create 非法 type/name/父不存在→400,path 计算=父path+id+"/";update 改 name/type 不动 path;move 防环(newParent 是后代→400)、移到自身→400、重算调用 `updateSubtreePath` mapper(verify SQL 参数);delete 有子→409、被引用→409、叶子无引用→removeById。
- **迁移门禁**:`DatabaseMigrationIT` 5→6,真实 MySQL 应用 V6(AUTO_INCREMENT + 种子)幂等。
- **E2E(真实 MySQL 过网关,扩 wave0-smoke.sh 追加 7x 步)**:admin 建节点 A(社区10 下,type GRID)→GET /orgs 找到 A 的 path 前缀 `/1/5/10/`;在 A 下建子 B→B.path 以 A.path 为前缀;move A 到社区11(10→11)→GET 断言 A.path 变 `/1/5/11/<Aid>/` 且 B.path 也随之变前缀(子树重算);删 B(叶子无引用)→200;删被 gridA 绑定的 1001→409;非授权 gridA 调 `POST /orgs`→403。全走五级门禁。

## 6. 非目标 / 后续片
- CUSTOM 范围;组织级授权委派 + 授权变更审计;严格父子层级类型校验;move 时主动失效受影响用户会话。

## 7. 预计触及文件
- community-auth:`model/entity/SysOrg.java`;`mapper/SysOrgMapper.java`(+ SysUserMapper 加 countUsersByOrg/countUsersByGrid,或复用);`service/OrgService.java`+`service/impl/OrgServiceImpl.java`;`controller/OrgController.java`;`model/req/*`+`model/vo/OrgDto.java`。人口引用计数:community-auth 直接 `@Select COUNT` t_population(跨模块表,SQL 层可查同库)。
- database:`migration/V6__org_management.sql`。
- 测试:community-auth 单测、`community-integration-tests/DatabaseMigrationIT`、`scripts/e2e/wave0-smoke.sh`。
