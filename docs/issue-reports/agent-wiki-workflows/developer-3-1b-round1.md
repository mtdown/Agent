# 阶段 3.1b 第 1 轮前端开发记录

Change agent-wiki-workflows，阶段 3／任务 3.1b；分支 feature/agent-wiki-workflows开发，HEAD bbc1819f7d44a0968638b193ff5509fec5786046。仅修改前端；开发自检不代替独立验收。被测前端文件停止写入时间：2026-11-01 15:48:32 +08:00。

## 本轮文件

- 修改 cloud_front/src/api/ragController.ts：导入确认类型、SSE 按 kind 分派、历史消息联合类型及逐项导入状态；SHA-256 5e684e08e6f0001afbefaa3cd2502eeaa8ac6bccaf21c51fa12f9403af52b105。
- 修改 cloud_front/src/pages/aiAssistant/AiAssistantPage.vue：确认卡片、逐层目录和逐条 URL、状态和 token 提交、断线后历史重载以及混合结果显示；SHA-256 1968398edd01bd22c99519e335a77bbe7c7a86e78e0a36b8ac55b76a71e7491。
- 新增 cloud_front/src/pages/aiAssistant/importConfirmation.ts：状态、过期、token 选择与会话防重锁；SHA-256 534cd5be7a710156e396b7cde250bdd296020a187699ac2663d321ef341aaf86。
- 新增 cloud_front/importConfirmation.test.mjs：现有 Node 测试框架下的状态、Vue 模板渲染和按钮、SSE 及页面提交处理测试；SHA-256 5353ba2310719cec8ec08ab5c18973b2e09966627f88c8f282338f0377e5f314。

无新增依赖，无后端、公开 HTTP 路径/请求体或 OpenSpec 修改。现有端点的响应事件和历史交互新增 import_confirmation 类型，需与 3.1a 后端配套部署。

## 行为及验证范围

确认卡直接显示服务端提供的空间、folderSegments 各级独立文本和全部 URLs；根目录单独标识，确认前明确显示不会写入。确认和取消分别仅提交服务器对应 token 与 turnId，禁止前端提交链接或目标。到期、已消费、未知、请求期间均不能再次提交。每次请求前先持久会话锁；会话存储不可用时拒绝发送。断线、HTTP 错误、未收到 done 和异常后刷新会话；刷新失败或服务器仍为 pending 时保留不确定锁，不能盲重试。服务端明确 cancelled、consumed 或已生成结果后可清除本地锁，unknown 不标为成功。状态恢复和后端原子单次执行、过期与权限仍依赖 3.1a 后端；前端锁不是安全边界。

沿用 wiki_choice 事件，根据 payload.kind 区分新卡和旧 destination_choice，保留旧卡处理；history 消息直接恢复新 interaction。混合结果补充网页导入、not_executed、来源和缓存警告显示。旧客户端遇到新 kind 是否零写由服务端强制设计保证，前端测试不能证明。

节点测试采用真实 Vue 模板编译及自定义 Vue 渲染器检查目录与链接、按钮分派；使用测试替身对实际 SSE 解析函数检查旧/新事件和请求体，仅本地 Response，无外网；对页面实际提交处理函数注入替身模拟断线及历史失败，确认锁先于调用、后续不重发。状态函数另覆盖取消、过期、已消费、unknown、会话存储失效与刷新后锁。它们不等于真实浏览器 DOM、真实前后端网络及数据库的联合 E1–E3；后端当前契约已只读核对，联合和跨用户/失权/确认前零写应由新独立测试实例验证。

## 命令与结果

均在 cloud_front 目录：
- node --test importConfirmation.test.mjs：退出 0，6 项通过、0 失败/跳过。最初新增测试因模块尚未存在预期失败；模板测试初次因正则转义、处理函数测试初次因固定测试时间已过期而自检失败，修正测试夹具后通过。
- node --test *.test.mjs：退出 0，104 项通过、0 失败/跳过；tmp/agent-3-1b-node-tests-final.log。
- npm run type-check：退出 0。
- npm run build-only：退出 0；Vite 报已有大型 chunk 警告；tmp/agent-3-1b-build.log。
- git diff --check -- cloud_front：退出 0。

构建产物在项目既有 dist 目录，未重启开发服务。未访问真实外网或用户数据、未创建容器，未触及以前被策略拒绝的目录。

## 未完成、问题和回滚

状态 PARTIAL：前端自检完成，真实前后端联合与 E1–E3 的服务器侧验证尚需独立验收；不是 PASS。#10 已由 3.1a 开发者更新待验证，历史原 C1 FAIL 保留；不修改本机问题状态。浏览器禁止会话存储时用户无法提交，作为断线安全限制明确提示。刷新后服务器仍报告 pending 而本地存在未决锁时不自动恢复可点击，需要服务端明确终态或新请求，不主动重复写入。

回滚仅撤销这四个文件本轮增量，保留其他开发者的 3.1a 变更；若后端继续下发 import_confirmation，旧客户端不显示该卡，因此回滚需前后端协调部署。不得清理用户数据、历史消息或已有工作区改动。

