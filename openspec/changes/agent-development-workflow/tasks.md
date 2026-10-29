# Tasks

## 1. 三角色协作规则

- [x] 1.1 原子任务 P1-T1：重写三份角色规则形成一致的协作闭环；按下列固定验收及命令由全新测试智能体逐项验证。

### 固定验收（P1-T1，所有轮次保持原文）

A1. 三份文件清楚区分共同规则、总管、开发、测试职责；任务卡明确角色文件、范围、验收、验证前提、命令、预期及交付；开发负责实现和自检，总管不代替独立测试判 PASS。
    verify：`Get-Content -Raw -Encoding UTF8 AGENTS.md,AGENTS_rules.md,AGENTS_test.md`
    预期：命令退出码 0；逐文件检查上述职责及交接内容齐全且不互相冲突。

A2. 每轮新建不继承开发及上轮测试对话的测试实例；记录实例标识、基线、含新增文件的被测内容及前后校验，测试时停止开发写入；无法建立实例或确认快照不得通过；说明共享文件并非盲测。
    verify：`Get-Content -Raw -Encoding UTF8 AGENTS.md,AGENTS_rules.md,AGENTS_test.md`
    预期：命令退出码 0；三份文件对实例、快照、并发写入、失败处理和共享工作区的规定一致。

A3. 固定验收逐项记录预期、实际、命令及证据；清单外重大回归单独报告并阻断交付，风格意见不计 FAIL，未执行或不足以判断时为证据不足；基线未运行不得称既有失败。
    verify：`Get-Content -Raw -Encoding UTF8 AGENTS.md,AGENTS_test.md`
    预期：命令退出码 0；上述所有场景均有明确处置，且不能把证据不足当作 PASS。

A4. 失败转交保留原始报告、验收、复现与版本证据；修改后用新测试实例复测，最多三轮，第三轮 FAIL 停止上报；普通试错与需追踪问题分级，报告按顺序写入；总管核对证据并按风险触发独立复核。
    verify：`Get-Content -Raw -Encoding UTF8 AGENTS.md,AGENTS_rules.md,AGENTS_test.md`
    预期：命令退出码 0；三轮计数、交接、记录及复核触发条件可执行且一致。

A5. 规则保留现有工作区、任务分支、OpenSpec、上传和 main 授权边界；超过十个文件仅提示影响检查，相关高风险事项按既定规则升级；验证命令可针对本任务创建的测试预先约定；测试临时脚本的允许范围、清理及基线比较约束清楚。
    verify：`Get-Content -Raw -Encoding UTF8 AGENTS.md,AGENTS_rules.md,AGENTS_test.md`
    预期：命令退出码 0；逐条检查上述边界与用户确认的方案一致，且不存在旧规则与新规则冲突。

A6. OpenSpec 规划工件能够通过严格验证。
    verify：`openspec validate agent-development-workflow --strict`
    预期：退出码 0，输出 change valid。

### 执行与交付

开发允许修改三份规则文件；不修改业务、脚本、CI、数据库、IssueLog 历史或 OpenSpec 规划工件。开发提交文件清单、实际自检、影响及未验证项。总管保存完整基线及快照并派发全新测试；每轮取得独立报告和现行规则要求的独立批语，全部通过后检查待上传文件。回滚以任务开始前的三份规则原始内容为准，保留问题及测试证据。
