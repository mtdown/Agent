# `run_ask_eval.py` —— RAG 答案层评测 runner（源码已丢失，此文档为其"身份证"）

> 本文件说明的对象是 `eval/scripts/__pycache__/run_ask_eval.cpython-313.pyc`。
> **源代码 `eval/scripts/run_ask_eval.py` 已不在工作区**，且 `git log` 显示它**从未提交过 git**
> （一直是 untracked），因此它是"丢了就没了"的那一类脚本。这里保留的是它的字节码与这份说明。
>
> 说明文档放在 `eval/scripts/`（上一级目录）而不是 `__pycache__/` 里，因为
> `.gitignore` 忽略 `__pycache__/`，放进去会随缓存一起被清掉。

- 源码版本：1106 行 / 50,753 字节
- 版本对应关系：`run_ask_eval.cpython-313.pyc` 的 mtime 为 **2026-09-15 23:18**，
  与归档包内源码的 mtime 完全一致 ⇒ 该 pyc 对应的是**最后一版源码**
  （另有 `run_ask_eval.cpython-312.pyc`，是同日更早一版，一并保留）
- 唯一源码副本：`.workbuddy/archive/20260916-rag-answer-quality.tar.gz`
  （该包同时含 4 份结果与 3 份报告）
- 恢复命令：
  ```bash
  tar -xzf .workbuddy/archive/20260916-rag-answer-quality.tar.gz eval/scripts/run_ask_eval.py
  ```

---

## 它是做什么的

**RAG 答案层评测 runner：消费 `POST /open/rag/ask` 的 SSE 流，产出答案层基线。**

它和检索层 `eval/scripts/run_eval.py` 是**互补关系，不是同一件事**：

| 层 | 回答的问题 | 主力脚本 |
|---|---|---|
| 检索层 | 材料**找没找到** | `run_eval.py`（recall@k / docRecall@k / hitRate@k / mrr） |
| 答案层 | 答得**可不可靠** | `run_ask_eval.py`（拒答 / 引用 / 文号合规） |

两者**任何情况下不合并为总分** —— 这是脚本设计时就写死的约束。

## 四项零 LLM 成本判据（核心价值）

全部靠正则与结构判定，不花钱、可复算：

| 指标 | 含义 |
|---|---|
| 拒答正确率 | 应拒答题里真正拒答的比例 |
| 误拒率（有答案题） | 有答案的题被错误拒答的比例 |
| **引用覆盖率** | 实质陈述中带角标 `[n]` 的比例 |
| **文号合规率** | 提及公文号时是否用规范格式（如「渝府办发〔2026〕24号」），容忍全半角括号变体 |

**有据性（faithfulness）单独成维**，只做 LLM 预筛 + 人工抽样，**不可作判据** ——
本项目实测 LLM-as-Judge 精准率仅 **6%**，且必须送完整切片（只送 gold 段会有 45% 假阳性）。

## 拒答判定是两级的（这里踩过大坑）

1. **第一级·短语**：命中拒答短语只说明"存在信号"，**本身不判定** ——
   部分作答的局部对冲句也会出现这类短语。
2. **第二级·结构**：有信号时，还需满足 (a) 首个陈述句含**强**拒答短语，或
   (b) 全文没有任何带角标的实质陈述，才判为拒答。

⚠️ **判据必须以 SYSTEM_PROMPT 规定的规范表述为锚，不能穷举自然语言变体。**
实测教训：P3 把拒答措辞改成「本次提供的资料中未包含……」后，按旧措辞写的清单
**漏判 11/20 应拒答题**，拒答率从 93.75% **假跌**到 56.25%（那些答案其实全是真拒答）。
英文数据集（MHR）的判据同样**不能照抄上游的 `Insufficient information.`**，得锚我们自己的表述。

## 用法

```bash
# 1) 探测后端与 key（走 /open/rag/search，不消耗 LLM 调用）
python eval/scripts/run_ask_eval.py --probe

# 2) 默认抽样基线：应拒答类全量 + 其余按比例抽约 40 题（seed=42，共约 54 次问答）
python eval/scripts/run_ask_eval.py

# 3) 全量 423 题（必须显式开启）
python eval/scripts/run_ask_eval.py --full

# 4) 复算校验：从逐题明细独立重算汇总，比对一致性
python eval/scripts/run_ask_eval.py --recompute eval/results/ask-baseline-xxx.json

# 5) 与上一份基线对比（共同题目子集）
python eval/scripts/run_ask_eval.py --compare eval/results/ask-baseline-xxx.json

# 6) 有据性 LLM 预筛（可选，每道非拒答题一次额外调用；送完整切片）
python eval/scripts/run_ask_eval.py --faithfulness-screen
```

**落盘约定**（`ROOT` = `eval/`）：

- 结果 → `eval/results/ask-baseline-<YYYYmmdd-HHMMSS>.json`
- 报告 → `eval/audit/ask-baseline-report.md`

**结果文件结构**：`runId` / `layer` / `config` / `summary` / `recomputeConsistent` / `errors` / `perQuestion`
（`summary` 内含 `refusal.falseRefusal.rate`、`citationCoverage.macroRate`、`docNumberCompliance.rate`）

## 三条设计约束（防"看起来跑通了"）

1. **失败显式记录而非静默跳过**：error 事件 / 超时 / 流中断都写入 `perQuestion.error` 与 `errors[]`；
   连错 5 次判定环境级故障，中止整轮。
2. **汇总必须能由逐题明细独立复算且一致**：落盘前 self-check（`recomputeConsistent`），另有 `--recompute` 复核入口。
3. **报告必须披露**：实际调用次数、样本量与可分辨能力、与检索层的互补关系。

## 已知基线（改造前，2026-09-15）

> 54 题 0 失败 · 拒答率 93.75%（改判后）· 误拒率 0/38 · **引用覆盖率 85.4%** · **文号合规率 94.4%**

## 恢复后要注意的两件事

1. **检索层涨了不等于答案层会好。** 已有一个反例：P3 只改拒答措辞、检索层没动，
   引用覆盖率却从 85.36% 跌到 80.30%（−5.05pt）。**召回与答案是两条曲线。**
2. **本脚本是中文口径的**（`POLICY_SPACE_ID` 写死政务空间、拒答判据绑定中文规范表述）。
   要用于英文 MHR 数据集，必须另写判据层 —— 英文题 56% 是是非题（`Yes`/`no` 大小写不统一需归一化），
   301 题 null_query 期望固定串，**不能直接复用本脚本的拒答正则**。

## 同组丢失的脚本

| 脚本 | 状态 | 字节码 |
|---|---|---|
| `run_ask_eval.py` | 源码丢失 | `__pycache__/run_ask_eval.cpython-313.pyc`（313 / 312 两版均已保留） |
| `compare_ask_runs.py` | 源码丢失 | `__pycache__/compare_ask_runs.cpython-313.pyc` |
| `export_spotcheck.py` | 源码丢失 | 无字节码残留 |

三者源码均在 `.workbuddy/archive/20260916-rag-answer-quality.tar.gz` 内。
