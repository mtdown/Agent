# Cloud 测试基础设施隔离试运行，第 1 轮

本次只用于测试基础设施试运行，不是独立验收。结果：BLOCKED。D1 在 Maven 构建模型阶段离线依赖解析失败，未执行测试；D2 和全量未运行。未修改项目业务代码或规格。

## 隔离与快照

任务标识：codex-wiki-infra-mvwi90vk。Java 17 + Maven 3.9、MySQL 8.0.45、Redis 7；新建 Docker internal 网络无默认 IPv4 路由，容器无宿主端口发布。外网 TCP 测试失败，网络网关的 3307、6379 TCP 连接探测均超时；无 Docker socket。这些证据只适用于本次网络实例。唯一宿主挂载为只读 Maven repository；没有挂载宿主运行 tmp、.env、用户主目录或现有数据库卷。HOME、Java user.home、工作目录和 Agent 文件及上传根目录均为容器私有路径，仅注入测试服务临时凭据与占位值，没有继承真实 API Key、代理或宿主凭据。

复制 330 个最小输入文件，容器内 sha256sum 校验 330 项通过，复制后再次核对原文件摘要未变化。唯一内容差异是快照 cloud/src/main/resources/application-local.yml 中四个字面量敏感值 password、secretId、secretKey、apiKey 替换为占位符；不是逐字节相同配置。脱敏清单保存在 isolated-cloud-snapshot-manifest.json，其 SHA-256 为 3f1886edd1aeeed46b6e3458fe8626a9d0a3c1c1d08c884bfac5f7fc1220ef08。

JDBC Connector/J 从测试容器实际连接临时 MySQL，核对 metadata URL、库名和服务 UUID；Redis TCP PING 和专属标记均匹配临时 Redis。Spring Boot 2.7.6 最小配置探针激活 local profile，核对 datasource、Redis、Agent 文件及上传根目录。预检期间曾执行一次 mvn -v 仅查看版本，未加载项目或运行测试；该行为早于预检完成，违反“预检前不运行 Maven”的字面要求，已告知总管。

## D1 与缓存检查

使用无凭据 settings、offline 及只读 repository 运行固定 D1 命令，退出 1。com.baomidou:mybatis-plus-bom:pom:3.5.9 本地存在，但缓存仓库 ID 不属于当前构建配置，Maven 离线拒绝作为 central 产物；POM 无法解析，测试数 0。脱敏错误见 isolated-cloud-d1-offline.log。未联网补依赖，未运行 D2 或全量。

只读检查 BOM：POM 存在且 SHA-1 与同目录摘要匹配；其 _remote.repositories 记录 ID 为 alimaven 和 aliyunmaven。全 repository 元数据条目按 ID 聚合为 aliyunmaven 7782、central 2561、alimaven 1920、本地 20。没有读取宿主 settings 或凭据，也没有修改 repository。下一轮拟在无凭据专用 settings 中声明这些已有 ID、保留 offline 并使用不可访问的占位 URL；仅为待验证假设，不证明依赖闭包完整。

## 清理与边界

容器及网络均通过本任务标签、完整 ID 和名称核验后删除；网络为 internal 且只连接本任务容器。核验结果：容器 0、网络 0、关联匿名卷 0。测试快照目录 C:/Temp/codex-wiki-infra-mvwi90vk 的递归删除命令被自动安全策略拒绝，工具只返回 blocked by policy，未给更具体原因；目录仍保留，未尝试绕过。拉取的 Maven 镜像保留在共享镜像缓存，不删除以免影响其他任务。Linux 环境不能称 Windows 测试通过；本次 Maven 未进入测试，不构成独立 PASS。
