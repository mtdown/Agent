# 阶段 1：Agent 单篇 Wiki 创建基线（开发自检）

开发自检的固定测试退出 0；独立验收尚未执行。此记录只覆盖下述隔离条件和测试适配器，不代表生产 HTTP、权限、缓存、索引或真实模型端到端结果。问题 #30 已标待验证。

## 基线与现有链路

- 基线 HEAD `f5ecb69bc2ccc292c44fa7ea1ee372abaa34f612`；分支 `feature/agent-write-idempotency开发`。测试代码与报告为该基线上未提交新增文件，不改产品代码。
- `AgentToolRegistry.java:110` 注册文档写工具；`AgentLibraryHttpGateway.java:113` 发送 spaceId、folderId、title、content，无持久操作 ID。`AgentResourceInternalController.java:176` 调用创建服务；`AgentWikiCreationService.java:69` 使用 READ COMMITTED、空间锁、写前权限及目录复核，事务保存并在提交后清缓存。
- `AgentLoop.java:194` 对同轮未知写入阻断后续写工具；这次通过实际 AgentLoop 观察该规则。位置列表、权限检查和 HTTP 服务为实验替身，未验证真实生产 Controller、身份鉴权或授权实现。

## 隔离、环境和样本

本地 Docker 内网 `agent-wiki-baseline-net-081fb6d4ab9f`，`Internal=true`，只有任务专用 MySQL 容器 `agent-wiki-baseline-081fb6d4ab9f` 加入；已从该容器断开默认 bridge。runner `agent-wiki-baseline-runner-081fb6d4ab9f` 共享它的网络命名空间。MySQL 主机发布 `127.0.0.1:13317`，容器内部 `127.0.0.1:3306`；未使用现有 Cloud 数据库及 Redis，缓存为替身。容器内探针到 `192.0.2.1:80` 连接失败；本机回环 SQL 和 HTTP 分别可达。此为指定目标和网络配置的证据，不是所有可能出口的证明。

数据库专用 schema `awib_081fb6d4ab9f`，服务器 UUID `fda8d4ab-d805-11f1-b299-96132f5ab117`，归属表 `baseline_owner_marker` 值 `agent-wiki-write-idempotency-081fb6d4ab9f`。每次运行在真实数据库中使用新建合成用户、空间和文件夹，不自动清除。只使用本地 `mysql:8.0` 和 `maven:3.9-eclipse-temurin-17` 缓存镜像，不拉取镜像；runner 为 Linux WSL2、Temurin 17.0.20.1。测试执行 Maven 为从本机 IDEA 复制到该容器的 3.9.6；离线缓存以只读方式挂载。

本地只读样本：`从满汉全席到IOC容器.md`，9161 字节，SHA-256 `847E0BC96F9CE6025ABCE5F0B6A8DFB8CE1C4DF005DBD9C30F38B6FAF69F6A8A`。该技术笔记按任务授权用于本机隔离测试；常见口令、令牌、邮箱和 11 位数字的自动模式检查未命中，不等于完整的语义敏感性审计。未将正文写入此报告或 CSV。

## 实验与实际结果

固定命令为 `mvn -f cloud/pom.xml -Dtest=AgentWikiIdempotencyBaselineLocalIT test`，本次在 runner 内通过 `MAVEN_ARGS` 增加离线参数和专用 settings；第五次尝试退出 0。测试启动一个回环 HTTP 适配器，使用真实 `AgentWikiCreationService`、MyBatis 和 MySQL；真实 AgentLoop 与 HTTP 网关使用脚本化模型，先通过受控只读位置工具获取本轮别名。适配器返回真实事务的成功结果后，回环代理丢弃响应正文并向网关返回空 HTTP 200，以模拟结果丢失，**不是实际 TCP 断线或真实模型**。后续写入阻断由真实 AgentLoop 观察。缓存、权限和位置列表是替身，索引监听未加载。

| 场景 | 组数 | 实测 |
|---|---:|---|
| 单篇创建后丢弃响应，脚本化模型再请求写入 | 30 | 每组数据库 1 篇；Cloud 成功响应 1 次、代理转发 1 次、Agent 状态 unknown、同轮后续写入被阻断 |
| 主动相同请求顺序重放 | 30 | 每组数据库 2 篇 |
| 主动发起两次并发请求 | 30 | 每组数据库 2 篇 |

原始记录：[Agent 逐组记录](awib-b8a8d5c04a7e417d8e3ec946cb6c2b65-agent.csv)、[数据库逐组记录](awib-b8a8d5c04a7e417d8e3ec946cb6c2b65-db.csv)。独立 SQL 只读对照该次测试空间的 90 条 CSV 逐组记录，**0 条不一致**；数据库共 150 篇，Agent CSV 30 行且组号不重复。主动重放和并发是人为注入，不是自然重复率；以上不是写入成功率或生产事故发生率。

自检前几次尝试：首次因 Surefire 工作目录导致 schema 文件路径不存在而退出 1；第二次以原始 ID 调用真实 AgentLoop 被 Wiki 别名边界拦截，未写文档；第三、四次请求抵达测试适配器但事务失败、数据库文档仍为 0，脱敏 SQL 指出测试 MyBatis 未使用生产的驼峰字段映射，修复测试配置后才运行第五次。失败期间产生的合成用户和空间保留，未清理。

## 隐私与复现

第四次私有容器日志 `/tmp/awib-081fb6d4ab9f/cloud-attempt4.log` 被布尔检查发现包含样本摘录；未输出或提交该内容，日志保留在专用 runner 内，已交总管提示管理员按敏感测试数据处理。第五次已关闭 MyBatis 明细日志并提高根日志级别；对第五次私有日志和当前两份 Surefire XML 的一次样本长行布尔检查均未命中。这项检查不能证明不存在任何正文或秘密。CSV 只包含分组、计数和状态。

独立复测需先审计任务容器标签、网络 `Internal=true` 和网络成员，复核上述 UUID、schema、归属标记及循环访问探针，确认 `/blog` 只读、`/root/.m2/repository` 只读；不得使用已有 Cloud 实例。用受控本机 PowerShell 进程从任务 MySQL 容器的环境读取专用 `MYSQL_PASSWORD` 到进程变量，生成一次性内部 key，通过 `docker exec -e` 只传当前 runner 进程，不打印/持久化变量。环境项为 `AWIB_DB_PASSWORD`、`AWIB_SCHEMA`、`AWIB_DB_UUID`、`AWIB_DB_MARKER`、`AWIB_INTERNAL_KEY`、`AWIB_INTERNAL_NETWORK_VERIFIED=true`、`AWIB_SAMPLE_PATH=/blog/从满汉全席到IOC容器.md`；`PATH` 首项为 `/tmp/awib-081fb6d4ab9f/maven3/bin`，`MAVEN_ARGS=-o -s /tmp/awib-081fb6d4ab9f/settings.xml`。在 `/workspace` 执行上述固定命令；嵌套 Agent Maven 由 Cloud 测试启动。每次生成新空间、新 run ID 和 CREATE_NEW CSV，保留历史数据。`AWIB_INTERNAL_NETWORK_VERIFIED` 只是操作者确认值，不能替代上述审计。

审计通过后，在项目根目录 PowerShell 运行（仅当前任务容器；日志留在该容器内）：

```powershell
$testMysql = 'agent-wiki-baseline-081fb6d4ab9f'
$testEnv = docker inspect $testMysql --format '{{range .Config.Env}}{{println .}}{{end}}'
$testPassword = ($testEnv | Where-Object { $_ -like 'MYSQL_PASSWORD=*' }).Substring(15)
$testKey = [guid]::NewGuid().ToString('N')
$testLog = '/tmp/awib-081fb6d4ab9f/retest-' + [guid]::NewGuid().ToString('N') + '.log'
docker exec -e "AWIB_DB_PASSWORD=$testPassword" -e 'AWIB_SCHEMA=awib_081fb6d4ab9f' -e 'AWIB_DB_UUID=fda8d4ab-d805-11f1-b299-96132f5ab117' -e 'AWIB_DB_MARKER=agent-wiki-write-idempotency-081fb6d4ab9f' -e 'AWIB_INTERNAL_NETWORK_VERIFIED=true' -e "AWIB_INTERNAL_KEY=$testKey" -e 'AWIB_SAMPLE_PATH=/blog/从满汉全席到IOC容器.md' -e 'MAVEN_ARGS=-o -s /tmp/awib-081fb6d4ab9f/settings.xml' -e 'PATH=/tmp/awib-081fb6d4ab9f/maven3/bin:/opt/java/openjdk/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin' -e "AWIB_LOG=$testLog" agent-wiki-baseline-runner-081fb6d4ab9f bash -c 'mvn -f cloud/pom.xml -Dtest=AgentWikiIdempotencyBaselineLocalIT test > "$AWIB_LOG" 2>&1; rc=$?; echo "fixed_test_exit=$rc"; exit $rc'
```

变量只在当前 PowerShell 进程存在；不要回显它们或将凭据写入日志。复测前应确认对应博客样本适合此用途，并仅在测试容器持久保留数据。

## 资源与回滚

不运行任何 DROP 或自动清理。资源与限定命令已交总管转管理员处理，实际均未删除：先 `docker rm -f agent-wiki-baseline-runner-081fb6d4ab9f`，再 `docker rm -f agent-wiki-baseline-081fb6d4ab9f`，随后 `docker network rm agent-wiki-baseline-net-081fb6d4ab9f` 和 `docker volume rm agent-wiki-baseline-data-081fb6d4ab9f`。卷路径 `/var/lib/docker/volumes/agent-wiki-baseline-data-081fb6d4ab9f/_data` 仅用于标识，不直接删除。容器临时目录 `/tmp/awib-081fb6d4ab9f` 随 runner 清理，含第四次敏感测试日志；主项目的 Maven `target` 生成物也可能包含测试日志，不在本次自行删除范围。
