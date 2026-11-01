# 会话映射任务本机全量隔离自检

2026-11-01，任务分支 feature/agent-wiki-workflows开发。仅新增测试夹具 LocalFullTestSchema.java、修改测试 CloudApplicationTests.java；没有更改生产代码、固定验收断言、依赖或数据库迁移。使用现有本机 127.0.0.1:3307 MySQL 服务，未操作容器或现有库。

夹具创建 `codex_full_it_` 加 32 位随机标识独有 schema，先验证 JDBC 端点、服务端口与 UUID、空库及所有权标识，从部署 SQL 提取 11 张表 CREATE 白名单（不执行 DROP）；应用 DataSource 每次取连接都在交给 Spring 使用之前验证端点、数据库名、服务 UUID 和归属标识。数据库启动副作用只发生在测试库。缓存业务使用 mock，Redis 目标设 127.0.0.1:1、会话使用非 Redis 存储；RAG 索引使用 mock；Agent 文件与上传根使用新建独有临时目录；外部服务凭据设占位值。夹具结束时再次验证归属后删除测试 schema，并查询 information_schema 确认删除；不删除文件目录。没有访问现有 Redis 业务数据。

预检：指定 CloudApplicationTests 测试，退出 0，1 项通过、0 跳过；专属 schema 9f170e9b7708424da94fff77d96f9dc8 创建并核验移除。固定全量 `mvn -f cloud/pom.xml test` 退出 0，383 项、0 失败、0 错误、1 跳过；专属 schema c6662b5635524dd0819e5ff3491e339b 创建并核验移除。跳过为 AgentPersonalFileStoreTest.refusesExistingSymlinkAsParent，Windows 创建符号链接不可用触发原 assumption；未修改该断言。全量控制台输出在本地 tmp/cloud-full-isolation-selfcheck.log，不作为可公开附件，可能含调试数据。

此结果为开发自检、非独立验收。现有 AgentWikiChoiceLocalIT 将数据库 URL 硬编码为既有 Cloud，不能在独有测试库执行且不得对既有库实施启动写入。故真实历史数据库升级及写后会话重开仍未取得本机安全证据；完整 D2 仍需总管核实。清理与回滚：撤销以上两个测试文件改动；不要清理其他会话目录或任何数据库。

安全事件：本次配置审查时一个敏感配置值出现在子任务 dev_conversation_fix 的配置审查工具输出中，已登记问题 #9；未在本报告记录值，也未擅自轮换。
