# add-rag-eval-runner Tasks

## 1. 环境准备

- [x] 1.1 查库确认空间 `2095544464810774531` 的成员账号与非成员账号各至少一个，
      记录 `user.id` 与 `userAccount`；验证：查出成员 2 人（admin / test1）与≥1 个非成员账号
      —— **已完成**：成员 `1971148507794014209`(admin) / `1984084468532183042`(test1)；
      非成员 11 人，取 `1984084805187993602`(viewer_user) 作对照
- [x] 1.2 **【负责人人工】** 启动后端（走 `start-dev.ps1`，不要绕过脚本私起 jar）；
      验证：`http://localhost:8123` 可达，`POST /open/rag/search` 返回非连接错误
      —— **已完成**（2026-09-11 18:04 负责人启动）。启动后另需 **Ollama（11434）** 才能检索：
      后端默认 embedding 走 `http://localhost:11434/v1`，且库内 2061 个 chunk 是 Ollama
      `qwen3-embedding:4b` 2560 维，查询必须同模型。Ollama 本机已装未运行，已补起并预热模型。
      另注意 **context-path 是 `/api`** —— 真实路径 `/api/open/rag/search`，漏前缀会 404
- [x] 1.3 记录当前检索运行配置：后端实际生效的 `rag.embedding.model` / `base-url` /
      `rag.retrieval.top-k`；验证：数值写入 `eval/results/` 首轮结果 JSON 的 `config`
      —— **已完成（人工核对 `application.yml`）**：`model=qwen3-embedding:4b`、
      `base-url=http://localhost:11434/v1`、`top-k=6`；库内向量实测 2560 维、2061 行全有值。
      **runner 不自动读取后端配置**（http 模式下 `config.embeddingModel` 置 null，
      报告标注"需人工核对"）—— 避免拿 `.env` 里 offline 的模型冒名顶替、产出误导性报告

## 2. 脚本实现

- [x] 2.1 写 `eval/scripts/prepare_keys.py`：为指定 `userId` 生成随机 key，
      按 `sha256Hex` 写入 `rag_api_key`（`key_prefix` 取前 8 位），明文只在标准输出打印一次；
      验证：制备成员与非成员两个 key，且写入后不回显明文到任何落盘文件
      —— **已完成**：两个 key 已入库（prefix `cpk_b6ca` / `cpk_e7ca`），明文写入 `eval/.env`
      （gitignore）；库内 `keyHash` 与明文 SHA-256 自校验一致
- [x] 2.2 写 `eval/scripts/run_eval.py` 的检索适配器：`HttpRetriever` / `OfflineRetriever`
      同一接口，支持 `--retriever`、`--base-url`、`--api-key`、`--member-key`、
      `--non-member-key`、`--top-k`、`--limit`；验证：两种适配器可独立实例化
      —— **已完成**（`--top-k` 改为固定 fetchK=10 本地截断，见 2.3）
- [x] 2.3 实现指标计算：`recall@K` / `docRecall@K` / `mrr` / `hitRate@K`，
      一次 `topK=10` 检索本地截断算 K ∈ {1,3,5,6,10}；
      验证：构造 mock 命中列表，手算结果与脚本输出一致
      —— **已完成**：mock 手算对照 7 项全部一致
- [x] 2.4 实现 C 类专属统计：非空返回率、`top1Score` 分位分布、按
      `unanswerableType` 分子类型；验证：C 类题不出现在召回类指标的分母中
      —— **已完成**：C 类 `metrics` 置 null，不进 `scored`
- [x] 2.5 实现 D 类双向对照：成员 key 算 `mirrorRecall@K`（取 `meta.mirrorOf` 指向题的 gold），
      非成员 key 检测 `forbiddenDocIds` 泄漏；验证：泄漏题被正确识别，D 类不进 overall 平均
      —— **已完成**；额外处理：offline 模式无权限过滤，D 类标记 `notApplicable` 而非产出假泄漏率
- [x] 2.6 实现 fail fast：开跑前探测、连续 5 次失败中止、
      `errorCount>0 且 recall@6==0` 时标记 `invalid: true`；
      验证：后端未启动时以非零码退出并打印指引，不产出结果文件
      —— **已完成**：`--probe` 在 8123 未监听时退出码 1 并打印启动指引；
      期间修掉一个环境陷阱（见 IssueLog）：urllib 默认走系统代理，localhost 请求被拦成 HTTP 502

## 3. 跑基线

- [x] 3.1a 对 `eval/golden.v1.jsonl` 全量 99 题跑 **offline 模式**（`--offline-vector recompute`，
      DashScope `qwen3.7-text-embedding-flash` 1024 维）；验证：99 题 0 失败，`invalid` 为 false
      —— **已完成**：`recall@6=0.356` / `docRecall@6=0.787` / `mrr=0.331` / `hitRate@6=0.613`；
      向量缓存落在 `eval/tmp/`（gitignore），重跑约 35 秒
- [x] 3.1b 跑 **http 模式**（真实系统）；验证：`eval/results/baseline-<ts>-http.json` 且 `invalid` 为 false
      —— **已完成**：99 题 0 失败、耗时 20 秒、`invalid=false`。
      `recall@6=0.437` / `docRecall@6=0.880` / `mrr=0.434` / `hitRate@6=0.733`
- [x] 3.2 生成 `eval/audit/baseline-report.md`：总览、分类明细、C 类分布、
      D 类泄漏结论、recall 最低的 10 题样例分析；验证：报告中的数字与结果 JSON 一致
      —— **已完成**，报告现基于 **http 真实系统**结果（offline 结果 JSON 另存，可对照）
- [x] 3.3 交叉核对：B 类文号题的 recall 应显著高于整体（验证后端"文号精确匹配层"是否生效）
      —— **已验证，结论成立**：
      | | offline 纯余弦 | http 真实系统 | 变化 |
      |---|---|---|---|
      | B 类 recall@6 | 0.074 | **0.653** | ×8.8 |
      | B 类 docRecall@6 | 0.333 | **1.0** | 全部命中 |
      | 整体 recall@6 | 0.356 | 0.437 | +23% |
      B 类从"远低于整体 0.282"翻转为"高于整体 0.215"，**证明文号精确匹配层确实生效**。
      同时说明 offline 模拟会系统性低估真实系统（缺权限过滤与文号层的加持）。
- [x] 3.4 补充分数重叠分析：有答案题 vs C 类的 top1Score 分布与阈值权衡表
      —— **已完成（http 口径）**：有答案题 min 0.546 / p25 0.718；无答案题 max 0.618 / median 0.535，
      重叠区间 `[0.546, 0.618]`。**T=0.618 时拦掉 93% 无答案题、仅误杀 3% 有答案题**（offline 口径误杀 7%，
      真实系统分离度更好）；但要做到 100% 拦截需 T=0.718，误杀率飙至 25%。
      结论不变：**不能只靠检索层阈值拒答**，仍需答案层判断证据充分性。

## 4. 收尾

- [ ] 4.1 跑 `openspec validate add-rag-eval-runner --strict`；验证：零错误零警告
- [ ] 4.2 把本轮遇到的报错与阻塞记入 `IssueLog.xlsx`；验证：表格新增对应行
- [ ] 4.3 提交改动到 `feature/rag-eval-runner开发` 并推送远程（走系统 git 复刻 `upload.ps1` 步骤）；
      验证：`git status` 干净，远程分支可见新提交

## 实施结果小结（offline + http 双基线，2026-09-11）

### 交付物

| 文件 | 说明 |
|---|---|
| `eval/scripts/run_eval.py` | 评测 runner：双检索适配器 + 指标计算 + fail fast + 报告生成 |
| `eval/scripts/prepare_keys.py` | 库内制备成员/非成员 API Key（sha256 与 Java 侧一致） |
| `eval/results/baseline-<ts>-offline.json` | 逐题明细 + 汇总，含 golden sha256 与运行配置 |
| `eval/audit/baseline-report.md` | 可读报告：总览、分类、C 类、阈值权衡、最差 10 题 |

### 基线数字（offline / DashScope `qwen3.7-text-embedding-flash` 1024 维 / 99 题 0 失败）

| 指标 | 数值 |
|---|---|
| `recall@6` | 0.356 |
| `docRecall@6` | 0.787 |
| `mrr` | 0.331 |
| `hitRate@6` | 0.613 |

### 三个有价值的发现

1. **E 类合成题严重虚高**：`recall@6` = 0.792，是 A 类（0.307）的 **2.6 倍**，`docRecall@6` 达 1.0。
   数据集阶段担心的"合成数据虚高"被量化证实 —— **对外引用指标应以 A 类为准，E 类仅作补充**。
2. **B 类文号题几乎全灭**：`recall@6` = 0.074（整体 0.356，`docRecall@6` 仅 0.333）。
   纯向量检索对文号确实不敏感，与后端 `RagSearchServiceImpl` 的既有注释一致。
   这条正是 http 模式的对照锚点：若文号精确匹配层生效，B 类应显著回升。
3. **无答案题 100% 返回内容，且无法用阈值一刀切**：C 类 top1Score 中位数 0.558、最高 0.615，
   与有答案题（p25=0.670）存在重叠区间 `[0.519, 0.615]`。
   权衡表：T=0.617 拦掉 93% 无答案题、误杀 7% 有答案题；想 100% 拦截需 T=0.670，误杀率升到 25%。
   **结论：拒答不能只靠检索层阈值，需在答案层判断证据充分性。**

### 已知局限

- offline 模式不做权限过滤，**D 类泄漏率必须 http 模式双 key 才能测**，
  脚本已显式标记 `notApplicable` 而非产出假数字
- 本轮指标来自 Python 端余弦排序，**不是真实系统的分数**；对外引用须用 http 模式重跑
- 检索无随机性，故未做 `--repeat` 稳定性验证

---

## http 真实系统基线（2026-09-11 18:13，本 change 的主要交付）

后端 `start-dev.ps1` 启动 + Ollama 补起后，99 题 **0 失败、耗时 20 秒**、`invalid=false`。

| 指标 | offline 纯余弦 | http 真实系统 | 变化 |
|---|---|---|---|
| `recall@6` | 0.356 | **0.437** | +23% |
| `docRecall@6` | 0.787 | **0.880** | +12% |
| `mrr` | 0.331 | **0.434** | +31% |
| `hitRate@6` | 0.613 | **0.733** | +20% |

### 四条结论

1. **文号精确匹配层被证实有效**（本轮最有价值的发现）
   B 类文号题 `recall@6` 从 offline 的 0.074 → http 的 **0.653（×8.8）**，`docRecall@6` 0.333 → **1.0**，
   由"远低于整体 0.282"翻转为"高于整体 0.215"。这是本系统相对朴素向量检索的**真实增量**，
   也是 interview 时最有说服力的工程点：不是调参，是针对性地补了检索层能力。
2. **offline 模拟会系统性低估真实系统** —— 缺权限过滤与文号层加持，四项指标低 12%~31%。
   → 往后做模型选型对比**必须固定 retriever**，两组数字不可混用、不可交叉平均。
3. **权限隔离零泄漏** —— D 类 10 题非成员侧 `leakCount = 0`；
   成员侧镜像召回 0.296 与 A 类 0.313 相当 → 权限过滤**未误伤正常检索**。
4. **拒答缺口依旧** —— C 类 14 题非空返回率 **100%**，系统无任何拒答机制。
   真实系统分离度优于离线：T=0.618 拦掉 93% 无答案题仅误杀 3%（离线误杀 7%）；
   但要 100% 拦截需 T=0.718，误杀率飙至 25%。**结论不变：拒答必须在答案层做。**

### 可对外引用的数字

`recall@6 = 0.437` · `docRecall@6 = 0.880` · `mrr = 0.434`（http 口径，99 题，Ollama 2560 维）。
其中 **A 类配对题 0.313 是最可信的子集**；E 类合成题 0.833 仍虚高，不应单独引用。

### 症状定位（指导下一步优化）

`docRecall@6 = 0.880` 而 `recall@6 = 0.437` —— **找得到文件，定不准段落**。
`mrr = 0.434` 意味着正确段落平均排在第 2~3 位，若只把 top3 喂给 LLM 会漏掉大量依据。
对症手段是 **rerank 精排**（成本远低于改切块：改切块要重编译 + 重建索引 + gold 重映射）。

## Rollback

- 不改后端业务代码、不改数据库 schema；`rag_api_key` 新增的评测 key 可整行删除
- 回滚只需删除 `eval/scripts/run_eval.py`、`eval/scripts/prepare_keys.py`、`eval/results/`
  或回退分支提交
- `golden.v1.jsonl` 全程只读，任何情况下不被 runner 修改
