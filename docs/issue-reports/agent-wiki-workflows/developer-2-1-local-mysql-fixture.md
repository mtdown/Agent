# 阶段 2.1 本机 MySQL 并发测试夹具修复自检

任务：仅修正 AgentUrlImportConcurrencyLocalIT 的本地测试环境；不修改业务代码、测试行为断言或 OpenSpec。环境 Windows、Java 17、IDEA Maven 3.9.6；项目分支和范围以总管冻结快照为准。本报告是开发自检，不是独立验收。

修改：cloud/src/test/java/com/et/cloud/service/impl/AgentUrlImportConcurrencyLocalIT.java。移除 docker run、exec、port、inspect 和 rm 逻辑及容器生命周期；保留原 15 项测试与断言。通过 WIKI_IT_MYSQL_USER、WIKI_IT_MYSQL_PASSWORD、WIKI_IT_MYSQL_PORT 环境变量从调用者取得本地数据库凭据，未写入测试代码或传为命令行参数。

安全边界：固定 JDBC 连接 127.0.0.1，验证元数据 URL 和服务端口、服务器 UUID；使用 32 位随机 token 的专属 schema，CREATE DATABASE 不含 IF NOT EXISTS，碰撞直接失败，核验 DATABASE()、服务器 UUID 且初始无表后才建立所有权标记。仅从部署 SQL 中提取并校验 11 张表的 CREATE TABLE 白名单，不执行原 SQL 中 DROP TABLE。每次测试写入前通过 Spring 数据源重新核验 schema 和标记；结束时同连接验证标记后删除专属 schema，并查询 information_schema 确认删除。异常退出注册关闭钩子进行同样的安全清理；若建库后、标记建立前失败则不会冒险删除该库，需人工核对。Redis 指向 127.0.0.1:1，缓存、网页获取和 RAG 使用测试替身；Agent 文件与上传根设置为新建的独立临时目录，未删除该目录。COS 和废弃 AI 的 key 使用测试占位值。未启停服务、创建或删除容器，未使用外网。

实际命令及结果：
1. 在未设置三项测试变量时执行 mvn -f cloud/pom.xml "-Dtest=AgentUrlImportConcurrencyLocalIT" test，退出 1，原因 Missing isolated test setting: WIKI_IT_MYSQL_USER；这是故意的失败关闭测试，15 项因上下文未启动而报错，没有运行行为测试或连接数据库。
2. 从本机 application-local.yml 在父脚本中读取端口和凭据，仅传给 Maven 子进程环境；先断言配置主机为 localhost/127.0.0.1、端口为 3307，移除继承的密钥、密码和代理环境变量。执行同一固定 Maven 命令；最终版本退出 0，15 项通过、0 失败、0 错误、0 跳过。测试输出确认专属 schema codex_wiki_it_8c33aad44edd49ac9b781038d71bd563 已创建并删除。
3. 通过只读连接查询 information_schema：最终 schema 数为 0，codex_wiki_it_ 前缀遗留 schema 总数为 0。未列出或操作其他库的数据。临时日志 tmp/wiki-local-it-negative.log、tmp/wiki-local-it-final.log 仅供本地检查，不直接公开；报告未包含凭据。

风险与限制：本机测试 schema 在当前 3307 服务创建并清除，初始库存在竞态或所有权无法证明时测试会停止；没有复现真实数据库死锁，原测试仍使用注入异常验证不重复保存。上述结果不替代独立 B7 验收；其他 B 项未在本轮重跑。临时文件目录留存，日志可能含测试调试信息。禁用测试环境变量会导致固定命令按预期失败，独立测试执行者必须通过安全环境变量提供本机专用凭据。

回滚：仅恢复上述测试文件中环境初始化相关变更到此前测试快照；不恢复或修改任何业务代码和用户数据。注意回滚会重新引入 Docker 容器依赖，与当前本地测试规则不符。

