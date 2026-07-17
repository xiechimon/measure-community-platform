# 后端需求基线 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 Excel「阶段1」中除三级驾驶舱和 AI 能力外的所有 Java 后端需求，持久化为可追踪、可验收的仓库内基线文档。

**Architecture:** 创建一份唯一的 `docs/requirements/backend-requirements.md`，按责任 Java 服务分域，并以稳定需求编号串联能力、来源行、权限范围、状态与证据。文末的来源覆盖矩阵逐行覆盖 Excel 中所有纳入范围的行；安全和接口细节只引用 docx 中与这些业务行相关的非功能约束。

**Tech Stack:** Markdown、Excel OOXML 解析（只读）、Java 17/Spring Boot 服务拓扑、现有 MyBatis-Plus/鉴权架构。

## Global Constraints

- 功能范围唯一来自 `docs/开发计划7.13(1).xlsx` 的「阶段1」；排除第 189 行及以后三级驾驶舱、整个 AI侧工作表和业务条目中的 AI 子能力。
- 所有后端实现使用 Java 17 + Spring Boot；不创建或对接 FastAPI、Node.js 或其他后端。
- 每个需求须有唯一业务归属服务；`community-common` 和 `community-gateway` 只能作为技术支撑，不得拥有业务数据。
- 每个纳入 Excel 行必须在来源覆盖矩阵中映射到至少一个需求编号；未映射即失败。
- 已完成/部分完成状态必须指向当前代码和测试证据；ping、表结构和旧 E2E 结论均不能单独证明完成。

---

### Task 1: 建立来源行与排除集清单

**Files:**
- Create: `docs/requirements/backend-requirements.md`
- Read: `docs/开发计划7.13(1).xlsx`
- Read: `docs/superpowers/specs/2026-07-17-backend-requirements-baseline-design.md`

**Interfaces:**
- Consumes: Excel「阶段1」行 3–188 与设计中的排除规则。
- Produces: 文档中的“范围、排除项、服务责任映射”章节，以及覆盖矩阵的纳入行集合。

- [ ] **Step 1: 提取并列出来源行集合**

运行：

```bash
python3 -c '
import zipfile, xml.etree.ElementTree as E
z=zipfile.ZipFile("docs/开发计划7.13(1).xlsx")
ns={"m":"http://schemas.openxmlformats.org/spreadsheetml/2006/main"}
ss=["".join(x.itertext()) for x in E.fromstring(z.read("xl/sharedStrings.xml")).findall("m:si",ns)]
root=E.fromstring(z.read("xl/worksheets/sheet1.xml"))
for row in root.findall(".//m:sheetData/m:row",ns):
 n=int(row.attrib["r"])
 if 3<=n<=188:
  values={}
  for c in row.findall("m:c",ns):
   v=c.find("m:v",ns); value="" if v is None else v.text
   values[c.attrib["r"].rstrip("0123456789")]=ss[int(value)] if c.attrib.get("t")=="s" and value else value
  print(n, values.get("A",""), values.get("B",""), values.get("C",""), values.get("D",""), values.get("E",""), sep=" | ")
'
```

Expected: 输出每行的 A–E 列值，用于判定业务归属与合并关系；第 189 行及以后不得进入清单。

- [ ] **Step 2: 写入范围和责任映射章节**

在 `docs/requirements/backend-requirements.md` 写入以下固定章节：范围、明确排除项、状态判定、责任服务映射、跨服务积分与告警边界。责任服务只能为 `community-auth`、`community-info`、`community-service`、`community-welfare`、`community-affairs` 或 `community-portal`。

- [ ] **Step 3: 验证排除边界**

运行：

```bash
rg -n '三级驾驶舱|AI侧|AI 智能分流|智能匹配' docs/requirements/backend-requirements.md
```

Expected: 文档仅在“明确排除项”中说明这些能力；任何需求表中不得将 AI 实现列为能力。

### Task 2: 写入按服务归属的全量需求表

**Files:**
- Modify: `docs/requirements/backend-requirements.md`
- Read: `docs/详细功能设计说明书-数智化社区服务平台-V7 版本.docx`
- Read: `docs/ROADMAP.md`
- Read: `README.md`

**Interfaces:**
- Consumes: Task 1 的来源行集合和责任映射。
- Produces: 稳定编号 `X-`、`I-`、`S-`、`W-`、`A-`、`P-`、`C-` 的需求表条目。

- [ ] **Step 1: 写入横切、信息和系统配置需求**

创建认证/RBAC/数据范围/审计/隐私、人口与空间资产、特殊人群、资源生态链、关联档案、消息和日志条目。每条列“主要数据/流程与状态机”“来源精确定位”“数据范围/权限”“状态”“实现与验收证据”。

- [ ] **Step 2: 写入服务、公益和居务需求**

创建项目/订单/支付/商城、资助/助农/法援、活动/工单/诉求/场地/工具/资讯/门禁条目。订单条目必须记录支付回调幂等、退款、对账、风控和核销；所有附件、导出、任务、告警在所属业务条目与横切条目中双向引用。

- [ ] **Step 3: 写入居民、物业和首页 API 需求**

创建居民个人中心、服务订单、活动、诉求、预约、资助、工具、师傅、求职、公告 API；物业工作台、履约工单、检索、考核 API；首页账本、待办和聚合统计 API。每项引用后台同一状态机的需求编号。

- [ ] **Step 4: 以现有实现确定状态与证据**

运行：

```bash
rg -n 'class PopulationController|class UserController|@RequiresPermission|t_population_his' community-info community-auth community-common database/mysql
```

Expected: 仅人口版本样板和现有认证/RBAC 基础标为“部分完成”；其余空壳业务服务标为“未开始”。

### Task 3: 创建来源覆盖矩阵并做文档验收

**Files:**
- Modify: `docs/requirements/backend-requirements.md`
- Modify: `docs/ROADMAP.md`

**Interfaces:**
- Consumes: Task 2 的稳定需求编号。
- Produces: 行号到需求编号的全覆盖矩阵，以及路线图到基线的正式链接。

- [ ] **Step 1: 写入来源覆盖矩阵**

按 Excel 业务域列出全部纳入行号及其需求编号；同一需求合并行时列出完整集合。将第 112 行 AI 分流和第 173 行智能匹配拆为“非 AI 流程映射、AI 子能力排除”的说明。

- [ ] **Step 2: 自动检查行号覆盖**

运行一个只读校验：从 Excel 取出 3–188 中非三级驾驶舱的业务行；从 Markdown 覆盖矩阵抽取行号；比较集合并输出缺失或多余行。

Expected: `missing=[]`，且第 189 行及以后不在矩阵中。

- [ ] **Step 3: 检查文档完整性和链接**

运行：

```bash
rg -n 'TBD|TODO|待定|待补|实施时再确定' docs/requirements/backend-requirements.md
git diff --check
```

Expected: 无输出；每条需求均有来源、业务归属服务、状态和证据列。

- [ ] **Step 4: 更新路线图链接并复审**

将 `docs/ROADMAP.md` 的基线设计链接替换为正式 `docs/requirements/backend-requirements.md` 链接；重新运行 Task 3 的校验命令，确认范围和追踪入口一致。

- [ ] **Step 5: Commit**

```bash
git add docs/requirements/backend-requirements.md docs/ROADMAP.md README.md docs/superpowers/specs/2026-07-17-backend-requirements-baseline-design.md docs/superpowers/specs/2026-07-16-measure-community-platform-scaffold-design.md docs/superpowers/plans/2026-07-17-backend-requirements-baseline.md
git commit -m "docs: add backend requirements baseline"
```

Expected: 一个只包含需求基线、范围对齐和实施计划文档的提交；不得包含用户的 `docker-compose.yml`。
