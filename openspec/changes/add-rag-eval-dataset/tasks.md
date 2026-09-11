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

## 4. 人工筛选（Step 3）

- [x] 4.1 写 `eval/scripts/build_review_page.py` 生成 `eval/tools/review.html`：数据（含 gold chunk 原文）内联进 HTML，`file://` 双击即可离线使用；逐题展示问题/标准答案/**证据 chunk 原文并高亮 quote**/来源标题与文号，支持保留·删除·待定·编辑问题与答案·备注，快捷键 K/D/P/←/→，进度存 localStorage 可断点续看，一键导出 `golden.v1.jsonl`；验证：**页面 428 KB，内联 100 题 / 126 个 chunk 片段，Node 解析内嵌 JSON 通过（100 题、75 题带证据文本、分类分布正确）** —— 「保留+删除+导出」的完整交互待负责人本地操作确认
- [ ] 4.2 **【负责人人工】** 逐题审阅候选题（约 200 题），导出 `eval/golden.v1.jsonl`；验证：导出文件每题带 `meta.reviewState`，kept/edited 题总数 = 最终题量

## 5. 校验与固化

- [ ] 5.1 写 `eval/scripts/validate.py`：校验 schema 完整性、所有 `gold` 锚点可解析到当前 ACTIVE chunk、问题去重、配比符合目标、C/D 类必填字段存在、跨辖区噪声文档未出题；验证：故意注入一条坏数据（不存在的 chunkIndex）时脚本报错并指出题号
- [ ] 5.2 对 `golden.v1.jsonl` 跑校验并修复不合格题目；验证：校验全绿，输出题量/分类配比/覆盖文档数摘要
- [ ] 5.3 生成 `eval/manifest.json`：语料快照 hash、chunk 快照统计、LLM 模型版本与生成时间、实际配比与偏差说明；验证：文件可被 `validate.py` 读取且字段完整
- [ ] 5.4 提交改动到 `feature/rag-eval开发` 并推送远程（走 `upload.ps1` 或等价系统 git 步骤）；验证：`git status` 干净，远程分支可见新提交

## 6. 集成验证与汇报

- [ ] 6.1 跑 `openspec validate add-rag-eval-dataset --strict`；验证：零错误零警告
- [ ] 6.2 汇总汇报：体检结论（空壳率/表格拍平率/文号覆盖）、配对数与拒绝数、最终题量与配比、校验结果、已知局限（表格题未出、C 类待 runner 复核）；验证：汇报文本写入 `tasks.md` 末尾「实施结果小结」
- [ ] 6.3 把本轮遇到的报错与阻塞记入 `IssueLog.xlsx`（时间/分支/change id/阶段/报错/影响/方案/状态/验证）；验证：表格新增对应行

## Verification

- 数据集可用：`validate.py` 全绿，`golden.v1.jsonl` 约 90 题，五类齐全
- 锚点稳定：所有 `gold` 用 `docId`+`chunkIndex`，无 `chunkId` 字段；模拟"重建后主键变化"仍可重新解析
- 只读约束：执行前后 `wiki_chunk` 行数（2061）与状态分布（全 ACTIVE）不变
- 可复现：`manifest.json` 记录模型版本、`temperature=0`、语料与 chunk 快照

## Rollback

- 无数据库与后端改动，回滚只需删除 `eval/` 目录或回退分支提交
- 远程分支 `feature/rag-eval开发` 未合并前可随时弃用；合并前需负责人在远程平台验收
- 已生成的 LLM 候选题落在 `eval/tmp/`（gitignore），删除不影响仓库
