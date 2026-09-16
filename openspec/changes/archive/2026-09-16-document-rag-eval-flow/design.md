## Context

`eval/` 已具备完整评测能力（99 题数据集、双适配器 runner、冻结的指标口径），但流程说明散落在脚本注释、`changes/` 工件与对话记录三个地方。见 proposal.md — Why。

影响本次做法的现状约束：

- `eval/README.md` 已有脚本清单与指标口径，但**没有端到端视图**（从语料到报告的主干路径），也没有"改动影响面"映射
- `eval/results/` 现存 3 份结果：1 份离线预演（`embeddingModel` 记为 DashScope 名，但通道是 offline）+ 1 份本地 Ollama http（2560 维）+ 1 份云端 DashScope http（1024 维）
- 两份 http 结果的 `config.embeddingModel` **均为 `null`**——`run_eval.py` 有意不读后端配置，避免拿 offline 的模型名冒名顶替
- 根 `README.md` 共 203 行，内容停在 Wiki 阶段；引用类指标集中在 `eval/audit/baseline-report.md`

## Goals / Non-Goals

**Goals:**

- 评估流程可在文档内闭环复现，不依赖脚本注释或对话记忆
- 任何常见改动都能查到影响面（动哪些文件、是否要重建索引、是否要重新对齐 gold）
- 对外引用的基线唯一，且 embedding 模型归属可确定
- 根 `README.md` 与仓库实际实现一致

**Non-Goals:**

- 不新增自动化对比工具（负责人明确不需要保留历史对比）
- 不改 `run_eval.py` 的模型探测逻辑
- 不重跑评测、不重建索引、不动任何 Java / Vue 代码

## Decisions

### 决策 1：流程说明增补进 `eval/README.md`，不新建文件

- **备选**：新建 `eval/FLOW.md` 由 README 引用
- **理由**：README 已是 `eval/` 的自然入口，且已承载脚本清单与指标口径。把"流程"与"口径"拆到两个文件，会让两者随时间各自漂移，直接违反 spec 中"单一权威说明"这条要求
- **代价**：文件由约 10.8 KB 增至 15 KB 量级；用清晰的二级标题分层缓解

### 决策 2：历史结果直接移除，不建归档子目录

- **备选**：移入 `eval/results/archive/`
- **理由**：负责人已明确"不需要保留"。更重要的是，两份 http 结果的 embedding 归属是 `null`，留在目录里会制造"这几份可以互相比较"的错觉；而实际的模型对比结论只存在于 `switch-embedding-dashscope/tasks.md` 的一对括号里，无法支撑对外引用
- **代价**：失去 offline vs http 的差异样本。缓解：三份文件均被 git 跟踪，git 历史可 `git show` 取回

### 决策 3：http 基线的模型归属改用文档显式记录

- **备选 A**：给 runner 加后端配置探测后重跑（需要后端与 Ollama 配合启动，多一轮依赖）
- **备选 B**：在文档中显式记录归属并注明来源
- **选 B**：负责人要求"拿现在的结果当基线"，B 不动代码、不重跑。spec 也允许"JSON 内记录 **或** 文档显式记录"二选一
- **前提**：归属必须有可追溯依据，不能凭印象写。依据为 `switch-embedding-dashscope/tasks.md` 的 5.3 / 5.4 记录（`force=true` 全量重嵌入 216 篇、维度 1024、随后跑出该结果）与 `application.yml` 的 `rag.embedding.model` 默认值

### 决策 4：README 的「技术亮点」按证据强度增补

- 只写仓库内**有实测记录**的数字：评测指标、B 类文号层增量、权限零泄漏、瓶颈定位
- 同时删除无法溯源的性能声明。依据是 spec 中"性能声明必须有实测来源"这条——保留无来源数字是负债，面试追问即塌

## Risks / Trade-offs

| 风险 | 缓解 |
|---|---|
| 文档写完再次与实现脱节 | spec 已固化"对外文档与实际状态一致"要求，并把一致性检查写进流程文档的维护项 |
| 移除历史结果后，外人以为项目从未跑过 offline 模式 | 在 README 的评测章节与 tasks 中注明 git 历史可追溯 |
| README 改动幅度大，与 `README_old.md`（学习笔记）风格冲突 | 保持现有章节骨架与写法，只改内容和个别章节标题，不重构结构 |
| 文档中写死基线指标，后续重跑后忘记更新 | 指标表旁显式标注来源文件名与运行 ID，让"该更新哪里"一目了然 |

## Migration Plan

纯文档与数据清理，无部署动作、无服务重启、无数据库变更。

回滚：`git revert` 本次提交即可；被移除的结果文件可从 git 历史恢复。

## Open Questions

无。
