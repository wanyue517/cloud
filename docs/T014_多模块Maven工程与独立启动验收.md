# T014 Spring Boot 多模块 Maven 工程与独立启动验收

日期：2026-11-26。状态：**已验收**。

## 范围与架构对齐

对照需求说明书（第 3.3 节 `cloud-api` / `cloud-worker` / `cloud-common` 模块拆分与独立部署单元要求）及原计划 T014（初始化 Spring Boot 多模块 Maven 工程，验收：API 与 Worker 可分别启动），将原单模块 `backend` 演进为标准多模块 Maven 工程：

- **父聚合工程（[backend/pom.xml](file:///E:/wanyue/projects/cloud/backend/pom.xml)）**：
  - `artifactId: cloud-parent`（`<packaging>pom</packaging>`），统一管理依赖版本（`dependencyManagement`）、Lombok/ConfigurationProcessor 注解处理器与 `maven-surefire-plugin`（配置 `failIfNoTests=false`，保证从根目录按 `-Dtest=...` 定向运行单测兼容 `scripts/verify-t008-live.py`）。
  - 聚合子模块：`cloud-common`、`cloud-api`、`cloud-worker`。
- **共享基础库（`backend/cloud-common`）**：
  - 抽离跨模块共享契约：`BaseDO`、`BizCode`、`BizException`、`PageParam`、`PageResult`、`R`；仅引入轻量基础依赖（不引入 Tomcat/Security/Redis），产出标准库 JAR。
- **Web API 启动模块（`backend/cloud-api`）**：
  - 承载原 `CloudApplication`、全部业务模块、Flyway 迁移、Redis Lua 脚本及 59 项后端测试，依赖 `cloud-common`，经 `spring-boot-maven-plugin` 打包为独立可执行 JAR（`cloud-api-0.0.1-SNAPSHOT.jar`）。
  - `application.yaml` 支持 `optional:file:./application-local.yaml,optional:file:../application-local.yaml`，兼容从 `backend/` 或 `backend/cloud-api/` 启动；`FrontendSessionE2ETest` 兼容多模块路径定位浏览器脚本。
- **独立 Worker 启动模块（`backend/cloud-worker`）**：
  - 新增独立入口 [CloudWorkerApplication.java](file:///E:/wanyue/projects/cloud/backend/cloud-worker/src/main/java/com/wanyue/cloud/worker/CloudWorkerApplication.java)（`@EnableScheduling`、`spring.main.web-application-type=none`，不启动 Web 容器、不占用 API 端口），配合 `WorkerRuntimeProperties` 与 `WorkerLifecycleRunner` 验证对 `cloud-common` 的复用，为后续阶段文档转换、视频转码、全文索引与孤儿清理消费者提供独立运行载体。
- **容器与编排同步**：
  - 更新 [backend/Dockerfile](file:///E:/wanyue/projects/cloud/backend/Dockerfile) 支持 `ARG MODULE=cloud-api` 按模块构建独立镜像；在 [compose.yaml](file:///E:/wanyue/projects/cloud/compose.yaml) 中补充独立的 `worker` 服务配置。

## 验证结果

| 项目 | 实际结果 |
|---|---|
| 多模块构建 | `mvn -f backend/pom.xml -B package` 全部 4 模块（`cloud-parent`、`cloud-common`、`cloud-api`、`cloud-worker`）`BUILD SUCCESS` |
| API 回归测试 | `cloud-api` 59/59 项常规测试全部通过（1 项 `FrontendSessionE2ETest` 按环境变量条件跳过），成功打包 `cloud-api-0.0.1-SNAPSHOT.jar` |
| Worker 独立测试 | `cloud-worker` 的 `CloudWorkerApplicationTests` 通过，断言无 `dispatcherServlet`、属性绑定及 `cloud-common` 复用正常，成功打包 `cloud-worker-0.0.1-SNAPSHOT.jar` |
| Worker JAR 独立启动 | 直接执行 `java -jar backend/cloud-worker/target/cloud-worker-0.0.1-SNAPSHOT.jar --cloud.worker.node-id=worker-jar-smoke --spring.main.keep-alive=false`，0.81 秒独立启动成功并输出 `WorkerLifecycleRunner` 就绪日志 |

> 注：本机环境当前为 Java 17 / Spring Boot 4.2.0，沿用现有运行基线；后续如需升级 Java 21 可在父 POM 统一调整 `<java.version>`。
