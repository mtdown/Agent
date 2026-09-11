# add-rag-eval-dataset Tasks

分支：`feature/rag-eval开发`（已从 `origin/main` @ 765e757 切出并跟踪远程）
语料输入：`F:\AIProject\my\corpus-2026`（216 篇，只读，不进仓）
数据库：`localhost:3307/Cloud`（只读查询，不改表、不改索引）
工期估算：约 2.5–3 天（含负责人人工环节）

## 1. 准备与语料体检（Step 0）

- [x] 1.1 建 `eval/` 目录骨架：`audit/`、`scripts/`、`tools/`、`tmp/`，并把 `eval/tmp/`、`eval/.env` 加入 `.gitignore`；验证：`eval/tmp/` 与 `eval/.env` 均被忽略（已确认）
- [x] 1.2 写 `eval/scripts/corpus_audit.py`：统计空壳率、表格丢失率、文号覆盖率、跨辖区噪声、内容重复；验证：产出 `eval/audit/corpus-audit.md` —— **216 篇 / 空壳 5 篇(2.3%，均为任免批复类短文件) / 含 MD 表格 0 篇、12 篇提及表或附表 / 有文号 54 篇(仅政策文件) / 跨省联合发文 1 篇(川渝通办，合法) / 重复 0 组**
- [x] 1.3 写 `eval/scripts/export_anchor_map.py`：查 `wiki_chunk` 导出映射到 `eval/tmp/anchor-map.json`；验证：**匹配 216/216，ACTIVE chunk 2061 全覆盖，无歧义、无孤儿 docId**（政策文件 1232 chunk / 部门解读 165 / 新闻发布会 233 / 媒体视角 431）
- [ ] 1.4 **【负责人人工】** 复核体检报告：确认空壳篇是否影响出题、跨辖区噪声清单是否完整；验证：负责人在报告末尾签字式批注或回复确认

## 2. 配对锚定（Step 1）

- [x] 2.1 写 `eval/scripts/build_pairs.py`：从解读标题书名号提取政策名并规范化匹配政策文件，输出 `eval/pairs.json`（含 `reviewState`、`matchType`、政策/解读 `docId`）；验证：**产出 31 组配对（部门解读 19/21、新闻发布会 8/10、媒体视角 4/128），覆盖唯一政策 26 篇**；全部为 contains 匹配（政策标题带「关于印发《X》的通知」前缀，属预期），未匹配 128 篇标记为 unmatched
- [x] 2.2 **【负责人人工】** 逐条核对 31 组配对，把 `reviewState` 置为 confirmed / rejected；验证：**负责人 2026-09-11 于对话中确认 31 组全部正确，脚本以 `--assume-confirmed` 执行（pending 视为 confirmed）**；`pairs.json` 中 `reviewState` 字段保留原值备查
- [x] 2.3 汇总配对结论写入 `eval/audit/pairs-audit.md`：验证：文件已产出，含覆盖情况、5 篇一对多政策提示、31 组待确认清单，与 `pairs.json` 统计一致

## 3. 题目格式规范与生成（Step 2）

- [x] 3.1 写 `eval/README.md`：固化 JSONL 字段规范（含 `gold` 用 `docId`+`chunkIndex` 逻辑坐标、`expectRefusal`、`permission`、`meta`）、脚本说明、目录说明、已知问题；验证：文件已产出，字段示例与 design D4 一致
- [x] 3.2 写 `eval/scripts/generate_pair_questions.py`：三段式（LLM 生成问题 → bigram 粗排候选 → LLM 精筛 + **摘抄校验**），答案取解读原文，`gold` 只绑政策原文 chunk；验证：**产出 A 类 54 题（31 组 × 2 − 丢弃 6），overview 30 / detail 25，覆盖 25 篇唯一政策，gold 全部通过 quote 校验**；丢弃的 6 题均为「答案只存在于解读、政策原文无支撑」，符合预期
- [x] 3.3 写 `eval/scripts/generate_docnum_questions.py` 生成 B 类文号题：验证：**10 题产出（汇总去重后 9 题）**，文号与库内 `docNumber` 逐一比对一致（54 篇 fileNum == dbDocNumber，0 差异）；**gold 跳过 chunk 0**（front-matter 污染，会使文号题退化为字符串匹配）
- [x] 3.4 写 `eval/scripts/generate_unanswerable.py` 生成 C 类无答案题 15 道（人工列候选 + **自动校验实体在库中 0 命中**）；验证：15 题全部通过校验，每题标注 `outOfScopeReason`；**发现「四川」在库中命中 67 chunk（川渝通办），不能作为库外实体**
- [x] 3.5 写 `eval/scripts/generate_permission.py` 生成 D 类权限题 10 道：验证：**政策文档空间 2095544464810774531 成员 2 人、非成员账号 10 个**，D 类为 A/B 镜像题（`meta.mirrorOf`），非成员必须 0 命中、成员应命中，构成对照
- [x] 3.6 写 `eval/scripts/generate_synthetic.py` 生成 E 类合成补充题：验证：**产出 12 题**，全部通过摘抄校验；8 个段落因信息量不足被跳过（目录型/落款型）
- [x] 3.7 写 `eval/scripts/merge_candidates.py` 汇总 `eval/candidates.jsonl` 并做规范化去重 + **gold 锚点有效性校验**（`(docId, chunkIndex)` 必须在当前 ACTIVE chunk 中存在）；验证：**读取 102 → 保留 100 题（去重 2、校验错误 0）**；分类 A 54 / B 9 / C 15 / D 10 / E 12

## 4. AI 预筛 + 人工筛选（Step 3）

- [x] 4.0 写 `eval/scripts/llm_judge.py`（LLM-as-Judge 预筛）：按三套判据评审全部候选题——有证据题审「可答性/忠实性/标注完整性/问题自然度」，无答案题审「库外合理性/自然度/诱导硬答风险」，权限题审「指向明确性/自然度/泄漏可判定性」；输出 `eval/ai-judge.jsonl` + `eval/audit/ai-judge-report.md`；验证：**100 题全部评审完成，keep 37 / edit 47 / drop 16，0 error；证据类四维均分 answerability 4.76 / faithfulness 4.29 / sufficiency 3.32 / naturalness 4.63**
- [x] 4.0.1 Judge 输出「应补充/应移除的 chunkIndex」，使 `sufficiency` 低的题可**补 gold 修复**而非丢弃；验证：**40 题给出 missingChunks，其中 39 题答案可答性≥4 且忠实性≥4（即答案无误、仅标注不全）**
- [x] 4.1 写 `eval/scripts/build_review_page.py` 生成 `eval/tools/review.html`：数据（含 gold chunk 原文）内联进 HTML，`file://` 双击即可离线使用；逐题展示问题/标准答案/**证据 chunk 原文并高亮 quote**/来源标题与文号，支持保留·删除·待定·编辑问题与答案·备注，快捷键 K/D/P/←/→，进度存 localStorage 可断点续看，一键导出 `golden.v1.jsonl`；验证：**页面 428 KB，内联 100 题 / 126 个 chunk 片段，Node 解析内嵌 JSON 通过（100 题、75 题带证据文本、分类分布正确）** —— 「保留+删除+导出」的完整交互待负责人本地操作确认
- [x] 4.1.1 页面接入 AI 评审：列表显示 verdict 徽章、新增「AI 建议」过滤器（保留/待修/建议删/可补证据修复）、详情展示四维分数+问题标签+理由+修改建议，并支持**一键采纳**把 AI 指出的缺失段落并入 gold；验证：**Node 解析通过（100 题全带 ai 字段，43 题带建议段落共 245 段、0 段缺文本，adopt 函数与过滤器均存在）**
- [x] 4.2 **【负责人人工】** 逐题审阅候选题（100 题），导出 `eval/golden.v1.jsonl`；验证：**保留 99 题 / 剔除 1 题（C-14），每题 `meta.reviewState='kept'` 且带 `reviewNote`**；人工实际处置：**修订标准答案 21 题**（删除原文无据的结构性断言，如 A-p-21-1「7 项民生指标」实为 8 项、A-p-25-1「两个扩面三个明确」原文不存在）、**补标 gold 证据 38 题共 228 段**（已逐段回原文核验）、复核通过 40 题；完整逐题处置表见 `eval/audit/human-review-report.md`
- [x] 4.3 **【负责人决策】** C 类无答案题口径：**决策为保留 14 题、剔除 1 题（C-14）**。负责人全库检索核验——「婚姻登记/住房补贴/报销比例/食品经营许可/消防验收/学区划分」等关键词在 216 篇语料中均为 0 篇，C-15「电动自行车」仅出现在消防整治与摩托车产业语境、无上牌流程规定，**库外前提成立，判为有效拒答题**；C-14「民营经济」在发布会、媒体视角、营商环境任务清单第 150 条均有提及，**库外前提不成立，剔除**。暂不补充「纯越界」对照题（当前 near_miss 已能诱导幻觉，够用）；验证：结论写入 `eval/audit/human-review-report.md` 第四节
  - **重要附带结论**：AI 预评审的 16 条 drop 建议中 **15 条被推翻**（drop 精准率仅 6%），但 AI 也漏判了 A-p-06-1/A-p-21-1 等原文无据的编造性断言（仅判为"证据缺失"）。→ **AI 预评审适合发现证据覆盖问题，不适合独立判定事实性错误与库外前提。**

## 5. 校验与固化

- [x] 5.1 写 `eval/scripts/validate.py`：校验 schema 完整性、所有 `gold` 锚点可解析到当前 ACTIVE chunk、`gold[].quote` 真实性、问题去重、五类齐全与配比偏差、C/D 类专属规则、**禁止出现 `chunkId` 字段**（递归扫描）；验证：**`--self-test` 注入 `chunkIndex=999999` 的坏数据后正确报错并指出题号 `SELFTEST-BAD`**；对 100 道候选题实跑**全绿无错误**（162 gold 段落覆盖 37 篇文档、144 条 quote 校验通过）
  - 与设计稿的两处偏差（已确认）：①「跨辖区噪声文档未出题」改为**提示而非禁止**——corpus-audit 已确认唯一的跨省文件是川渝通办联合发文，属合法语料，禁止会误伤；② 去重键改为 `(category, 归一化问题)`，因 D 类是 A/B 的镜像题，问题文本本就相同
- [x] 5.2 对 `golden.v1.jsonl` 跑校验；验证：**全绿无错误**——**99 题**（A 配对 54 / B 文号 9 / C 无答案 14 / D 权限 10 / E 合成 12，五类齐全）、**gold 390 段覆盖 37 篇文档**、**144 条 quote 全部命中原文**；所有 gold 锚点可解析到当前 ACTIVE chunk，**无 `chunkId` 字段**
- [x] 5.3 生成 `eval/manifest.json` 与 `eval/scripts/gen_manifest.py`：语料快照（216 篇逐篇 sha256 + 汇总 hash `fc472f63…`）、chunk 快照（2061 行 / 216 篇 / 全 ACTIVE / 单空间）、LLM 与 embedding 模型版本（**不记密钥**）、实际配比与偏差说明、只读约束自检；验证：**`validate.py` 已内置 manifest 字段完整性校验**；人为构造缺字段 manifest 时准确报出 10 处缺失
- [x] 5.4 提交改动到 `feature/rag-eval开发` 并推送远程；验证：**已推送至 `origin/feature/rag-eval开发`（`765e757..f8524a9`，累计 6 个提交全部同步，`rev-list --left-right` 为 `0 0`，`git status` 干净）**；本机 PowerShell 沙箱内 git 不可用导致 `upload.ps1` 无法执行，改用系统 git（`/c/Program Files/Git/cmd/git`）复刻等价的 add / commit / push 步骤

## 6. 集成验证与汇报

- [x] 6.1 跑 `openspec validate add-rag-eval-dataset --strict`；验证：**输出 `Change 'add-rag-eval-dataset' is valid`，零错误零警告**
- [x] 6.2 汇总汇报：体检结论、配对数、最终题量与配比、校验结果、已知局限；验证：汇报文本已写入 `tasks.md` 末尾「实施结果小结」—— **待 4.2 完成后与最终题量一并写入**
- [x] 6.3 把本轮遇到的报错与阻塞记入 `IssueLog.xlsx`（时间/分支/change id/阶段/报错/影响/方案/状态/验证）；验证：**已追加第 113–114 行**（分支切换导致 eval 产物不可见、validate 规则与 D 类结构冲突），状态均为已修复、验证通过

## Verification

- 数据集可用：`validate.py` 全绿，`golden.v1.jsonl` 约 90 题，五类齐全
- 锚点稳定：所有 `gold` 用 `docId`+`chunkIndex`，无 `chunkId` 字段；模拟"重建后主键变化"仍可重新解析
- 只读约束：执行前后 `wiki_chunk` 行数（2061）与状态分布（全 ACTIVE）不变
- 可复现：`manifest.json` 记录模型版本、`temperature=0`、语料与 chunk 快照

## Rollback

- 无数据库与后端改动，回滚只需删除 `eval/` 目录或回退分支提交
- 远程分支 `feature/rag-eval开发` 未合并前可随时弃用；合并前需负责人在远程平台验收
- 已生成的 LLM 候选题落在 `eval/tmp/`（gitignore），删除不影响仓库

## 实施结果小结

### 交付物

| 产物 | 内容 |
|---|---|
| `eval/golden.v1.jsonl` | **正式数据集 99 题**，392 KB |
| `eval/manifest.json` | 可复现快照：语料 216 篇 hash、chunk 2061 行、模型版本、配比 |
| `eval/audit/human-review-report.md` | 人工评测报告（含 100 题逐题处置表） |
| `eval/review-state.json` | 逐题筛选决策（可回灌 localStorage 复现） |
| `eval/scripts/` | 11 个脚本：体检/锚点/配对/出题×5/汇总/预筛/筛选页/校验/快照 |

### 数据集配比

| 类 | 题型 | 目标 | 实际 | 偏差 |
|---|---|---|---|---|
| A | 配对题 | 54 | **54** | — |
| B | 文号题 | 9 | **9** | — |
| C | 无答案题 | 15 | **14** | −1（C-14 库外前提不成立，剔除） |
| D | 权限题 | 10 | **10** | — |
| E | 合成题 | 12 | **12** | — |
| | **合计** | 100 | **99** | |

gold 证据 **390 段**，覆盖 **37 篇**政策文档，单题均值 3.9 段。

### 语料体检结论

- 空壳率 **2.3%**（5 篇，均为任免批复类短文件）；Markdown 表格 **0 篇**（12 篇仅"提及"表或附表）
- 文号覆盖 **54 篇**（仅政策栏），`fileNum` 与库内 `docNumber` **0 差异**
- 跨辖区噪声 1 篇（川渝通办，合法）；重复文档 0 组
- 配对 **31 组**（部门解读 19/21、发布会 8/10、媒体 4/128），覆盖唯一政策 26 篇

### 人工复核结论

- 保留 99 / 剔除 1；**修订标准答案 21 题**、**补标证据 38 题 228 段**、复核通过 40 题
- 144 条 gold 引文**全部**在原文逐字命中；9 条文号题文件名与原文完全对应
- **AI 预评审的 16 条 drop 建议 15 条被推翻**（精准率 6%），主要因：把"答案可修"当成"数据不可用"、
  对库外前提凭直觉判断。但 AI 也漏判了部分编造性断言。
  → 结论：**LLM-as-Judge 只能做预筛（发现证据覆盖问题），不能替代人工判定事实性与库外前提。**

### 已知局限

1. **gold 粒度偏粗**：多条引文常落在同一 chunk，长段落混有无关内容 —— 应在重建索引时按条款/小标题细切
2. **A-p-09-1 证据链有缺口**：缺"加力扩大有效投资""帮助经营主体降本增效"两个标题段，需在细切后重新定位
3. **D 类判定口径**：非成员"零命中"必须用 `forbiddenDocIds` 文件级权限过滤判定，不能依赖答案内容
4. **答案口径未统一**：overview 类题存在"按章节数"与"按条目数"两种统计，评测时易口径漂移
5. **表格题未出**（语料中 0 篇 Markdown 表格）；**C 类待 runner 实际复核拒答率**
6. **Judge 一致率未验**（未跑 `--repeat`），故 Judge 结论仅作预筛参考，未写入对外结论

### 只读约束

全程未写业务库、未触发索引重建：执行前后 `wiki_chunk` 恒为 **2061 行 / 全 ACTIVE**（manifest 已自检）。
