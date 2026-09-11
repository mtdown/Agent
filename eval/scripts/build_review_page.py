# -*- coding: utf-8 -*-
"""生成人工筛选页 eval/tools/review.html：逐题展示问题/答案/证据 chunk，支持保留·删除·编辑并导出。

数据（含 gold chunk 原文）内联进 HTML，双击即可离线使用；进度存 localStorage，可断点续看。
"""
from __future__ import annotations

import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from lib_eval import EVAL_DIR, db_conn, load_env  # noqa: E402

CHUNK_PREVIEW = 1200

HTML = """<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>RAG 评测数据集 · 人工筛选</title>
<style>
:root{
  --bg:#f6f7f9; --panel:#fff; --line:#e3e6ea; --text:#1f2328; --muted:#6b7280;
  --accent:#2563eb; --keep:#16a34a; --drop:#dc2626; --warn:#d97706;
}
*{box-sizing:border-box}
body{margin:0;font:14px/1.65 -apple-system,BlinkMacSystemFont,"Segoe UI","Microsoft YaHei",sans-serif;
  background:var(--bg);color:var(--text)}
header{position:sticky;top:0;z-index:10;background:var(--panel);border-bottom:1px solid var(--line);
  padding:10px 16px;display:flex;gap:12px;align-items:center;flex-wrap:wrap}
header h1{font-size:16px;margin:0 12px 0 0}
.bar{display:flex;gap:8px;align-items:center;flex-wrap:wrap}
button{font:inherit;border:1px solid var(--line);background:#fff;border-radius:6px;padding:5px 12px;cursor:pointer}
button:hover{border-color:var(--accent);color:var(--accent)}
button.primary{background:var(--accent);color:#fff;border-color:var(--accent)}
button.keep{background:var(--keep);color:#fff;border-color:var(--keep)}
button.drop{background:var(--drop);color:#fff;border-color:var(--drop)}
.prog{font-variant-numeric:tabular-nums;color:var(--muted)}
select,input[type=search]{font:inherit;padding:5px 8px;border:1px solid var(--line);border-radius:6px}
#wrap{display:flex;height:calc(100vh - 52px)}
#list{width:340px;overflow:auto;border-right:1px solid var(--line);background:var(--panel)}
.item{padding:8px 12px;border-bottom:1px solid var(--line);cursor:pointer;display:flex;gap:8px;align-items:flex-start}
.item:hover{background:#eef2ff}
.item.active{background:#e0e7ff}
.item .idx{font-variant-numeric:tabular-nums;color:var(--muted);font-size:12px;min-width:52px}
.item .qq{flex:1;font-size:13px;overflow:hidden;display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical}
.tag{font-size:11px;padding:1px 6px;border-radius:4px;color:#fff;flex-shrink:0}
.t-pair{background:#2563eb}.t-docnum{background:#7c3aed}.t-unanswerable{background:#d97706}
.t-permission{background:#be123c}.t-synthetic{background:#0891b2}
.st-kept{color:var(--keep);font-weight:700}.st-dropped{color:var(--drop);text-decoration:line-through}
.st-pending{color:var(--muted)}
#main{flex:1;overflow:auto;padding:20px 24px}
.card{background:var(--panel);border:1px solid var(--line);border-radius:10px;padding:16px 18px;margin-bottom:14px}
.card h3{margin:0 0 10px;font-size:13px;color:var(--muted);font-weight:600;letter-spacing:.5px}
.q{font-size:17px;font-weight:600;line-height:1.5}
.a{white-space:pre-wrap;background:#f8fafc;border-left:3px solid var(--accent);padding:10px 12px;border-radius:0 6px 6px 0}
textarea{width:100%;font:inherit;border:1px solid var(--line);border-radius:6px;padding:8px;resize:vertical}
.chunk{border:1px solid var(--line);border-radius:8px;padding:10px 12px;margin-bottom:8px;background:#fff}
.chunk .h{font-size:12px;color:var(--muted);margin-bottom:6px}
.chunk .t{white-space:pre-wrap;font-size:13px;color:#374151;max-height:260px;overflow:auto}
mark{background:#fde68a;padding:0 2px;border-radius:2px}
.meta{font-size:12px;color:var(--muted);line-height:1.9}
.meta b{color:var(--text);font-weight:600}
.actions{display:flex;gap:10px;align-items:center;flex-wrap:wrap;margin-top:6px}
.note{width:100%;margin-top:8px}
.kbd{font:12px ui-monospace,monospace;background:#eef;border:1px solid var(--line);border-radius:4px;padding:1px 5px}
.ai{border-left:4px solid #94a3b8}
.ai-keep{border-left-color:#16a34a}
.ai-edit{border-left-color:#d97706}
.ai-drop{border-left-color:#dc2626}
.v-keep{background:#16a34a}.v-edit{background:#d97706}.v-drop{background:#dc2626}.v-error{background:#6b7280}
.score{display:inline-block;font-size:12px;color:var(--muted);margin-right:14px}
.score b{color:#111827;font-size:13px}
.issue{display:inline-block;font-size:11px;background:#fee2e2;color:#991b1b;border-radius:4px;padding:1px 6px;margin-right:6px}
.sug{border:1px dashed #cbd5e1;border-radius:8px;padding:10px 12px;margin-top:8px;background:#fff}
.sug .t{white-space:pre-wrap;font-size:12.5px;color:#475569;max-height:180px;overflow:auto}
</style>
</head>
<body>
<header>
  <h1>RAG 评测数据集 · 人工筛选</h1>
  <div class="bar">
    <button class="keep" onclick="act('kept')">保留 K</button>
    <button class="drop" onclick="act('dropped')">删除 D</button>
    <button onclick="act('pending')">待定 P</button>
    <span style="color:var(--line)">|</span>
    <button onclick="nav(-1)">上一题 ←</button>
    <button onclick="nav(1)">下一题 →</button>
    <span style="color:var(--line)">|</span>
    <select id="fcat" onchange="render()"><option value="">全部分类</option></select>
    <select id="fst" onchange="render()">
      <option value="">全部状态</option><option value="pending">待定</option>
      <option value="kept">已保留</option><option value="dropped">已删除</option></select>
    <select id="fv" onchange="render()">
      <option value="">AI 建议(全部)</option><option value="keep">AI:保留</option>
      <option value="edit">AI:待修</option><option value="drop">AI:建议删</option>
      <option value="fixable">AI:可补证据修复</option></select>
    <input type="search" id="fq" placeholder="搜索问题…" oninput="render()" style="width:150px">
    <span class="prog" id="prog"></span>
    <span style="color:var(--line)">|</span>
    <button class="primary" onclick="exportJson()">导出 golden.jsonl</button>
    <button onclick="resetAll()">重置</button>
  </div>
</header>
<div id="wrap">
  <div id="list"></div>
  <div id="main"></div>
</div>
<script>
const DATA = __DATA__;
const KEY = 'rag-eval-review-v1';
let state = JSON.parse(localStorage.getItem(KEY) || '{}');
let cur = 0, view = [];

function save(){ localStorage.setItem(KEY, JSON.stringify(state)); }
function st(id){ return (state[id] && state[id].st) || 'pending'; }
function esc(s){ return (s||'').replace(/[&<>]/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;'}[c])); }
function hl(text, quote){
  if(!quote) return esc(text);
  const flat = t => (t||'').replace(/[\\s，,。.；;：:、（）()《》""''\\-—－]/g,'');
  const f = flat(text), q = flat(quote);
  const i = f.indexOf(q);
  if(i < 0) return esc(text);
  // 把 flat 下标映射回原文下标
  let n = -1, j = 0;
  for(let k = 0; k < text.length && j <= i; k++){
    if(!/[\\s，,。.；;：:、（）()《》""''\\-—－]/.test(text[k])){ if(j === i){ n = k; break; } j++; }
  }
  if(n < 0) return esc(text);
  let m = n, cnt = 0;
  while(m < text.length && cnt < q.length){
    if(!/[\\s，,。.；;：:、（）()《》""''\\-—－]/.test(text[m])) cnt++;
    m++;
  }
  return esc(text.slice(0,n)) + '<mark>' + esc(text.slice(n,m)) + '</mark>' + esc(text.slice(m));
}
function render(){
  const cat = document.getElementById('fcat').value;
  const stt = document.getElementById('fst').value;
  const q   = document.getElementById('fq').value.trim();
  const v = document.getElementById('fv').value;
  view = DATA.filter(d => (!cat || d.category===cat) && (!stt || st(d.id)===stt) &&
                          (!q || d.question.includes(q)) &&
                          (!v || (v==='fixable' ? (d.ai && d.ai.fixable)
                                                : (d.ai && d.ai.verdict===v))));
  const L = document.getElementById('list');
  L.innerHTML = view.map((d,i)=>{
    const s = st(d.id);
    const av = d.ai ? `<span class="tag v-${d.ai.verdict}" title="AI 建议">${d.ai.verdict}</span>` : '';
    return `<div class="item ${i===cur?'active':''}" onclick="go(${i})">
      <span class="idx">${d.id}</span>
      <span class="tag t-${d.category}">${d.category.slice(0,4)}</span>
      <span class="qq">${esc(d.question)}</span>
      ${av}
      <span class="st-${s}">${s==='kept'?'✓':s==='dropped'?'✗':'·'}</span></div>`;
  }).join('');
  document.getElementById('prog').textContent =
    `已处理 ${Object.keys(state).length}/${DATA.length} · 保留 ${DATA.filter(d=>st(d.id)==='kept').length} · 删除 ${DATA.filter(d=>st(d.id)==='dropped').length}`;
  show();
}
function go(i){ cur = i; render(); }
function nav(d){ cur = Math.min(view.length-1, Math.max(0, cur+d)); render();
  document.querySelector('.item.active')?.scrollIntoView({block:'nearest'}); }
function show(){
  const d = view[cur];
  if(!d){ document.getElementById('main').innerHTML = '<div class="card">没有匹配的题目</div>'; return; }
  const s = state[d.id] || {};
  const q = s.q ?? d.question, a = s.a ?? d.answer;
  let html = `<div class="card"><h3>问题 · ${d.id} · <span class="tag t-${d.category}">${d.category}</span></h3>
    <textarea id="eq" rows="2">${esc(q)}</textarea></div>`;
  html += `<div class="card"><h3>标准答案</h3><textarea id="ea" rows="3">${esc(a)}</textarea></div>`;
  if(d.ai){
    const A = d.ai;
    const sc = Object.entries(A.scores||{}).map(([k,v])=>`<span class="score">${k} <b>${v}</b></span>`).join('');
    const iss = (A.issues||[]).map(t=>`<span class="issue">${esc(t)}</span>`).join('');
    html += `<div class="card ai ai-${A.verdict}"><h3>AI 评审建议 ·
      <span class="tag v-${A.verdict}">${A.verdict}</span>${A.fixable?' <span class="tag v-edit">补证据可修复</span>':''}</h3>
      <div style="margin-bottom:8px">${sc}</div>
      <div style="margin-bottom:8px">${iss||'<span style="color:var(--muted)">无问题标签</span>'}</div>
      <div class="meta"><b>理由：</b>${esc(A.reason||'')}</div>
      ${A.suggestion?`<div class="meta"><b>修改建议：</b>${esc(A.suggestion)}</div>`:''}
      ${A.hint?`<div class="meta" style="color:#b45309"><b>注意：</b>${esc(A.hint)}</div>`:''}`;
    if(A.suggest && A.suggest.length){
      html += `<div class="sug"><div class="h" style="font-size:12px;color:var(--muted);margin-bottom:6px">
        AI 认为还应标注为证据的段落（${A.suggest.length}）</div>` +
        A.suggest.map(x=>`<div style="margin-bottom:8px"><div style="font-size:12px;color:var(--muted)">chunkIndex ${x.chunkIndex}</div>
          <div class="t">${esc(x.text||'')}</div></div>`).join('') +
        `<button onclick="adopt()">采纳：把这些段落并入 gold（${A.suggest.length} 段）</button></div>`;
    }
    html += `</div>`;
  }
  if(d.gold && d.gold.length){
    html += `<div class="card"><h3>证据 chunk（${d.gold.length}）</h3>` +
      d.gold.map(g=>`<div class="chunk"><div class="h">docId ${g.docId} · chunkIndex ${g.chunkIndex} · ${esc(g.why||'')}</div>
        <div class="t">${hl(g.text||'', g.quote)}</div></div>`).join('') + `</div>`;
  } else if(d.category === 'unanswerable'){
    html += `<div class="card"><h3>预期</h3><div class="a">系统应当拒答（知识库无此内容）</div>
      <div class="meta">库外依据：<b>${esc(d.meta.outOfScopeReason||'')}</b></div></div>`;
  } else if(d.category === 'permission'){
    html += `<div class="card"><h3>权限对照</h3><div class="a">非成员账号检索必须 0 命中；成员账号应能命中（镜像题 ${esc(d.meta.mirrorOf||'')}）</div>
      <div class="meta">目标空间 <b>${d.permission.targetSpaceId}</b> · 禁止文档 <b>${d.permission.forbiddenDocIds.join(', ')}</b><br>
      成员 ${d.permission.memberUserIds.length} 人 · 非成员账号 ${d.permission.nonMemberUserIds.length} 个</div></div>`;
  }
  html += `<div class="card"><h3>元信息</h3><div class="meta">
    来源政策：<b>${esc(d.source.policyTitle||'')}</b><br>
    文号：<b>${esc(d.source.docNumber||'-')}</b> ｜ 解读篇：<b>${esc(d.source.interpretTitle||'-')}</b><br>
    qType：${esc(d.meta.qType||'')} ｜ 关键术语：${esc(d.meta.evidenceKey||'-')} ｜ 生成：${esc(d.meta.generatedBy||'')}
    </div></div>`;
  html += `<div class="card"><h3>操作</h3><div class="actions">
    <button class="keep" onclick="act('kept')">保留 (K)</button>
    <button class="drop" onclick="act('dropped')">删除 (D)</button>
    <button onclick="act('pending')">待定 (P)</button>
    <span style="color:var(--muted)">当前状态：<b class="st-${st(d.id)}">${st(d.id)}</b></span></div>
    <textarea class="note" id="en" rows="2" placeholder="备注（可选）：为什么删／怎么改">${esc(s.note||'')}</textarea>
    <div class="meta" style="margin-top:6px">快捷键 <span class="kbd">K</span> 保留 ·
      <span class="kbd">D</span> 删除 · <span class="kbd">←</span> <span class="kbd">→</span> 切换</div></div>`;
  document.getElementById('main').innerHTML = html;
}
function sync(){
  const d = view[cur]; if(!d) return;
  state[d.id] = state[d.id] || {};
  const eq = document.getElementById('eq'), ea = document.getElementById('ea'), en = document.getElementById('en');
  if(eq) state[d.id].q = eq.value;
  if(ea) state[d.id].a = ea.value;
  if(en) state[d.id].note = en.value;
  save();
}
function act(s){
  const d = view[cur]; if(!d) return;
  sync();
  state[d.id].st = s;
  save();
  if(cur < view.length - 1) nav(1); else render();
}
function exportJson(){
  sync();
  const out = DATA.filter(d => st(d.id) === 'kept').map(d=>{
    const s = state[d.id] || {};
    const o = JSON.parse(JSON.stringify(d));
    if(s.q) o.question = s.q;
    if(s.a) o.answer = s.a;
    o.meta.reviewState = 'kept';
    o.meta.reviewNote = s.note || '';
    o.gold.forEach(g=>{ delete g.text; });
    return o;
  });
  const dropped = DATA.filter(d => st(d.id) === 'dropped').length;
  const pending = DATA.filter(d => st(d.id) === 'pending').length;
  const blob = new Blob([out.map(o=>JSON.stringify(o)).join('\\n') + '\\n'], {type:'application/jsonl'});
  const a = document.createElement('a');
  a.href = URL.createObjectURL(blob);
  a.download = 'golden.v1.jsonl';
  a.click();
  alert(`导出 ${out.length} 题（已删除 ${dropped}，仍待定 ${pending}）`);
}
function adopt(){
  const d = view[cur]; if(!d || !d.ai || !d.ai.suggest) return;
  const docId = (d.gold && d.gold[0]) ? d.gold[0].docId : (d.ai.suggestDocId || 0);
  const have = new Set((d.gold||[]).map(g=>g.chunkIndex));
  let n = 0;
  d.ai.suggest.forEach(x=>{
    if(!have.has(x.chunkIndex)){
      d.gold = d.gold || [];
      d.gold.push({docId: docId, chunkIndex: x.chunkIndex, why: 'AI 评审补充', quote: '', text: x.text});
      n++;
    }
  });
  d.ai.suggest = [];
  d.ai.fixable = false;
  render();
  alert('已采纳 ' + n + ' 段并入 gold，请重新判断是否保留本题');
}
function resetAll(){ if(confirm('确认清空全部筛选进度？')){ state = {}; save(); render(); } }
document.addEventListener('keydown', e=>{
  if(/INPUT|TEXTAREA|SELECT/.test(document.target?.tagName || e.target.tagName)) return;
  const k = e.key.toLowerCase();
  if(k==='k') act('kept'); else if(k==='d') act('dropped'); else if(k==='p') act('pending');
  else if(e.key==='ArrowLeft') nav(-1); else if(e.key==='ArrowRight') nav(1);
});
// 分类下拉
const cats = [...new Set(DATA.map(d=>d.category))];
document.getElementById('fcat').innerHTML = '<option value="">全部分类</option>' +
  cats.map(c=>`<option value="${c}">${c} (${DATA.filter(d=>d.category===c).length})</option>`).join('');
render();
</script>
</body>
</html>
"""


def main():
    cfg = load_env()
    src = os.path.join(EVAL_DIR, "candidates.jsonl")
    rows = [json.loads(l) for l in open(src, encoding="utf-8") if l.strip()]

    # 载入 AI 评审结论
    ai_path = os.path.join(EVAL_DIR, "ai-judge.jsonl")
    ai_map = {}
    if os.path.exists(ai_path):
        for line in open(ai_path, encoding="utf-8"):
            if line.strip():
                x = json.loads(line)
                ai_map[x["id"]] = x
        print(f"载入 AI 评审 {len(ai_map)} 条")

    # 内联 gold chunk 文本（含 AI 建议补充的段落）
    pairs = {(g["docId"], g["chunkIndex"]) for r in rows for g in r["gold"]}
    for r in rows:
        ai = ai_map.get(r["id"])
        if not ai:
            continue
        did = r["gold"][0]["docId"] if r["gold"] else r.get("source", {}).get("policyDocId")
        for ci in ai.get("missingChunks") or []:
            if did:
                pairs.add((int(did), int(ci)))
    text_map = {}
    if pairs:
        conn = db_conn(cfg)
        try:
            with conn.cursor() as cur:
                cur.execute("SELECT docId, chunkIndex, chunkText FROM wiki_chunk WHERE status='ACTIVE'")
                for did, idx, txt in cur.fetchall():
                    if (int(did), int(idx)) in pairs:
                        text_map[f"{did}:{idx}"] = (txt or "")[:CHUNK_PREVIEW]
        finally:
            conn.close()
    for r in rows:
        for g in r["gold"]:
            g["text"] = text_map.get(f"{g['docId']}:{g['chunkIndex']}", "")
        ai = ai_map.get(r["id"])
        if not ai:
            continue
        did = r["gold"][0]["docId"] if r["gold"] else r.get("source", {}).get("policyDocId")
        sug = [{"chunkIndex": int(ci),
                "text": text_map.get(f"{did}:{ci}", "")[:CHUNK_PREVIEW]}
               for ci in (ai.get("missingChunks") or [])]
        sc = ai.get("scores", {})
        r["ai"] = {
            "verdict": ai.get("verdict", "error"),
            "scores": sc,
            "issues": ai.get("issues", []),
            "reason": ai.get("reason", ""),
            "suggestion": ai.get("suggestion", ""),
            "suggest": sug,
            "suggestDocId": int(did) if did else 0,
            # 答案本身站得住、只是证据标注不全 → 补 gold 即可，不必丢题
            "fixable": bool(sug) and sc.get("answerability", 0) >= 4
                       and sc.get("faithfulness", 0) >= 4,
            # 建议补充的段落过多 → 说明这题的答案横跨整篇，更适合收窄问题而非堆证据
            "hint": ("该题答案横跨整篇文档（AI 建议补充 %d 段），"
                     "建议把问题收窄到具体条款或指标，而不是把整篇都标为证据" % len(sug))
                    if len(sug) >= 6 else "",
        }

    data = json.dumps(rows, ensure_ascii=False).replace("</", "<\\/")
    out = os.path.join(EVAL_DIR, "tools", "review.html")
    os.makedirs(os.path.dirname(out), exist_ok=True)
    open(out, "w", encoding="utf-8").write(HTML.replace("__DATA__", data))
    size = os.path.getsize(out) / 1024
    print(f"内联 {len(rows)} 题 / {len(text_map)} 个 chunk 片段")
    print(f"产出 -> {out} ({size:.0f} KB)")


if __name__ == "__main__":
    main()
