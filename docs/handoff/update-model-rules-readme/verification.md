# 模型分工与 README 更新验证

日期：2026-11-23（Asia/Shanghai）。分支：`feature/model-rules-readme开发`。基线：`a8784092ed583b76cade5e80f159d70b312d8fc0`。

## 授权与范围

负责人明确要求「本次修改任务只由你来完成，不需要新建开发智能体」，本次由主智能体实施和静态检查，不创建子智能体，不标记独立测试 PASS。另经负责人授权移除 OpenAiStreamModel.java 的两个行尾空格；恢复后该文件无差异，未纳入交付。

实施范围仅 AGENTS.md、AGENTS_manager.md、README.md，加本 change 的 proposal/tasks/元数据和本报告，共 7 个文件。未修改代码、脚本、CI/CD、配置或评测结果。未读取本地密钥内容。未创建临时目录，无需管理员清理。

## 逐项结果

| 任务 | 实际核验 | 结论 |
| --- | --- | --- |
| 1.1 | 共同规则精确写入 gpt-6-astra/high、gpt-6.1-sol/medium；总管派发显式 model、reasoning_effort、fork_turns=none；不可用上报、当前会话不能靠文档切换 | 自检通过 |
| 1.2 | 二级标题顺序、全部徽章与基线一致；链接目标存在；新增内容逐项对照下表源码 | 自检通过 |
| 1.3 | 总表 0.851/0.967/0.945/0.626、四个题型行、21.2pt/17.3pt 提升、30.3pt→11.6pt 差距、0.25pt 历史重跑极差均由 JSON 核算；README 无 @6 或 top-6 残留 | 自检通过 |

| README 内容 | 已核对的依据 |
| --- | --- |
| 模型偏好与实际调用边界 | cloud_front/src/components/ModelSelector.vue（选择仅本地保存、后端由启动变量决定、重启提示） |
| 标准生产构建默认隐藏，开发期配置入口 | cloud_front/src/constants/modelProfiles.ts、cloud_front/vite/localEnvPlugin.ts |
| .env.local 加载，临时隧道显式开启并关闭密钥端点 | start-dev.ps1 的 Import-DevEnvFile、Tunnel/NoTunnel 与 CLOUDWIKI_DISABLE_LOCAL_ENV 分支 |
| 部署端口与备案 | deploy/docker-compose.yml 的 HTTP_PORT→8080、deploy/nginx.conf 的 listen 8080、deploy/README.md 的 HTTP_PORT=80 说明、cloud_front/src/layouts/BasicLayout.vue 的备案链接 |
| 召回指标与样本一致性 | eval/results/baseline-20260916-201335-http.json、baseline-20260915-091440-http.json、baseline-20260915-091252-http.json；三者 goldenSha256 一致 |
| MRR 含义 | eval/scripts/lib_rag_eval.py 的 compute_metrics：首个命中排名倒数，未命中为 0 |
| 516ms / p95 600ms | openspec/changes/archive/2026-09-16-boost-rag-passage-recall/proposal.md 的单独延迟实验；README 已区分延迟实验与召回基线 |

## 检查命令与证据

以下命令在仓库根目录执行；Python 核验退出 0，标题与徽章一致、missing=[]、指标断言全部满足。`git diff --check` 退出 0；`openspec validate update-model-rules-readme --strict` 退出 0，提示 skip_specs 允许零 delta。

```powershell
git diff --check
openspec validate update-model-rules-readme --strict
rg -n 'gpt-6-astra|gpt-6.1-sol|high|medium|fork_turns|不可用' AGENTS.md AGENTS_manager.md
rg -n '@6|@recall6' README.md
```

模型规则检索匹配预期参数；旧指标检索无匹配（退出 1，为预期）。数值、结构及链接的可复查核验命令：

```powershell
@'
from pathlib import Path
import json,re,subprocess,urllib.parse,hashlib
s=Path('README.md').read_text(encoding='utf-8')
old=subprocess.check_output(['git','show','a8784092ed583b76cade5e80f159d70b312d8fc0:README.md']).decode('utf-8')
assert re.findall(r'^## .+$',old,re.M)==re.findall(r'^## .+$',s,re.M)
assert re.findall(r'<img[^>]+>',old)==re.findall(r'<img[^>]+>',s)
assert not re.search(r'@6|@recall6|top-6|\.env\.dev',s)
missing=[]
for f in ['README.md','AGENTS.md','AGENTS_manager.md']:
 p=Path(f); text=p.read_text(encoding='utf-8',errors='strict')
 for t in re.findall(r'(?<!!)\[[^\]]+\]\(([^)]+)\)',text):
  if not t.startswith(('http://','https://','#','mailto:')) and not (p.parent/urllib.parse.unquote(t.split('#')[0])).exists(): missing.append((f,t))
assert not missing,missing
print('missing:',missing)
def load(n): return json.loads(Path('eval/results',f'baseline-{n}-http.json').read_text(encoding='utf-8'))
d,b,a=map(load,['20260916-201335','20260915-091440','20260915-091252'])
assert d['config']['goldenSha256']==b['config']['goldenSha256']==a['config']['goldenSha256']
for k in ['recall@10','docRecall@10','hitRate@10','mrr']:
 assert f"| `{k}` | **{d['overall'][k]:.3f}** |" in s
for label,cat in [('X 跨文档（新闻/解读提问、答案锚政策原文）','crossdoc'),('A 配对','pair'),('S 单文档','single'),('B 文号','docnum')]:
 r=d['byCategory'][cat]
 assert f"| {label} | {r['count']} | {r['recall@10']:.3f} | {r['docRecall@10']:.3f} |" in s
for k in ['recall@10','hitRate@10']:
 assert f"{100*(d['overall'][k]-b['overall'][k]):.1f}pt" in s
for r in [b,d]:
 assert f"{100*(r['overall']['docRecall@10']-r['overall']['recall@10']):.1f}pt" in s
assert f"{100*abs(a['overall']['recall@10']-b['overall']['recall@10']):.2f} 个百分点" in s
rules=Path('AGENTS.md').read_text(encoding='utf-8'); manager=Path('AGENTS_manager.md').read_text(encoding='utf-8')
assert '`gpt-6-astra` / `high`' in rules and '`gpt-6.1-sol` / `medium`' in rules
for t in ['model="gpt-6.1-sol"','reasoning_effort="medium"','fork_turns="none"']: assert t in manager
for f in ['AGENTS.md','AGENTS_manager.md','README.md']: print(f,hashlib.sha256(Path(f).read_bytes()).hexdigest())
print('document checks: PASS (self-check only)')
'@ | python -
```

核验后的实施文件 SHA256：

| 文件 | SHA256 |
| --- | --- |
| AGENTS.md | 022d7256aa8e17041d303a96e67f5f6f57bf01aa7b0b747f1f344436762d9559 |
| AGENTS_manager.md | c9d058053faf1e0edbdef3f12939f79b37b8c271134e46d20fe5c5dd5ac2f9fd |
| README.md | 51a44e29acf8e5663d8b8e1c4f113a90f0ba92b000251b002dc53fec873dc777 |

## 限制与收尾

未启动服务、未重跑模型评测、未验证线上站点、未创建独立测试实例，也未实际启动新模型子实例；规则约束的是后续任务，不表示当前会话模型已切换。上述为本次文档任务的验证边界，无产品行为变更。无 FAIL、未解决缺陷或临时目录清理事项。

L0 的 design 根据条件指令省略，CLI 可能仍显示 ready；skip_specs 明确声明零 delta，无需同步主规格。按本次单人执行授权完成文档自检后归档；不将该授权扩展为长期取消独立验收。

归档核验：源目录已移出 active，目标为 `openspec/changes/archive/2026-11-23-update-model-rules-readme/`，元数据、proposal 和 tasks 均保留，三个任务均完成；`openspec list --json` 不再列出本 change。上文 change 名称的严格校验命令在归档前执行。最终提交仅含本报告、三份实施文档和三个归档工件。
