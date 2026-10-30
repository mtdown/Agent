# 3.2 第 1 轮当前快照独立补证

Change `agent-tool-file-reference`；阶段 3／任务 3.2／第 1 轮；测试实例 `/root/tester_3_2_current`；2026-10-30，UTC+08。固定验收 A1、A2、A3 在下列受控范围内 PASS；未发现已证实缺陷。真实登录入口、完整 Agent→Cloud→数据库→浏览器跨进程链路和整体压测未测，不以本补证证明完整端到端安全或性能。原轮报告见 [3.2 第1轮](issue-p3-t3-2-round1.md)。

## 验收结果及范围

- **A1 PASS（受控交互及存储层）**：`cloud_front` 的 `npm run build` 和 `node --test *.test.mjs` 均退出 0，前端 98 项通过；`cloud` 的 `mvn -q "-Dtest=AgentPersonalFileReferenceLocalIT,AgentPersonalFilePreviewTest,AgentWikiMultiResultLocalIT" test` 退出 0。浏览器连接随机端口本地 fixture，API 和 SSE 均为模拟：历史两轮分别显示第一、第二文件名及字节 6–12；点击后右侧预览显示对应位置，上一段和下一段显示中文；新模拟回合显示并可点击新引用。真实 50 MiB 合成文件以当前 `AgentPersonalFileStore` 源码重新编译，`java -Xmx24m` 成功读取引用段及相邻段，断言中文、emoji UTF-8 边界和连续性；每段 16384 字节。文件 SHA256 `81906df84d144bbb037a5821c01af070d11ad1d02be1fdc8b18f19f81f31a03d`。该探针为存储层，非完整链路 50 MiB 压测。
- **A2 PASS（受控认证与异常场景）**：当前控制器及生产异常处理器在本地随机端口运行，认证和消息数据源为替身。有效引用成功；无登录、跨用户、伪造引用和游标、修改及删除文件均得到非零业务码和空 data；断言响应不含合成正文、绝对路径、私有密钥、证据和令牌。另用真实 Servlet session、生产 `UserServiceImpl.getLoginUser`、`userLogout` 和生产异常处理器：会话预览业务码 0，登出返回 true，同一 cookie 再次预览业务码 40100、data 为 null，敏感内容及路径不出现在响应中；仅用户 `getById` 查询和消息源为替身。浏览器模拟过期时清空正文并显示“引用不可用、权限已失效或文件已变化，请重新获取引用。”项目无个人文件级共享撤权模型；未验证真实登录入口或文件级共享撤权。
- **A3 PASS（受控回归）**：上述固定命令均退出 0；模拟 API 浏览器中点击 RAG 文档和 Wiki 文档分别在右侧显示对应正文，历史消息及引用保持可用。代码和测试覆盖单段限制；本补证未运行真实 RAG 检索。未发现新增数据库迁移或依赖的证据；完整差异供复核。

## 快照、记录与证据

分支 `feature/agent-tool-file-reference开发`，HEAD `7ffc2d629a1d91b46fc8fce3f0ee19da30a3d6f6`；前后逐项核对 47 文件零不匹配。快照 `%TEMP%/agent-tool-file-reference-current-sha256.txt` SHA256 `65f2d54c09b2eb3cd72985c988066d1ee8b87505407e46a900b3d0472860488f`；完整差异 `%TEMP%/agent-tool-file-reference-current-diff.txt` SHA256 `ed586f692a3b1c072bdc76bbc845df945e59ed50670ee3d889ee389fd4a1ad1a`；开发记录 `tmp/p3t32-development-record.md` SHA256 `e25c8e813ed81cef16bbcf9bc1ab0950851b735d9daa7816bbc886964d152fa3`。开发记录所述构建和 98 项测试由本实例重跑；其开发过程未独立复现。

固定命令日志 `%TEMP%/p3t32-independent-build.log`、`p3t32-independent-front.log`、`p3t32-independent-mvn.log`。存储及 HTTP 探针源码、编译与运行日志保留 `%TEMP%/p3t32-independent-43c180052e624be58426d0387e3b6292/`；HTTP 使用合成数据。真实会话探针源码、日志、前后快照及 488 项编译输入哈希保留 `%TEMP%/p3t32-session-supplement/`，其 `inputs-sha256.txt` SHA256 `358cfc9584dcab339b1afbeecb40d9a2c7415c4a20e2317656170db5bdc75700`。浏览器 fixture `tmp/p3t32-ui-fixture2.py` SHA256 `c2f3db6913ac70ae5d6968e13d04fd9c7d48a5970648897381a6a1935ec8691f`；交互观察保存在测试实例工具记录。模拟服务与探针服务已停止，临时文件和构建产物保留，未操作真实用户数据。

固定验收缺陷及清单外交付阻断问题：无已证实问题。`ATFR-P3-T3.2-R1-S1` 保留完整端到端未测的限制。基线与变异检验未做；未修改被测源；风格意见无。