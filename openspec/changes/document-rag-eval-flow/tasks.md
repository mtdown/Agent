## 1. 基线清理

- [x] 1.1 移除 `eval/results/baseline-20260911-181335-http.json`（本地 Ollama 2560 维）与 `eval/results/baseline-20260911-173132-offline.json`（离线预演），保留 git 历史可追溯
- [x] 1.2 确认 `eval/results/` 仅剩 `baseline-20260912-151958-http.json`，作为唯一对外基线
- [x] 1.3 从该基线 JSON 提取对外引用指标（recall@6 / docRecall@6 / mrr / hitRate@6 / 权限泄漏数），作为后续文档中的数字来源

## 2. 评估流程文档（`eval/README.md`）

- [x] 2.1 增补「端到端评估流程」章节：语料准备 → 数据集构建 → 运行评测 → 产出报告，每阶段注明输入、执行命令、产出物与落盘位置
- [x] 2.2 增补「改动影响面」映射表，至少覆盖：切块参数、embedding 模型、题型配比、指标口径、检索参数、语料来源；每行给出涉及文件与该改动是否迫使重建索引 / 重新对齐 gold
- [x] 2.3 在评测章节记录基线归属：标明基线文件名、运行 ID、retriever 与 embedding 模型（DashScope `qwen3.7-text-embedding-flash` / 1024 维），并**显式注明该归属来自人工核对 + rebuild 记录，非程序探测**（`config.embeddingModel` 为 `null`）
- [x] 2.4 更新目录表与产物说明，反映结果目录现在只保留唯一基线

## 3. 根 `README.md` 更新

- [x] 3.1 演进路线表与路线图：RAG 由「🚧 规划中」改为已实现；项目简介删除"下一步将基于 RAG"的表述
- [x] 3.2 新增「AI 问答与检索（RAG）」功能小节，与「RAG 检索与评测」章节（检索链路图 + 基线指标表 + 两条核心结论）
- [x] 3.3 「技术亮点」拆为「RAG 与 AI 工程」「平台工程」两组，增补 5 条 AI 类条目
- [x] 3.4 删除无实测来源的性能声明（「纳秒级延迟、百万级吞吐」），改写为定性描述
- [x] 3.5 项目结构补 `eval/` 与 `cloud/.../rag/`；技术架构表补「检索增强（RAG）」「大模型接入」

## 4. 校验与收尾

- [x] 4.1 `openspec validate document-rag-eval-flow --strict` 通过
- [x] 4.2 通读两份文档，确认无残留的"规划中"表述、无无法溯源的数字
- [x] 4.3 汇报改动清单与需负责人人工复核的项

## 5. 过程中的问题

- [x] 5.1 **`git rm` 在本机环境会连带删除整个目录**：删除 2 个结果文件后，`eval/` 下全部 40 个文件从工作区消失；`git status` 显示其中 38 个是**未暂存的** ` D`（只有指定的 2 个被暂存），说明并非 git 按指令执行，而是目录被整体清掉。已用 `git restore --source=HEAD --staged --worktree eval/` 完整恢复，随后改用 `rm` + `git add -A` 完成删除并逐项验证目录完整性。已记入 `IssueLog.xlsx`。
