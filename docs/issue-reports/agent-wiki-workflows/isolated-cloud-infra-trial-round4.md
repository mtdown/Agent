# Cloud 隔离测试基础设施试运行，第 4 轮

本轮为测试基础设施试运行，不是独立验收。使用 Linux 容器及 Maven 3.9.6，结果不能代表 Windows 测试通过。本轮任务标识 codex-wiki-infra4-mvwjdiax。

## 测试环境与预检

- 使用本机已有 maven:3.9-eclipse-temurin-17、mysql:8.0、redis:7-alpine 镜像，未拉取镜像；从 IDEA Maven 分发只读复制 bin、boot、lib，共 83 个文件，源与副本及容器内摘要均一致。未复制 IDEA conf、settings 或凭据，未修改宿主 Maven 仓库或配置。
- 330 个源码及测试快照文件与第 2 轮清单的源摘要一致，无增删，容器内快照摘要逐项通过；仅测试快照的 application-local.yml 中四个字面量敏感值替换为占位符，配置并非逐字节相同。最终测试前再次核对原始源码摘要无变化。
- 本轮独立 internal 网络无默认 IPv4 路由，三个容器未发布宿主端口；到网络网关的 3307、6379 和外部 1.1.1.1:443 的 TCP 探测未能连接。runner 无 Docker socket，唯一宿主挂载是只读 Maven repository；用户目录、宿主运行 tmp、.env、既有数据库卷均未挂载。临时主目录、Java user.home、项目目录及 Agent 文件/上传目录均位于容器内。
- 临时 MySQL 数据库使用本轮 schema 初始化 11 张表。通过 Connector/J 实际连接，数据库名、JDBC URL 和服务 UUID 均匹配专属实例；Redis TCP PING 及本轮专属标记匹配。最小 Spring Boot local 配置探针确认数据库、Redis 及两个 Agent 文件路径均指向隔离服务或容器内路径；空 Redis 密码和临时 MySQL 凭据仅用于本轮。
- 新建空 logging 目录，显式使用本轮新建的无凭据全局与用户 settings，offline 模式，用户 settings 仅声明离线缓存使用的 alimaven、aliyunmaven 仓库和插件仓库 ID，地址为不可用回环占位；MAVEN_SKIP_RC=1，MAVEN_ARGS、MAVEN_OPTS、MAVEN_CONFIG 清空。预检后实测 Maven 3.9.6、Java 17.0.20.1。没有下载项目依赖。

## 结果

使用复制到 /maven396 的 Maven，所有命令附加 -o、无凭据 -gs/-s，并顺序执行：

- D1：mvn -f cloud/pom.xml "-Dtest=AgentConversationPersistenceMappingTest,AgentConversationStoreTest" test；退出 0，7 项，失败 0、错误 0、跳过 0。
- D2 定向：mvn -f cloud/pom.xml "-Dtest=AgentConversationStoreTest,AgentPreviewControllerTest,AgentPreviewProxyWikiEventTest" test；退出 0，13 项，失败 0、错误 0、跳过 0。
- D2 全量：mvn -f cloud/pom.xml test；退出 0，383 项，失败 0、错误 0、跳过 1。跳过的是 AgentPersonalFileStoreTest.refusesWindowsDirectoryJunctionEscape，原因是运行环境不满足该 Windows 测试的前置假设。

脱敏结果摘要分别为 isolated-cloud-d1-round4-summary.log、isolated-cloud-d2-targeted-round4-summary.log、isolated-cloud-d2-full-round4-summary.log；只保留测试类及统计、构建结果。完整原始运行日志保留在本轮私有临时目录，未纳入项目报告，避免将测试日志中的无关信息带入共享文档。

## 清理与边界

清理前核验网络标签、名称、完整 ID、internal 标志和连接成员，以及每个容器标签、名称和完整 ID；只删除本轮三个容器及其专属匿名卷和本轮网络。清理后本轮标签的容器、网络和卷均无残留，两个已记录匿名卷的 inspect 均返回不存在。本轮本机临时目录 C:/Temp/codex-wiki-infra4-mvwjdiax 保留供审计；未触碰之前轮次残留目录，未尝试递归删除。

结果反映隔离环境内该快照的测试表现，不改变问题状态，也不替代独立测试或 Windows 测试。

