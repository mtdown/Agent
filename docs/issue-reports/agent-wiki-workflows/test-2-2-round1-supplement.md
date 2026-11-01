# 2.2 第 1 轮补充验收报告

实例：/root/test_conversation_r1；日期：2026-11-01（北京时间）；同一轮同一快照补证。结论：BLOCKED，另有证据不足；未发现可证明的固定验收失败。

规则与快照：已重新阅读 AGENTS_test.md，新增默认使用本地环境的规则；该文件为负责人明确更新，独立于原 57 项实现及报告清单，当前 SHA256 9a154e748b75075b4d84ac6f7a6f8d6722a50efad8c3ba285d49e25b5c8c7546。分支 feature/agent-wiki-workflows开发，HEAD bbc1819f7d44a0968638b193ff5509fec5786046。原 manifest SHA256 5190c746e4894b0263124c0a038ca0f1cc725c94bd2c288e1115229df18aee7e，完整 diff SHA256 029481b507d599a4c88de85a7bab2ad3a1aa76f2380ee34bc4952c09c110cd8a，开发记录 SHA256 b30f7a3d9b4c2a5defadfe1b4bae2a1963ea569ab496c5dc4b38bd36d8d09fda，均复核一致。补证前后逐项 Get-FileHash -Algorithm SHA256 对照 manifest，均零差异；git status --short --untracked-files=all 除规则变更及本轮获准报告和日志外无新增项。未运行基线、未修改业务或正式测试。

本地只读审查：CloudApplicationTests 标记 @SpringBootTest 与 @ActiveProfiles("local")；源码本地配置的 JDBC 目标为 localhost:3307/Cloud，本机 3307、6379 有监听，但随后按总管要求仅连接 MySQL 查询 SHOW GRANTS 和服务器端口，不选现有数据库、不查询用户数据；权限包含全局创建库，服务内部端口为 3306。未确认既有库内容为可识别测试数据，应用运行时配置覆盖及连接身份未实测。AgentConversationStore 的 PostConstruct 调用 AgentConversationInitializer，执行 CREATE TABLE IF NOT EXISTS、缺列时 ALTER TABLE、以及无测试用户限定的 repairLegacyTitle UPDATE。AdminInitializer 在没有管理员时创建管理员；WikiSpaceInitializer 调用 ensurePublicSpace。启动副作用不仅限于测试数据，且不受测试方法的事务回滚保证。故不能确认全量或真实 Spring Context 测试不会影响现有数据。

D1：此前定向命令的 7 项通过证据见 test-2-2-round1.md 及 test-2-2-r1-d1.log；真实旧库补列和历史数据保持仍为证据不足，本次无安全可识别旧库可执行该场景。

D2 定向：此前 13 项通过，见原报告及 test-2-2-r1-d2.log。现只读审查发现 AgentWikiChoiceLocalIT 包含真实数据库写入后读取会话结果、历史投影、归属和预览断言；该测试激活 local 并先启动上述全局初始化，不能确认只影响测试数据，故没有执行。其断言中的同一事务内 messages 重读也不能单独证明跨事务或进程重开。上述真实数据库行为仍为证据不足。

D2 全量：BLOCKED。拟执行命令为指定 Maven 3.9.6 的 mvn -f cloud/pom.xml test，预期退出 0、报告所有跳过；由于 CloudApplicationTests 的本地真实上下文副作用不可控，未执行，故无退出码及全量跳过清单。未用排除测试方式冒充固定全量。未创建容器、未读取或清理既有数据库内容、未启动服务、未访问用户文件。

问题状态：#3 待验证（定向映射通过，全量未执行）；#5 待处理（现有数据库启动副作用仍未消除或确认可控）；#6 证据不足（真实旧库升级及会话写后重开）。未做变异检验。无新建临时目录或数据；未操作此前自动策略拒绝删除的临时目录。

进一步本地方案评估：可连接的 MySQL 账户具备创建专用 schema 的权限，deploy/schema-init.sql 包含针对当前 schema 的 DROP TABLE，因此只有确证目标为独有库才可使用；另有 SQL 文件硬编码 USE Cloud 或 picture，不可直接对既有服务执行。固定全量可以理论上通过系统属性覆盖 JDBC URL 及 agent.workspace-root、agent.upload-temp-root，但本次尚未证明 Spring 测试进程实际采用覆盖值，未创建 schema 或运行上下文。AgentWikiChoiceLocalIT 的旧列检查硬编码 JDBC URL 必须以 localhost:3307/Cloud 开头，换独有 schema 会使该断言失败，不能降低断言。

Redis 配置指向本机 6379 的逻辑库 0；只读 INFO keyspace 返回仅 db0 有键，但空逻辑库不证明专属权或无并发使用，不能据此对其执行可能清除键的测试。WikiCacheManagerImpl 对当前逻辑库执行通配符 KEYS 与 DELETE；未发现扫描其他逻辑库的实现，但也未实测 Spring 的 Redis 逻辑库覆盖生效。本机 PATH 未发现 redis-server 或 redis-cli，可供启动的专用实例尚未确认。根据不可影响现有数据的约束，本次未执行全量或真实数据库场景，未新建或删除 schema、Redis 键及临时目录；该限制不以容器作为前提。

Redis 和路径进一步只读审查：搜索 cloud/src/main/java、cloud/src/test/java 与 Maven 配置，未见 FLUSHALL、FLUSHDB 或 Redis 跨逻辑库 SELECT；多数单元测试使用 mock，默认命名规则下 *LocalIT 不属于普通 Surefire *Test 全量。WikiCacheManagerImpl.clearAllListCache 使用 `agentWiki:documentWiki:list:all:*` 通配符批量删除，无本轮标记；AgentWikiChoiceLocalIT 的 Wiki 写入可触发缓存失效，当前空逻辑库有并发写入时无法保证只删本轮键。未发现 redis-server 在 PATH。仅静态看到 Spring 可配置 agent.workspace-root 和 agent.upload-temp-root，AgentPersonalFileUpload 构造时会删除上传目录内 upload-*.tmp，尚未证明测试进程实际覆盖到独有目录。CloudApplicationTests 未在初始化前核验实际 JDBC、Redis 或文件路径；本地数据库账户具备跨库权限，不能仅依赖尚未实测的属性覆盖来保证错误配置下不影响现有库。本次因此没有创建专用 schema、写 Redis 或启动测试；全量仍 BLOCKED，真实旧库升级及重开仍为证据不足。以上为当前证据边界，不表示逻辑库必然发生并发写入或源码必然跨库操作。
