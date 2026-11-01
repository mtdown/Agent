# Cloud 测试基础设施隔离试运行，第 2 轮

结果 BLOCKED；仅为基础设施试运行，不是独立验收。全新任务标识 codex-wiki-infra2-mvwis2jq，未触及第 1 轮残留目录。配置 Maven 离线 profile 声明缓存中已有的 alimaven 和 aliyunmaven 仓库及插件仓库 ID，URL 为不可访问的回环占位地址；没有凭据，也没有修改宿主 repository。

## 预检

新建 internal 网络、MySQL 8.0.45、Redis 7 和 Java 17/Maven 测试容器，均以本轮标签命名且未发布端口。测试进程无默认 IPv4 路由，外网 TCP 失败，网关 3307/6379 连接探测超时；无 Docker socket，唯一宿主挂载是只读 Maven repository，未挂载宿主 tmp、.env、用户主目录或现有数据库卷。实际 JDBC metadata URL、数据库名、服务 UUID 均与临时 MySQL 一致，Redis TCP PING 和专属标记均通过；Spring Boot 最小 local profile 探针确认 datasource、Redis、Agent 文件和上传目录指向隔离实例或容器内私有路径。

330 个快照文件经容器内校验通过，复制前后源文件摘要一致。local 配置中四个字面量敏感值在测试快照中替换为占位符，不是逐字节相同配置；清单为 isolated-cloud-snapshot-manifest-round2.json，SHA-256 3f1886edd1aeeed46b6e3458fe8626a9d0a3c1c1d08c884bfac5f7fc1220ef08。MySQL 11 张表来自本轮专属 schema，无真实用户数据。就绪探针首次因 PowerShell 未引用的 -h 参数被拆分而输出帮助，引用后认证 TCP SELECT 1 成功；未连接已有数据库。

## 结果和清理

固定 D1 在离线模式退出 1，测试未开始；原 BOM 仓库 ID 匹配障碍已越过，但 Maven 3.9.16 请求默认插件 maven-resources-plugin:3.4.0，本地无该目录、POM 或 JAR。宿主仓库仅只读核对，未安装或修改依赖；脱敏日志为 isolated-cloud-d1-offline-round2.log。D2 与全量未运行。经总管只读核实，项目指定的 IDEA 内置 Maven 为 3.9.6，测试容器 Maven 3.9.16 的默认插件差异须列为环境差异；后续另起全新环境使用 3.9.6 镜像验证，不能把本轮作通过。

仅本轮容器、网络在核验标签、名称、完整 ID 和网络连接后清理，复查容器 0、网络 0、专属匿名卷 0。第 1 轮被拒绝清理的目录没有触及；本轮脱敏快照目录保留供审计。Linux 测试结果不得表述为 Windows 通过，未执行测试不得表述为独立 PASS。
