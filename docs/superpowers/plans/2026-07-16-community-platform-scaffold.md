# 数智化社区服务平台微服务骨架落地 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 `spring-cloud-alibaba-base-demo` 落地为「数智化社区服务平台」正式微服务骨架:统一命名、清理 demo、保留 common/gateway/auth,并新增连真实 MySQL、经网关可访问的样板业务模块 community-info。

**Architecture:** 多模块 Maven 工程(父 POM 聚合),Spring Cloud Alibaba 微服务 + Nacos 注册/配置中心 + Spring Cloud Gateway 网关。common 提供统一响应/MyBatis-Plus/Redis 等公共能力,各业务模块依赖 common,经网关按 `/api/v1/{域}/**` 路由。

**Tech Stack:** JDK 17、Spring Boot 3.3.5、Spring Cloud 2023.0.1、Spring Cloud Alibaba 2023.0.1、Nacos、MyBatis-Plus 3.5.8、dynamic-datasource、MySQL 8、Redis、Lombok、springdoc。

## Global Constraints

- groupId = `com.measure`(所有 pom)
- 根 artifactId = `measure-community-platform`,packaging=pom
- 模块前缀 = `community-`;包根 = `com.measure.community.{模块}`
- 主类命名 = `Community{Module}Application`;`spring.application.name` = `community-{module}`
- Nacos 命名空间 ID 沿用 = `74193cd9-fac4-4f2a-addc-47c60508b15c`
- 信息服务接口前缀固定 = `/api/v1/population`(依据设计说明书 4.1.2)
- 样板真实表 = `t_population`、`t_population_his`;数据库名 = `measure_community`
- 不改动原始 `spring-cloud-alibaba-base-demo` 目录;新工程独立 `git init`
- 全量替换后代码中不得残留 `com.xf`、`cloud-common/cloud-gateway/cloud-user`、`CloudGatewayApplication/CloudUserApplication`
- 版本号锁定,不升级依赖;不实现 info 之外的业务模块与 AI/FastAPI 代码

---

## 文件结构(创建/修改总览)

```
measure-community-platform/
 ├ pom.xml                         [改] 根 POM:groupId/artifactId/modules
 ├ community-common/               [改名自 cloud-common] 包 com.measure.community.common
 ├ community-gateway/              [改名自 cloud-gateway] 包 com.measure.community.gateway
 │   └ .../filter/AuthFilter.java  [改] 白名单加 /api/v1/population
 ├ community-auth/                 [改名自 cloud-user]    包 com.measure.community.auth
 ├ community-info/                 [新增] 样板业务模块
 │   ├ pom.xml
 │   ├ src/main/java/com/measure/community/info/
 │   │   ├ CommunityInfoApplication.java
 │   │   ├ controller/PopulationController.java
 │   │   ├ service/PopulationService.java
 │   │   ├ service/impl/PopulationServiceImpl.java
 │   │   ├ mapper/PopulationMapper.java
 │   │   ├ model/entity/Population.java
 │   │   ├ model/req/PopulationQueryReq.java
 │   │   ├ model/req/PopulationCreateReq.java
 │   │   ├ support/AesTypeHandler.java        (AES 接线占位)
│   │   ├ support/HmacUtil.java              (盲索引 HMAC 接线占位)
 │   │   └ ...
 │   ├ src/main/resources/application.yml
 │   ├ src/main/resources/application-local.yml
 │   └ src/test/java/com/measure/community/info/controller/PopulationControllerTest.java
 ├ database/mysql/01-init-schema.sql          [新增] 建表脚本
 ├ doc/community-info-dev.yaml                 [新增] Nacos 业务配置模板
 ├ doc/community-gateway-dev.yaml              [新增] Nacos 网关路由配置
 └ README.md                                   [改] 项目说明 + 新增模块指南
```

---

## Task 1: 导入脚手架、清理 demo、git init

**Files:**
- Create: `measure-community-platform/`(整树复制)
- Delete: `measure-community-platform/cloud-producer/`、`measure-community-platform/cloud-consumer/`
- Modify: `measure-community-platform/pom.xml`(仅 `<modules>` 去掉 producer/consumer)

**Interfaces:**
- Produces: 一个可编译的 3 模块(cloud-common/cloud-gateway/cloud-user)Maven 工程,坐标仍为 demo 原值。供 Task 2 改名。

- [ ] **Step 1: 复制脚手架到新目录(排除 git/idea/target)**

Run:
```bash
cd "//wsl.localhost/Ubuntu/home/measure/ideaProject"
rsync -a --exclude='.git' --exclude='.idea' --exclude='target' \
  spring-cloud-alibaba-base-demo/ measure-community-platform/
ls measure-community-platform
```
Expected: 列出 `pom.xml cloud-common cloud-gateway cloud-user cloud-producer cloud-consumer doc Dockerfile Jenkinsfile README.md ...`
(若无 rsync,用 `cp -a spring-cloud-alibaba-base-demo/. measure-community-platform/` 后手动 `rm -rf measure-community-platform/{.git,.idea}` 及各模块 target)

- [ ] **Step 2: 删除 demo 模块目录**

Run:
```bash
cd "//wsl.localhost/Ubuntu/home/measure/ideaProject/measure-community-platform"
rm -rf cloud-producer cloud-consumer
ls
```
Expected: 不再出现 cloud-producer / cloud-consumer

- [ ] **Step 3: 从父 POM 的 `<modules>` 移除 producer/consumer**

在 `measure-community-platform/pom.xml` 的 `<modules>` 中删除这两行:
```xml
        <module>cloud-producer</module>
        <module>cloud-consumer</module>
```
保留:
```xml
    <modules>
        <module>cloud-gateway</module>
        <module>cloud-user</module>
        <module>cloud-common</module>
    </modules>
```

- [ ] **Step 4: git init 并验证可编译**

Run:
```bash
cd "//wsl.localhost/Ubuntu/home/measure/ideaProject/measure-community-platform"
git init -q
mvn -q -DskipTests package 2>&1 | tail -5
```
Expected: `BUILD SUCCESS`,reactor 含 3 个模块(cloud-common、cloud-gateway、cloud-user)。

- [ ] **Step 5: 首次提交(导入基线)**

Run:
```bash
git add -A
git commit -q -m "chore: 导入 spring-cloud-alibaba 脚手架并移除 demo 模块"
git log --oneline -1
```
Expected: 一条提交记录。

---

## Task 2: 全量改名为 com.measure / community-*

**Files:**
- Rename: `cloud-common`→`community-common`、`cloud-gateway`→`community-gateway`、`cloud-user`→`community-auth`(含内部 java 包目录)
- Modify: 全部 `pom.xml`、`application.yml`、`*.java`(包名/类名/字符串)、主类与测试类文件名
- Modify: `community-gateway/.../filter/AuthFilter.java`(白名单字符串)
- Modify: `Jenkinsfile`(模块 case/端口/镜像名/仓库地址;删除 producer/consumer)

**Interfaces:**
- Consumes: Task 1 的 3 模块工程。
- Produces: 坐标 `com.measure`、模块 `community-common/gateway/auth`、包根 `com.measure.community.{common,gateway,auth}`、主类 `CommunityGatewayApplication`/`CommunityAuthApplication`。Task 3 的 community-info 依赖 `community-common`(groupId `com.measure`),`@ComponentScan` 引用 `com.measure.community.common`。

- [ ] **Step 1: 移动模块目录与 java 包目录**

Run:
```bash
cd "//wsl.localhost/Ubuntu/home/measure/ideaProject/measure-community-platform"
# 模块目录
mv cloud-common community-common
mv cloud-gateway community-gateway
mv cloud-user   community-auth
# java 包目录(main + test)
mkdir -p community-common/src/main/java/com/measure/community
mv community-common/src/main/java/com/xf/cloudcommon community-common/src/main/java/com/measure/community/common
for m in gateway auth; do
  case $m in gateway) old=gateway; dir=community-gateway;; auth) old=clouduser; dir=community-auth;; esac
  for s in main test; do
    if [ -d "$dir/src/$s/java/com/xf/$old" ]; then
      mkdir -p "$dir/src/$s/java/com/measure/community"
      mv "$dir/src/$s/java/com/xf/$old" "$dir/src/$s/java/com/measure/community/$m"
    fi
  done
done
# 清理空的旧包根
find . -type d -path '*/com/xf' -empty -delete
find . -type d -name xf -empty -delete 2>/dev/null
echo done
```
Expected: `done`,且 `find . -type d -path '*com/xf*'` 无输出。

- [ ] **Step 2: 批量替换包名(仅 .java)**

Run:
```bash
cd "//wsl.localhost/Ubuntu/home/measure/ideaProject/measure-community-platform"
grep -rl --include=*.java 'com\.xf\.' . | while read f; do
  sed -i \
    -e 's/com\.xf\.cloudcommon/com.measure.community.common/g' \
    -e 's/com\.xf\.gateway/com.measure.community.gateway/g' \
    -e 's/com\.xf\.clouduser/com.measure.community.auth/g' "$f"
done
grep -rn 'com\.xf' --include=*.java . | head
```
Expected: 末尾 grep 无输出(java 中已无 com.xf)。

- [ ] **Step 3: 重命名主类与测试类(文件名 + 内容)**

Run:
```bash
cd "//wsl.localhost/Ubuntu/home/measure/ideaProject/measure-community-platform"
# 文件名
mv community-gateway/src/main/java/com/measure/community/gateway/CloudGatewayApplication.java \
   community-gateway/src/main/java/com/measure/community/gateway/CommunityGatewayApplication.java
mv community-gateway/src/test/java/com/measure/community/gateway/CloudGatewayApplicationTests.java \
   community-gateway/src/test/java/com/measure/community/gateway/CommunityGatewayApplicationTests.java
mv community-auth/src/main/java/com/measure/community/auth/CloudUserApplication.java \
   community-auth/src/main/java/com/measure/community/auth/CommunityAuthApplication.java
mv community-auth/src/test/java/com/measure/community/auth/CloudUserApplicationTests.java \
   community-auth/src/test/java/com/measure/community/auth/CommunityAuthApplicationTests.java
# 内容(类名引用)
grep -rl --include=*.java -e 'CloudGatewayApplication' -e 'CloudUserApplication' . | while read f; do
  sed -i -e 's/CloudGatewayApplication/CommunityGatewayApplication/g' \
         -e 's/CloudUserApplication/CommunityAuthApplication/g' "$f"
done
echo done
```
Expected: `done`。

- [ ] **Step 4: 更新 AuthFilter 白名单字符串**

在 `community-gateway/src/main/java/com/measure/community/gateway/filter/AuthFilter.java` 中,把:
```java
    private static final List<String> EXCLUDE_PATH_LIST = List.of("/cloud-user/user/login");
```
改为(登录路径改名 + 放行样板只读接口,便于骨架验证;生产应移除样板放行):
```java
    private static final List<String> EXCLUDE_PATH_LIST = List.of(
            "/community-auth/user/login",
            "/api/v1/population");
```

- [ ] **Step 5: 批量替换 pom 坐标(仅 pom.xml)**

Run:
```bash
cd "//wsl.localhost/Ubuntu/home/measure/ideaProject/measure-community-platform"
find . -name pom.xml | while read f; do
  sed -i \
    -e 's#<groupId>com\.xf</groupId>#<groupId>com.measure</groupId>#g' \
    -e 's#spring-cloud-alibaba-base-demo#measure-community-platform#g' \
    -e 's#<artifactId>cloud-common</artifactId>#<artifactId>community-common</artifactId>#g' \
    -e 's#<artifactId>cloud-gateway</artifactId>#<artifactId>community-gateway</artifactId>#g' \
    -e 's#<artifactId>cloud-user</artifactId>#<artifactId>community-auth</artifactId>#g' \
    -e 's#<module>cloud-common</module>#<module>community-common</module>#g' \
    -e 's#<module>cloud-gateway</module>#<module>community-gateway</module>#g' \
    -e 's#<module>cloud-user</module>#<module>community-auth</module>#g' \
    -e 's#<name>cloud-gateway</name>#<name>community-gateway</name>#g' \
    -e 's#<name>cloud-user</name>#<name>community-auth</name>#g' \
    -e 's#<description>cloud-gateway</description>#<description>community-gateway</description>#g' \
    -e 's#<description>cloud-user</description>#<description>community-auth</description>#g' "$f"
done
grep -rn 'com\.xf\|cloud-common\|cloud-gateway\|cloud-user\|base-demo' --include=pom.xml . | head
```
Expected: 末尾 grep 无输出。

- [ ] **Step 6: 更新各模块 spring.application.name**

Run:
```bash
cd "//wsl.localhost/Ubuntu/home/measure/ideaProject/measure-community-platform"
sed -i 's/name: cloud-gateway/name: community-gateway/' community-gateway/src/main/resources/application.yml
sed -i 's/name: cloud-user/name: community-auth/'       community-auth/src/main/resources/application.yml
grep -rn 'cloud-gateway\|cloud-user' --include=*.yml . | head
```
Expected: 无输出。

- [ ] **Step 6b: 更新 Jenkinsfile(模块名/端口/镜像/仓库地址)**

Jenkinsfile 里硬编码了 demo 模块的 case、端口、镜像名与仓库地址,sed 覆盖不到,须单独改。
先做字符串替换,再手工修 case 块:
```bash
cd "//wsl.localhost/Ubuntu/home/measure/ideaProject/measure-community-platform"
sed -i -e 's/cloud-gateway/community-gateway/g' \
       -e 's/cloud-user/community-auth/g' \
       -e 's/spring-cloud-alibaba-base-demo/measure-community-platform/g' Jenkinsfile
```
然后把 Jenkinsfile 中的模块 `case` 块(原 cloud-consumer/gateway/producer/user 四项)整段替换为(删除 producer/consumer,新增 community-info):
```groovy
        case 'community-gateway': config.containerName = 'community-gateway'; config.containerPort = '9090'; config.imageName = 'community-gateway'; break
        case 'community-auth':    config.containerName = 'community-auth';    config.containerPort = '9093'; config.imageName = 'community-auth'; break
        case 'community-info':    config.containerName = 'community-info';    config.containerPort = '9094'; config.imageName = 'community-info'; break
```
并把 `GITHUB_REPO` 改为你的真实新仓库地址(占位:`git@github.com:<org>/measure-community-platform.git`)。

- [ ] **Step 7: 编译验证 + 残留扫描**

Run:
```bash
cd "//wsl.localhost/Ubuntu/home/measure/ideaProject/measure-community-platform"
mvn -q -DskipTests package 2>&1 | tail -5
echo "--- 残留检查(应全空;-I 跳过二进制,排除 .git/target) ---"
grep -rnI 'com\.xf\|cloud-common\|cloud-gateway\|cloud-user\|cloud-producer\|cloud-consumer\|base-demo' . \
  --exclude-dir=.git --exclude-dir=target | head
```
Expected: `BUILD SUCCESS`,reactor 模块显示为 community-common/community-gateway/community-auth;残留检查无输出(含 Jenkinsfile)。

- [ ] **Step 8: 提交**

Run:
```bash
git add -A
git commit -q -m "refactor: 统一改名为 com.measure / community-* 命名规范"
git log --oneline -1
```
Expected: 提交成功。

---

## Task 3: 新增样板模块 community-info(代码 + 单元测试)

本任务只做「可编译 + 控制器单测通过(mock service,无需 MySQL)」。真实库接线在 Task 4。

**Files:**
- Create: `community-info/pom.xml`
- Create: `community-info/src/main/java/com/measure/community/info/CommunityInfoApplication.java`
- Create: `.../info/model/entity/Population.java`
- Create: `.../info/model/req/PopulationQueryReq.java`、`PopulationCreateReq.java`
- Create: `.../info/support/AesTypeHandler.java`、`support/HmacUtil.java`
- Create: `.../info/mapper/PopulationMapper.java`
- Create: `.../info/service/PopulationService.java`、`service/impl/PopulationServiceImpl.java`
- Create: `.../info/controller/PopulationController.java`
- Create: `community-info/src/test/java/com/measure/community/info/controller/PopulationControllerTest.java`
- Modify: `pom.xml`(父 POM `<modules>` 增加 community-info)

**Interfaces:**
- Consumes: `com.measure.community.common.model.RetObj`(静态 `RetObj.success(T)`、`RetObj.error(String)`);common 的 `MybatisPlusConfig`(分页插件 + 审计填充,经 `@ComponentScan` 装配)。
- Produces:
  - `PopulationService.pagePersons(PopulationQueryReq req) -> RetObj`
  - `PopulationService.createPerson(PopulationCreateReq req) -> RetObj`
  - HTTP:`GET /api/v1/population/persons`、`POST /api/v1/population/persons`

- [ ] **Step 1: 建模块目录并注册到父 POM**

Run:
```bash
cd "//wsl.localhost/Ubuntu/home/measure/ideaProject/measure-community-platform"
mkdir -p community-info/src/main/java/com/measure/community/info/{controller,service/impl,mapper,model/entity,model/req,support}
mkdir -p community-info/src/main/resources
mkdir -p community-info/src/test/java/com/measure/community/info/controller
```
在父 `pom.xml` 的 `<modules>` 增加一行:
```xml
        <module>community-info</module>
```

- [ ] **Step 2: 写 community-info/pom.xml**

Create `community-info/pom.xml`:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.measure</groupId>
        <artifactId>measure-community-platform</artifactId>
        <version>0.0.1-SNAPSHOT</version>
        <relativePath>../pom.xml</relativePath>
    </parent>
    <artifactId>community-info</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <name>community-info</name>
    <description>信息服务:人房业态数据中枢</description>
    <properties>
        <java.version>17</java.version>
    </properties>
    <dependencies>
        <dependency>
            <groupId>com.measure</groupId>
            <artifactId>community-common</artifactId>
            <version>0.0.1-SNAPSHOT</version>
        </dependency>
    </dependencies>
    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```
(数据库/MyBatis-Plus/Redis/Nacos/Web 等依赖均由父 POM 与 community-common 传递,无需重复声明。)

- [ ] **Step 3: 写实体、请求对象、TypeHandler**

Create `.../info/model/entity/Population.java`:
```java
package com.measure.community.info.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.measure.community.info.support.AesTypeHandler;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName(value = "t_population", autoResultMap = true)
public class Population {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /** 户籍/常住/流动 */
    private String type;
    private String name;
    /** 证件号,AES 加密存储(占位) */
    @TableField(value = "id_card", typeHandler = AesTypeHandler.class)
    private String idCard;
    /** 证件号 HMAC 盲索引,用于唯一/等值精确匹配(见说明书§5) */
    @TableField("id_card_hmac")
    private String idCardHmac;
    private String gender;
    private String phone;
    @TableField("insured_status")
    private String insuredStatus;
    @TableField("employment_status")
    private String employmentStatus;
    private Integer version;
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableField(value = "create_by", fill = FieldFill.INSERT)
    private String createBy;
    @TableField(value = "update_by", fill = FieldFill.INSERT_UPDATE)
    private String updateBy;
}
```

Create `.../info/model/req/PopulationQueryReq.java`:
```java
package com.measure.community.info.model.req;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(name = "PopulationQueryReq", description = "人口分页查询请求")
@Data
public class PopulationQueryReq {
    @Schema(description = "类型:户籍/常住/流动")
    private String type;
    @Schema(description = "姓名(模糊匹配)")
    private String name;
    @Schema(description = "证件号(经盲索引等值精确匹配)")
    private String idCard;
    @Schema(description = "页码", example = "1")
    private Long pageNo = 1L;
    @Schema(description = "页大小", example = "10")
    private Long pageSize = 10L;
}
```

Create `.../info/model/req/PopulationCreateReq.java`:
```java
package com.measure.community.info.model.req;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(name = "PopulationCreateReq", description = "人口信息录入请求")
@Data
public class PopulationCreateReq {
    @Schema(description = "类型:户籍/常住/流动", example = "户籍")
    private String type;
    @Schema(description = "姓名", example = "张三")
    private String name;
    @Schema(description = "证件号")
    private String idCard;
    private String gender;
    private String phone;
    private String insuredStatus;
    private String employmentStatus;
}
```

Create `.../info/support/AesTypeHandler.java`:
```java
package com.measure.community.info.support;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import java.sql.*;

/**
 * 敏感字段 AES 加密接线占位。
 * 实际密钥管理/加解密见《详细功能设计说明书》第 5 章,后续接入 KMS。
 */
public class AesTypeHandler extends BaseTypeHandler<String> {
    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType) throws SQLException {
        ps.setString(i, encrypt(parameter));
    }
    @Override
    public String getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return decrypt(rs.getString(columnName));
    }
    @Override
    public String getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return decrypt(rs.getString(columnIndex));
    }
    @Override
    public String getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return decrypt(cs.getString(columnIndex));
    }
    // TODO 接入真实 AES/KMS,当前为直通占位
    private String encrypt(String v) { return v; }
    private String decrypt(String v) { return v; }
}
```

Create `.../info/support/HmacUtil.java`:
```java
package com.measure.community.info.support;

/**
 * 敏感字段盲索引(HMAC)接线占位。见《详细功能设计说明书》§5:
 * 加密字段落库为 AES-256 密文,另存一列 HMAC 盲索引用于唯一/等值精确匹配。
 */
public final class HmacUtil {
    private HmacUtil() {}
    /** TODO 接入真实 HMAC-SHA256 + 密钥,当前为直通占位(仅供骨架跑通) */
    public static String blindIndex(String plain) {
        return plain == null ? null : plain;
    }
}
```

- [ ] **Step 4: 写 mapper 与 service 接口**

Create `.../info/mapper/PopulationMapper.java`:
```java
package com.measure.community.info.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.measure.community.info.model.entity.Population;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PopulationMapper extends BaseMapper<Population> {
}
```

Create `.../info/service/PopulationService.java`:
```java
package com.measure.community.info.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.measure.community.common.model.RetObj;
import com.measure.community.info.model.entity.Population;
import com.measure.community.info.model.req.PopulationCreateReq;
import com.measure.community.info.model.req.PopulationQueryReq;

public interface PopulationService extends IService<Population> {
    RetObj pagePersons(PopulationQueryReq req);
    RetObj createPerson(PopulationCreateReq req);
}
```

- [ ] **Step 5: 写 service 实现**

Create `.../info/service/impl/PopulationServiceImpl.java`:
```java
package com.measure.community.info.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.measure.community.common.model.RetObj;
import com.measure.community.info.mapper.PopulationMapper;
import com.measure.community.info.model.entity.Population;
import com.measure.community.info.model.req.PopulationCreateReq;
import com.measure.community.info.model.req.PopulationQueryReq;
import com.measure.community.info.service.PopulationService;
import com.measure.community.info.support.HmacUtil;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class PopulationServiceImpl extends ServiceImpl<PopulationMapper, Population> implements PopulationService {

    @Override
    public RetObj pagePersons(PopulationQueryReq req) {
        LambdaQueryWrapper<Population> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(req.getType())) {
            qw.eq(Population::getType, req.getType());
        }
        // 姓名模糊匹配;证件号为密文列,无法 LIKE,只能经盲索引等值匹配(见§5)
        if (StringUtils.hasText(req.getName())) {
            qw.like(Population::getName, req.getName());
        }
        if (StringUtils.hasText(req.getIdCard())) {
            qw.eq(Population::getIdCardHmac, HmacUtil.blindIndex(req.getIdCard()));
        }
        qw.orderByDesc(Population::getCreateTime);
        Page<Population> page = this.page(new Page<>(req.getPageNo(), req.getPageSize()), qw);
        return RetObj.success(page);
    }

    @Override
    public RetObj createPerson(PopulationCreateReq req) {
        if (!StringUtils.hasText(req.getIdCard())) {
            return RetObj.error("证件号不能为空");
        }
        String hmac = HmacUtil.blindIndex(req.getIdCard());
        long exists = this.count(new LambdaQueryWrapper<Population>()
                .eq(Population::getIdCardHmac, hmac));
        if (exists > 0) {
            return RetObj.error("该证件号已存在");
        }
        Population p = new Population();
        p.setType(req.getType());
        p.setName(req.getName());
        p.setIdCard(req.getIdCard());
        p.setIdCardHmac(hmac);
        p.setGender(req.getGender());
        p.setPhone(req.getPhone());
        p.setInsuredStatus(req.getInsuredStatus());
        p.setEmploymentStatus(req.getEmploymentStatus());
        p.setVersion(1);
        this.save(p);
        return RetObj.success(p.getId());
    }
}
```

- [ ] **Step 6: 写 controller 与主类**

Create `.../info/controller/PopulationController.java`:
```java
package com.measure.community.info.controller;

import com.measure.community.common.model.RetObj;
import com.measure.community.info.model.req.PopulationCreateReq;
import com.measure.community.info.model.req.PopulationQueryReq;
import com.measure.community.info.service.PopulationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Tag(name = "人口信息(信息服务)")
@RestController
@RequestMapping("/api/v1/population")
public class PopulationController {

    @Autowired
    private PopulationService populationService;

    @Operation(summary = "人口信息分页查询")
    @GetMapping("/persons")
    public RetObj listPersons(PopulationQueryReq req) {
        return populationService.pagePersons(req);
    }

    @Operation(summary = "人口信息录入")
    @PostMapping("/persons")
    public RetObj createPerson(@RequestBody PopulationCreateReq req) {
        return populationService.createPerson(req);
    }
}
```

Create `.../info/CommunityInfoApplication.java`:
```java
package com.measure.community.info;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@EnableDiscoveryClient
@ComponentScan(basePackages = {"com.measure.community.common", "com.measure.community.info"})
public class CommunityInfoApplication {
    public static void main(String[] args) {
        SpringApplication.run(CommunityInfoApplication.class, args);
    }
}
```

- [ ] **Step 7: 写失败的控制器测试**

Create `community-info/src/test/java/com/measure/community/info/controller/PopulationControllerTest.java`:
```java
package com.measure.community.info.controller;

import com.measure.community.common.model.RetObj;
import com.measure.community.info.model.req.PopulationCreateReq;
import com.measure.community.info.model.req.PopulationQueryReq;
import com.measure.community.info.service.PopulationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PopulationControllerTest {

    @Mock
    PopulationService populationService;

    @InjectMocks
    PopulationController populationController;

    MockMvc mockMvc;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.standaloneSetup(populationController).build();
    }

    @Test
    void listPersons_returnsOk() throws Exception {
        when(populationService.pagePersons(any(PopulationQueryReq.class)))
                .thenReturn(RetObj.success("paged"));
        mockMvc.perform(get("/api/v1/population/persons").param("pageNo", "1"))
                .andExpect(status().isOk());
    }

    @Test
    void createPerson_returnsOk() throws Exception {
        when(populationService.createPerson(any(PopulationCreateReq.class)))
                .thenReturn(RetObj.success(1L));
        mockMvc.perform(post("/api/v1/population/persons")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"张三\",\"idCard\":\"3301X\",\"type\":\"户籍\"}"))
                .andExpect(status().isOk());
    }
}
```

- [ ] **Step 8: 先跑测试确认能编译并通过(先不含 resources 时可能因缺 yml 无影响,standalone 不启动容器)**

Run:
```bash
cd "//wsl.localhost/Ubuntu/home/measure/ideaProject/measure-community-platform"
mvn -q -pl community-info -am test 2>&1 | tail -15
```
Expected: `PopulationControllerTest` 2 个用例 `Tests run: 2, Failures: 0`,`BUILD SUCCESS`。
(standalone MockMvc 不加载 Spring 容器,故此时无 application.yml/无 MySQL 也能跑。)

- [ ] **Step 9: 提交**

Run:
```bash
git add -A
git commit -q -m "feat(info): 新增 community-info 人口样板模块及控制器单测"
git log --oneline -1
```
Expected: 提交成功。

---

## Task 4: 真实 MySQL 接线 + Nacos 配置 + 网关路由 + 建表脚本

**Files:**
- Create: `database/mysql/01-init-schema.sql`
- Create: `community-info/src/main/resources/application.yml`
- Create: `community-info/src/main/resources/application-local.yml`
- Create: `doc/community-info-dev.yaml`
- Create: `doc/community-gateway-dev.yaml`

**Interfaces:**
- Consumes: Task 3 的 community-info 模块;Task 2 的网关(路由 `lb://community-info`)。
- Produces: 可在有 Nacos + MySQL 时端到端跑通 `/api/v1/population/**`。

- [ ] **Step 1: 建表脚本**

Create `database/mysql/01-init-schema.sql`:
```sql
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
```

- [ ] **Step 2: community-info 的 application.yml(Nacos 注册+配置,对齐 community-auth)**

Create `community-info/src/main/resources/application.yml`:
```yaml
spring:
  application:
    name: community-info
  profiles:
    active: dev
  cloud:
    nacos:
      discovery:
        server-addr: ${NACOS_SERVER_ADDR:127.0.0.1:8848}
        username: ${NACOS_USERNAME:nacos}
        password: ${NACOS_PWD:nacos}
        namespace: 74193cd9-fac4-4f2a-addc-47c60508b15c
        cluster-name: DEFAULT
      config:
        server-addr: ${NACOS_SERVER_ADDR:127.0.0.1:8848}
        username: ${NACOS_USERNAME:nacos}
        password: ${NACOS_PWD:nacos}
        file-extension: yaml
        namespace: 74193cd9-fac4-4f2a-addc-47c60508b15c
  config:
    import:
      - nacos:${spring.application.name}-${spring.profiles.active}.${spring.cloud.nacos.config.file-extension}
      - nacos:redis-common.yaml
      - nacos:common-config.yaml
```

- [ ] **Step 3: community-info 的 application-local.yml(数据源,对齐 community-auth 风格)**

Create `community-info/src/main/resources/application-local.yml`:
```yaml
server:
  port: 9094
management:
  endpoints:
    web:
      exposure:
        include: "*"
spring:
  datasource:
    dynamic:
      primary: master
      strict: false
      datasource:
        master:
          url: jdbc:mysql://${SERVER_ADDRESS:127.0.0.1}:3306/measure_community?useUnicode=true&characterEncoding=utf-8&allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=Asia/Shanghai
          username: root
          password: root
          driver-class-name: com.mysql.cj.jdbc.Driver
  cloud:
    loadbalancer:
      nacos:
        enabled: true
```

- [ ] **Step 4: Nacos 业务配置模板 community-info-dev.yaml**

Create `doc/community-info-dev.yaml`:
```yaml
# 导入 Nacos(命名空间 74193cd9-...)后作为 community-info 的业务配置。
# 生产用真实数据源覆盖 application-local.yml。
server:
  port: 9094
spring:
  datasource:
    dynamic:
      primary: master
      strict: false
      datasource:
        master:
          url: jdbc:mysql://${SERVER_ADDRESS:127.0.0.1}:3306/measure_community?useUnicode=true&characterEncoding=utf-8&allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=Asia/Shanghai
          username: root
          password: root
          driver-class-name: com.mysql.cj.jdbc.Driver
```

- [ ] **Step 5: Nacos 网关路由配置 community-gateway-dev.yaml**

Create `doc/community-gateway-dev.yaml`:
```yaml
# 导入 Nacos 后作为 community-gateway 的路由配置。
spring:
  cloud:
    gateway:
      discovery:
        locator:
          enabled: false
      routes:
        - id: community-info
          uri: lb://community-info
          predicates:
            - Path=/api/v1/population/**
        - id: community-auth
          uri: lb://community-auth
          predicates:
            - Path=/community-auth/**
          filters:
            - StripPrefix=1
```

- [ ] **Step 6: 全量编译验证**

Run:
```bash
cd "//wsl.localhost/Ubuntu/home/measure/ideaProject/measure-community-platform"
mvn -q -DskipTests package 2>&1 | tail -6
```
Expected: `BUILD SUCCESS`,reactor 含 4 模块(community-common/gateway/auth/info)。

- [ ] **Step 7: 提交**

Run:
```bash
git add -A
git commit -q -m "feat(info): 接入真实 MySQL 数据源、建表脚本、Nacos 配置与网关路由"
git log --oneline -1
```
Expected: 提交成功。

---

## Task 5: 改写 README + 新增模块指南

**Files:**
- Modify: `README.md`(整篇替换为本项目说明)

**Interfaces:**
- Consumes: 前述所有产物(命名、模块、Nacos 配置清单、DDL、启动方式)。

- [ ] **Step 1: 覆盖 README.md**

将 `README.md` 整篇替换为:
```markdown
# 数智化社区服务平台(measure-community-platform)

面向区/街镇/社区三级治理 + 居民 + 物业的一体化微服务平台。基于 Spring Cloud Alibaba(Nacos/Sentinel + 脚手架自带 Seata/RocketMQ)。

> 注:设计说明书 3.2 消息队列用 RabbitMQ/Kafka(未含 RocketMQ/Seata)。本骨架暂沿用脚手架自带的 RocketMQ/Seata 且不实现 MQ/分布式事务;后续落地时按说明书对齐。

## 模块

| 模块 | 说明 | 包根 | 端口 |
| --- | --- | --- | --- |
| community-common | 公共:统一响应、异常、MyBatis-Plus、Redis 等 | com.measure.community.common | - |
| community-gateway | API 网关、统一鉴权、路由 | com.measure.community.gateway | - |
| community-auth | 用户/鉴权(JWT) | com.measure.community.auth | 9093 |
| community-info | 信息服务(人房业态,样板模块) | com.measure.community.info | 9094 |

> AI 智能体(FastAPI)为独立技术栈、独立仓库,经网关/内部 REST 对接,不在本工程。

## 环境准备
JDK 17+、Maven 3.8+、Nacos 2.x(必须)、MySQL 8、Redis。

## 启动步骤
1. **建库**:执行 `database/mysql/01-init-schema.sql`(库名 `measure_community`)。
2. **Nacos 命名空间**:新建命名空间,手填 ID `74193cd9-fac4-4f2a-addc-47c60508b15c`(免改代码)。
3. **导入 Nacos 配置**(命名空间下):`doc/` 内
   `common-config.yaml`、`redis-common.yaml`、`seata-common.yaml`、`rocketmq-common.yaml`、
   `community-gateway-dev.yaml`、`community-info-dev.yaml`。
4. **环境变量**(可选):`NACOS_SERVER_ADDR`、`NACOS_USERNAME`、`NACOS_PWD`、`SERVER_ADDRESS`(MySQL 主机)。
5. **启动**:`community-gateway` → `community-info`。到 Nacos 控制台确认注册成功。
   (`community-auth` 由脚手架 cloud-user 改名而来,如需启动须另在 Nacos 建 `community-auth-dev.yml` 及其数据源;本骨架验证只需 gateway + info。)
6. **验证**:
   - `POST http://<gateway>/api/v1/population/persons` body `{"type":"户籍","name":"张三","idCard":"3301X"}`
   - `GET  http://<gateway>/api/v1/population/persons?pageNo=1&pageSize=10`

## 如何新增业务模块(以 community-xxx 为例)
1. 复制 `community-info` 为 `community-xxx`,改 `pom.xml` 的 `<artifactId>`。
2. 包 `com.measure.community.info` → `com.measure.community.xxx`;主类 `CommunityXxxApplication`,`@ComponentScan` 含 `com.measure.community.common` 与本模块包。
3. `application.yml` 改 `spring.application.name: community-xxx`。
4. 父 `pom.xml` 的 `<modules>` 增加 `community-xxx`。
5. Nacos 建 `community-xxx-dev.yaml`;网关 `community-gateway-dev.yaml` 增加对应路由。
6. 按 `controller/service/service.impl/mapper/model` 分层开发。

## 命名规范
groupId `com.measure`;模块前缀 `community-`;包根 `com.measure.community.{模块}`;
主类 `Community{Module}Application`;`spring.application.name` = `community-{module}`。

License: Apache 2.0
```

- [ ] **Step 2: 提交**

Run:
```bash
cd "//wsl.localhost/Ubuntu/home/measure/ideaProject/measure-community-platform"
git add -A
git commit -q -m "docs: 改写 README 为社区平台说明并补充新增模块指南"
git log --oneline -1
```
Expected: 提交成功。

---

## Task 6: 端到端验证(需 Nacos + MySQL,手动 runbook)

无 Nacos/MySQL 时本步跳过,但必须在有环境时执行以满足 spec §8 成功判据。

- [ ] **Step 1: 起依赖**:本地或容器启动 Nacos 2.x(8848)、MySQL 8(3306)、Redis(6379)。执行建库脚本:
```bash
mysql -h 127.0.0.1 -uroot -proot < database/mysql/01-init-schema.sql
```
Expected: 无错误,`measure_community` 库下有 `t_population`、`t_population_his`。

- [ ] **Step 2: Nacos 命名空间 + 导入 6 个配置**(见 README 步骤 2、3)。

- [ ] **Step 3: 启动网关与信息服务**:
```bash
# 先把各模块装进本地仓库,避免单模块 run 解析不到 community-common
mvn -q install -DskipTests
mvn -q -pl community-gateway spring-boot:run &   # 或 IDEA 运行 CommunityGatewayApplication
mvn -q -pl community-info    spring-boot:run &   # 或 IDEA 运行 CommunityInfoApplication
```
Expected: Nacos 控制台「服务列表」出现 `community-gateway`、`community-info`。

- [ ] **Step 4: 冒烟测试(经网关)**:
```bash
GW=127.0.0.1:<gateway端口>
curl -s -X POST "http://$GW/api/v1/population/persons" \
  -H 'Content-Type: application/json' \
  -d '{"type":"户籍","name":"张三","idCard":"3301X0001"}'
curl -s "http://$GW/api/v1/population/persons?pageNo=1&pageSize=10"
```
Expected: POST 返回 `{"code":200,...,"data":<id>}`;GET 返回分页对象,`records` 含刚写入记录,`total>=1`。数据库 `t_population` 有对应行。

- [ ] **Step 5: 负路径确认(spec §8)**:停掉 MySQL 再启动 community-info,日志应为数据源连接失败,而非类加载/配置结构错误。

---

## Self-Review

**1. Spec coverage(逐条对照 spec):**
- §2 命名规范 → Task 2(Global Constraints 全覆盖)✅
- §3 工程结构/删 demo → Task 1、Task 2 ✅
- §4 样板模块 community-info、`/api/v1/population`、t_population(+his)、GET/POST 两接口、AES 占位 → Task 3、Task 4 ✅
- §5 敏感字段 AES-256 密文 + HMAC 盲索引(TypeHandler)→ Task 3(AesTypeHandler/HmacUtil/idCardHmac)、Task 4(t_population.id_card_hmac 唯一键)✅ 已按 docx L2471 修正,唯一/等值走盲索引而非密文列
- §5 Nacos 策略(命名空间、doc 配置、{app}-dev.yml、环境变量)→ Task 4、Task 5 ✅
- §6 AI 独立仓 → README 标注(Task 5)✅
- §7 文档核实结论 → 已并入设计与命名 ✅
- §8 验证标准 → Task 3(单测)、Task 6(端到端)✅
- §9 YAGNI(不做其他模块/加密全链路/AI)→ 计划未纳入 ✅

**2. Placeholder scan:** 无 TBD/未完成步骤。仅 `AesTypeHandler` / `HmacUtil` 内含 `// TODO 接入 AES/HMAC` —— 为 spec §4/§5 明确要求的"接线占位",非计划缺口。✅

**3. Type consistency:** `pagePersons(PopulationQueryReq)`、`createPerson(PopulationCreateReq)` 在 service 接口、实现、controller、测试中一致;`RetObj.success/error` 与 common 定义一致;实体字段 `createTime/updateTime/createBy/updateBy` 与 common `MybatisPlusConfig` 的填充属性名一致。✅
