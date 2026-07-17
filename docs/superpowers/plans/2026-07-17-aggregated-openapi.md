# Aggregated OpenAPI Snapshot Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create one self-contained root `openapi.yaml` that Apifox can import to obtain all six business modules' APIs.

**Architecture:** Treat the six module-level OpenAPI files as sources and create a static import snapshot at the repository root. Merge tags, paths, the shared Bearer scheme, and the information-service schemas into one OpenAPI 3.0.3 document while keeping every reference internal and every operation ID unique.

**Tech Stack:** OpenAPI 3.0.3, YAML, Python 3 with PyYAML for read-only validation

## Global Constraints

- The root file is an Apifox import snapshot, not a new canonical contract.
- Keep all six module-level OpenAPI files unchanged.
- Use `http://localhost:9090` as the only server URL.
- Include exactly 7 unique paths and 9 operations from `community-info`, `community-auth`, `community-portal`, `community-service`, `community-welfare`, and `community-affairs`.
- Use only internal `#/components/...` references.
- Do not add a generator, build plugin, or Swagger UI aggregation configuration.

---

### Task 1: Create and validate the aggregated snapshot

**Files:**
- Create: `openapi.yaml`
- Reference: `community-info/src/main/resources/openapi/openapi.yaml`
- Reference: `community-auth/src/main/resources/openapi/openapi.yaml`
- Reference: `community-portal/src/main/resources/openapi/openapi.yaml`
- Reference: `community-service/src/main/resources/openapi/openapi.yaml`
- Reference: `community-welfare/src/main/resources/openapi/openapi.yaml`
- Reference: `community-affairs/src/main/resources/openapi/openapi.yaml`

**Interfaces:**
- Consumes: Six valid module-level OpenAPI 3.0.3 documents.
- Produces: A self-contained `openapi.yaml` accepted by Apifox, with one gateway server, six tags, seven paths, nine unique operations, one Bearer security scheme, and six information-service schemas.

- [ ] **Step 1: Run the failing existence check**

Run:

```bash
test -f openapi.yaml
```

Expected: exit code 1 because the aggregate snapshot does not exist.

- [ ] **Step 2: Create the self-contained OpenAPI document**

Create `openapi.yaml` with this exact top-level structure and source mapping:

```yaml
openapi: 3.0.3
info:
  title: 数智化社区服务平台 API
  version: v1
  description: 六个业务模块的聚合 API 契约快照，供 Apifox 导入；所有外部请求经网关访问。
servers:
  - url: http://localhost:9090
    description: 本地网关
security:
  - bearerAuth: []
tags:
  - name: 人口信息
    description: 户籍/常住/流动人口档案
  - name: 鉴权
  - name: 首页门户
  - name: 社区服务
  - name: 社区公益
  - name: 居务管理
paths: {}
components:
  securitySchemes:
    bearerAuth:
      type: http
      scheme: bearer
      bearerFormat: JWT
  schemas: {}
```

Replace `paths: {}` with these exact source blocks:

| Aggregated path | Source block | Required aggregate-only change |
| --- | --- | --- |
| `/api/v1/population/persons` | `community-info/.../openapi.yaml` path of the same name | None |
| `/api/v1/population/persons/{id}/versions` | `community-info/.../openapi.yaml` path of the same name | None |
| `/api/v1/auth/login` | `community-auth/.../openapi.yaml` path of the same name | Add `security: []` to the POST operation |
| `/api/v1/portal/ping` | `community-portal/.../openapi.yaml` path of the same name | Change `operationId` to `portalPing` |
| `/api/v1/service/ping` | `community-service/.../openapi.yaml` path of the same name | Change `operationId` to `servicePing` |
| `/api/v1/welfare/ping` | `community-welfare/.../openapi.yaml` path of the same name | Change `operationId` to `welfarePing` |
| `/api/v1/affairs/ping` | `community-affairs/.../openapi.yaml` path of the same name | Change `operationId` to `affairsPing` |

Replace `schemas: {}` with the complete `components.schemas` mapping from `community-info/src/main/resources/openapi/openapi.yaml`: `Population`, `PopulationCreateReq`, `PopulationPage`, `PopulationVersionUpdateReq`, `PopulationHis`, and `PopulationHisPage`. Preserve every request, response, example, constraint, and internal schema reference verbatim apart from the four operation ID changes and the login security override listed above.

- [ ] **Step 3: Run structural validation**

Run:

```bash
python3 -c 'import yaml; from pathlib import Path; d=yaml.safe_load(Path("openapi.yaml").read_text()); methods={"get","put","post","delete","options","head","patch","trace"}; ops=[op for item in d["paths"].values() for name,op in item.items() if name in methods]; ids=[op.get("operationId") for op in ops]; refs=[]; walk=lambda x: refs.append(x["$ref"]) if isinstance(x,dict) and "$ref" in x else [walk(v) for v in x.values()] if isinstance(x,dict) else [walk(v) for v in x] if isinstance(x,list) else None; walk(d); assert d["openapi"]=="3.0.3"; assert len(d["paths"])==7; assert len(ops)==9; assert all(ids) and len(ids)==len(set(ids)); assert all(r.startswith("#/components/") for r in refs); assert all(r.split("/")[-1] in d["components"][r.split("/")[-2]] for r in refs); print("paths={} operations={} uniqueOperationIds={} refs={}".format(len(d["paths"]),len(ops),len(set(ids)),len(refs)))'
```

Expected:

```text
paths=7 operations=9 uniqueOperationIds=9 refs=6
```

- [ ] **Step 4: Run repository diff validation**

Run:

```bash
git diff --check -- openapi.yaml
git status --short
```

Expected: `git diff --check` exits 0; status shows `?? openapi.yaml` plus only pre-existing user changes and the approved design/plan documents.

- [ ] **Step 5: Review the generated snapshot**

Run:

```bash
rg -n '^  /|operationId:|^  - name:|^    [A-Za-z].*:$' openapi.yaml
```

Expected: all seven paths, nine unique operation IDs, six tags, the single `bearerAuth` scheme, and the six schemas are visible. No module source file appears in `git diff`.

- [ ] **Step 6: Commit only if explicitly requested**

The current worktree already contains unrelated user changes. Do not commit by default. If the user explicitly requests a commit, stage only:

```bash
git add openapi.yaml docs/superpowers/specs/2026-07-17-aggregated-openapi-design.md docs/superpowers/plans/2026-07-17-aggregated-openapi.md
git commit -m "docs: 添加聚合 OpenAPI 快照"
```
