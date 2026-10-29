# 测试报告
Change ID／阶段 ID／原子任务 ID／轮次／时间：openspec-local-workflow／P1／P1-T6／第 1 轮／2026-10-29 15:22 +08:00
测试实例标识：/root/local_archive_tester（本轮新建独立实例）
总体结论：PASS
环境与基线：Windows PowerShell，分支 feature/agent-development-workflow开发，HEAD 1ffd9f08982a600719ea484eb7052fe3f3a85a3c；共享工作区已有任务相关未提交、未跟踪文件。未运行基线，任务卡未指定安全的基线运行方式，本次为固定条款和结构验收。
被测范围与快照：新增 .agents/skills/openspec-archive-change-local/SKILL.md；对照 .agents/skills/openspec-archive-change/SKILL.md，以及 change 下 proposal.md、design.md、specs/agent-development-workflow/spec.md、tasks.md。任务卡完整补丁 diff.patch SHA256 2ed4ec9674f83e8691213ef94e83a8bb7c5bbf777153bef96041aa6be8f333cd，正文与新增文件一致；acceptance.txt SHA256 5c9e5a2a9d8e8f6d860be8da77f5b41da7d8020576280cbf31a8bad85832db15。测试前后 Get-FileHash -Algorithm SHA256 对以上六文件和两输入文件逐项核对，均与任务卡一致；结果见 hashes-before.txt、hashes-after.txt（前者表格路径因 PowerShell 格式显示截断，顺序同任务卡；后者完整）。未修改被测文件。
验收项结果：
- F1：PASS
  操作及命令：Get-Content -Raw -Encoding UTF8 .agents/skills/openspec-archive-change-local/SKILL.md
  退出码与关键断言：0；第 2 行名称与目录匹配；第 8、10、14–15 行约束总管确认的 change、store、工件、tasks、阶段和独立验收。
  预期／实际：命令成功且具备规定前提／命令成功，规定前提均存在。
  证据：F1.txt、F1-exit.txt、skill-lines.txt。
- F2：PASS
  操作及命令：Get-Content -Raw -Encoding UTF8 .agents/skills/openspec-archive-change-local/SKILL.md
  退出码与关键断言：0；第 20–22 行规定 delta 全量 local sync、主规格核对和失败停止；第 15–16、26 行规定未完成、校验失败和目标冲突停止；第 26–28 行规定保留元数据、验证位置和状态；第 14 行规定明确授权不重复询问、超范围及用户停点停止。
  预期／实际：逐项具备同步、失败、冲突、归档验证及授权分支／各条款均存在。
  证据：F2.txt、F2-exit.txt、skill-lines.txt。
- F3：PASS
  操作及命令：分别执行 git diff --exit-code -- .agents/skills/openspec-archive-change/SKILL.md；python -X utf8 C:/Users/origin/.codex/skills/.system/skill-creator/scripts/quick_validate.py .agents/skills/openspec-archive-change-local；openspec validate openspec-local-workflow --strict。
  退出码与关键断言：依次 0、0、0；原版无 diff，技能输出 Skill is valid!，OpenSpec 输出 Change 'openspec-local-workflow' is valid。
  预期／实际：三命令分别成功、技能有效且 change valid／均符合。
  证据：F3-git.txt、F3-git-exit.txt、F3-skill.txt、F3-skill-exit.txt、F3-openspec.txt、F3-openspec-exit.txt。
固定验收缺陷：无。
清单外交付阻断问题：无。
未验证项与原因：无固定验收遗漏；本次依据任务卡核查技能文本及校验命令，未实际归档或同步。
变异检验：未做，任务卡未授权。
问题记录：无；无需空 IssueLog 记录或 HTML。
临时文件、数据及服务清理状态：证据保留在 tmp/openspec-local-p1t6-evidence/，未启动服务或修改数据。首次尝试生成行号证据的辅助 PowerShell 命令因空管道语法解析失败（退出码 1），随后修正命令成功；不影响固定命令结果。
风格意见（不计 FAIL）：无。
