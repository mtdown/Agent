## Context

见 proposal.md - Why。此处只列约束现状（均已实测）：

- **库侧**：`localhost:3307/Cloud`，`wiki_chunk` 共 **2061** 行、**216** 个 distinct `docId`、**全部 ACTIVE**，其中 1341 行带 `docNumber`；`document_wiki` 存活 218 篇。字段名为 `docId`（不是 `document_id`），另含 `docTitle`、`docNumber`、`headingPath`、`chunkIndex`。
- **语料侧**：`F:\AIProject\my\corpus-2026`，216 篇分 4 栏目（政策文件 57 / 部门解读 21 / 新闻发布会 10 / 媒体视角 128），每篇 `meta.json`（`metadataId` / `title` / `fileNum` / `publishDate` / `sourceUrl`）+ `content.md`。**F 盘标题与库内 `docTitle` 精确匹配 216/216**——映射无需模糊匹配。
- **配对现状**：以解读标题书名号内的政策名做规范化匹配，部门解读 17/21、新闻发布会 8/10 可配到政策原文，媒体视角仅 4/128（新闻体标题不带书名号）。合计 **25 组**可用配对。
- **代码事实**：`WikiRagIndexServiceImpl.doIndexDocument` 第 69 行走 `invalidateByDocId`——重建索引时旧 chunk 置 INVALID 后插入新行，**同一逻辑 chunk 会拿到新主键**；`rebuildAll` 还会用 `isUpToDate` 跳过未变更文档。
- **当前缺口**：`openspec/` 与 `cloud/src` 下检索 `评测题 / 评测集 / golden / benchmark` 零命中。

## Goals / Non-Goals

**Goals:**

- 产出一份**可复现、可校验、可长期维护**的 RAG 评测数据集（约 90 题）及其格式规范
- ground truth 锚点**跨索引重建稳定**，使后续切块策略、embedding 模型、检索参数的对比实验都基于同一把尺子
- 题目来源以**人类撰写的官方文本**为主，把 LLM 的作用限制在"把解读翻译成问题"，压住合成数据虚高
- 全程**只读**数据库与语料，不建表、不改业务代码、不影响线上索引

**Non-Goals:**

- 不设计指标计算与评测 runner（下一个 change）
- 不定义 LLM-as-Judge 的评分 prompt
- 不改动 `MarkdownChunker` 参数、`wiki_chunk` 结构或检索链路

## Decisions

### D1. ground truth 用 `docId + chunkIndex` 逻辑坐标，不用 chunk 主键

**决定**：每道题的 `gold` 记为 `{"docId": 123, "chunkIndex": 3}`，运行时由评测侧映射为当前 `chunkId`。

**理由**：`doIndexDocument` 是"旧行置 INVALID + 插新行"，换 embedding 模型或改切块策略后主键必然漂移。绑死主键的数据集会在第一次重建后整体失效。

**备选**：① 绑 `chunkId`（实现最简单，但重建即失效，已否决）；② 只绑 `docId` 到文档级（可算文档召回，无法算 chunk 级 Recall@K，区分度不足，已否决）。

**附带收益**：切块策略实验（改 600/100/80）只需重算 `chunkIndex` 映射，题目本身不用动。

### D2. 生成路径反转：问题从"解读"出，证据绑"政策原文"

**决定**：A 类题用 LLM 读**整篇官方解读**（而非单个 chunk）生成用户视角的问题；标准答案取**解读原文**中的对应段落；`gold` 绑到**政策原文**的 chunk。

**理由**：解读由官方撰写、措辞贴近真实提问，用它出题天然带"用户会怎么问"的视角；而政策原文才是检索目标。反过来（从政策原文出题）会生成"复述式"问题，检索几乎必然命中，区分度为零。

**GT 绑定范围（已与负责人确认）**：**只绑政策原文**，命中解读 chunk 计为未命中。代价是 Recall 偏低，但真实——系统若先返回解读，说明检索确实没找到政策原文。

### D3. 媒体视角 128 篇留库作干扰项，不出题

**决定**：`04-媒体视角` 的 128 篇不从评测语料剔除，也不为它们出题。

**理由**：它们留在检索池里，恰好模拟真实环境的语义噪声（同一政策的新闻报道会与政策原文高度相似），能提高评测区分度，且零成本。

**备选**：TF-IDF 二次配对（成本半天、命中率存疑，已否决）；从评测语料剔除（指标虚高、脱离真实，已否决）。

### D4. 数据集格式（JSONL，一行一题）

```json
{
  "id": "A-001",
  "category": "pair | docnum | unanswerable | permission | synthetic",
  "question": "重庆市地震应急预案中，Ⅲ级响应由谁启动？",
  "answer": "由市抗震救灾指挥部启动，……",
  "expectRefusal": false,
  "gold": [{"docId": 123, "chunkIndex": 3, "why": "第三章响应分级"}],
  "source": {
    "pairId": "p-01",
    "policyDocId": 123,
    "policyTitle": "重庆市地震应急预案",
    "interpretDocId": 456,
    "interpretTitle": "《重庆市地震应急预案》文字解读",
    "docNumber": "渝府办发〔2026〕12号"
  },
  "permission": null,
  "meta": {
    "generatedBy": "qwen-plus@2026-09-11",
    "temperature": 0,
    "reviewState": "kept | edited | dropped",
    "reviewNote": ""
  }
}
```

- `permission` 仅 D 类题非空：`{"visibleSpaces": [7], "forbiddenDocIds": [...]}`，评测侧用不同权限账号各跑一次，**泄漏即一票否决，不进平均分**。
- `expectRefusal: true` 仅 C 类无答案题。
- 文件固定为 `eval/golden.v1.jsonl`，配 `eval/manifest.json`（语料快照 hash、chunk 快照统计、LLM 模型版本、生成时间、题量与配比）。

### D5. 生题脚本直连 OpenAI 兼容 HTTP 接口，不起后端

**决定**：Python 脚本用环境变量（`LLM_BASE_URL` / `LLM_API_KEY` / `LLM_MODEL`）直连 OpenAI 兼容端点；**不改 pom、不改后端代码、不起 Spring 服务**。

**理由**：生题是一次性离线任务，为它新增 Controller 或测试脚手架不划算，也会污染主工程。

**备选**：复用 `RagLlmClient`（需起服务并新增入口，已否决）。

**可复现性**：`temperature=0`，模型名与生成时间写入 `manifest.json` 与每题 `meta`。

### D6. 人工筛选用本地静态 HTML 页面

**决定**：`eval/tools/review.html`，`file://` 直接打开，载入 `candidates.jsonl`，逐题展示「问题 / 标准答案 / 预期命中 chunk 片段」，支持 保留·编辑·删除，一键导出 `golden.jsonl`。

**理由**：约 200 道候选题要人过一遍，必须能看到上下文才好判断；纯前端零依赖，不需要起服务。

**备选**：CLI 逐题 y/n（看不到上下文、改题麻烦）；直接编辑 JSONL（易错、无校验，均否决）。

### D7. 校验脚本是数据集的门禁

`eval/scripts/validate.py` 在导出后强制检查：schema 字段完整、**所有 `gold` 锚点能解析到真实 ACTIVE chunk**、问题去重（规范化后文本相似度）、配比符合目标、C/D 类题必填字段存在、无跨省噪声题目（命中 `川办发` 等非渝文号的文档不出题）。校验不通过则数据集不得标记为 v1。

### D8. 语料体检只报告、不自动修

**决定**：Step 0 体检（空壳率 / 表格拍平率 / 文号覆盖率 / `pageText` 与入库文本差异 / 跨省噪声）只产出 `eval/audit/corpus-audit.md`，**不修改任何已入库内容**。

**理由**：216 篇已全量 ACTIVE 索引，自动修会触发重建并改变主键；且修语料属于另一个关注点。

**已知影响**：表格被拍平成逐行文本 → 本 change **不出表格类题目**，如实记录为数据集局限。

## Risks / Trade-offs

| 风险 | 影响 | 缓解 |
|---|---|---|
| LLM 合成题质量虚高 | 指标失真，结论不可信 | A 类答案强制取自解读原文；人工逐题筛选；合成补充题（E 类）只占 10–15 题并在报告中单列 |
| 配对误匹配 | 整道题失效（问题与证据对不上） | 25 组配对逐条人工核对；未匹配的 4 篇解读单独标注不用于出题 |
| 生成随机性 | 数据集不可复现 | `temperature=0`、模型版本与生成时间入 `manifest.json`、LLM 原始响应落 `eval/tmp/`（gitignore） |
| chunk 快照漂移 | 锚点解析失败 | 逻辑坐标 + manifest 记录快照统计；校验脚本在每次使用前重跑锚点可解析性 |
| C 类"无答案"名不副实 | 拒答率测了个寂寞 | 题目限定为库外实体（外省政策、虚构文号），并在下一个 change 的 runner 首次运行时复核 |
| 语料跨省噪声 | 题目指向错误辖区 | 体检标记；出题时过滤非渝文号文档（已发现 `川办发〔2026〕23号`） |
| 库侧改动（换 embedding / 重建） | 数据集与索引不同步 | 本 change 明确要求"先跑基线再换模型"；换模型属于后续实验，需重跑 manifest |

## Migration Plan

无生产迁移：不建表、不改业务代码、不动既有索引。

- **上线方式**：`eval/` 目录随任务分支提交，`golden.v1.jsonl` 通过校验后标记为 v1。
- **回滚**：删除 `eval/` 目录或回退提交即可，无副作用（数据库与后端均未改动）。
- **数据集演进**：后续修改走 `golden.v2.jsonl` + 新 manifest，保留旧版本以便纵向对比。

## Open Questions

1. **C 类无答案题的最终校验**放在本 change（人工保证涉及库外实体）还是下一个 change 的 runner（可程序化验证检索结果为空）？当前按前者处理，后者作为补充校验——不影响本 change 的 spec 与任务拆分。
2. **90 题中 A 类配额**在 40–50 之间浮动，取决于 25 组配对实际能生成多少道互不重复且答案可溯源的问题。若不足，缺口由 E 类合成题补齐，最终配比在 manifest 中如实记录。
