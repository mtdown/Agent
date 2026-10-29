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

## 2. 单独拆分总管规则

- [x] 2.1 原子任务 P2-T1：将总管职责迁入独立文件并保留共同入口；按下列固定验收由全新测试实例验证。

### 固定验收（P2-T1，各轮保持原文）

B1. 根 AGENTS.md 保留全部共同工作区、分支、OpenSpec、服务、UTF-8 和共享资源边界，并明确总管读取 AGENTS_manager.md，开发和测试分别读取对应角色文件；根文件不再包含总管专属任务卡及工作流程正文。
    verify：`Get-Content -Raw -Encoding UTF8 AGENTS.md,AGENTS_manager.md,AGENTS_rules.md,AGENTS_test.md`
    预期：退出码 0；逐文件审阅角色入口及共同边界准确、完整。

B2. AGENTS_manager.md 包含原根文件中总管职责、开发和验证任务卡、独立测试与快照、判定与修复循环、问题记录、升级、阶段上传和 IssueLog 项目经验；迁移不得改变这些原有行为契约。
    verify：`git show HEAD:AGENTS.md; Get-Content -Raw -Encoding UTF8 AGENTS.md,AGENTS_manager.md`
    预期：退出码 0；逐项对照原文件，总管规则均能在新总管文件或根共同规则找到，行为契约无丢失。

B3. 开发和测试文件中的共同规则、角色文件和任务卡引用均能定位现有文件；四份规则之间没有与迁移相关的矛盾，总管须显式读取其角色文件。
    verify：`Get-Content -Raw -Encoding UTF8 AGENTS.md,AGENTS_manager.md,AGENTS_rules.md,AGENTS_test.md`
    预期：退出码 0；检查所有角色入口与链接，内容一致。

B4. OpenSpec change 通过严格校验。
    verify：`openspec validate agent-development-workflow --strict`
    预期：退出码 0，输出 change valid。

允许开发修改 AGENTS.md、AGENTS_manager.md、AGENTS_rules.md、AGENTS_test.md；禁止修改业务代码、脚本和 OpenSpec。测试应给出逐项条款及具体行号，保留原始命令输出和快照前后哈希。回滚使用本阶段基线提交 57f9b31ff9f8be267361c188d1b2211610167866 恢复相关规则并移除新增总管文件。

## 3. OpenSpec 融入三角色协作

- [x] 3.1 原子任务 P3-T1：将六个 OpenSpec 技能融入三角色工作流，并由全新测试实例核验以下固定验收。

### 固定验收（P3-T1，各轮保持原文）

C1. 总管规则按工作区检查、任务分支、explore、propose、总管派单、开发执行 apply、独立测试的顺序描述完整开发流程，并明确用户已授权完整实施时不因规划完成重复请求启动授权。
    verify：`Get-Content -Raw -Encoding UTF8 AGENTS.md,AGENTS_manager.md,AGENTS_rules.md,AGENTS_test.md`
    预期：退出码 0；逐项检查步骤、角色、先后顺序和授权边界清楚一致。

C2. 开发执行 apply 仅实施总管分配的原子任务并交回自检；总管在全新测试实例 PASS 后勾选 tasks.md；FAIL、证据不足和 BLOCKED 不得勾选或上传。
    verify：`Get-Content -Raw -Encoding UTF8 AGENTS_manager.md,AGENTS_rules.md,AGENTS_test.md`
    预期：退出码 0；开发与总管职责、所有判定分支和任务状态一致。

C3. 测试失败时总管记录失败及修复任务并按需使用 update-change；不得因失败降低固定验收；需求或设计变化时更新有关工件，修复后新建测试实例，三轮上限及升级规则保持不变。
    verify：`Get-Content -Raw -Encoding UTF8 AGENTS_manager.md,AGENTS_rules.md,AGENTS_test.md`
    预期：退出码 0；逐项核对失败、修复、变更和升级条件无矛盾。

C4. 每阶段 PASS 可按原规则上传；全部阶段通过后先 sync-specs 并验证主规格，再 archive-change 并验证归档，最后上传收尾变更；同步失败、不完整验收或无法验证时不得归档或上传为完成；不自动合并 main。
    verify：`Get-Content -Raw -Encoding UTF8 AGENTS_manager.md,AGENTS_rules.md,AGENTS_test.md`
    预期：退出码 0；各步骤顺序、执行者、失败处置和 main 授权边界明确。

C5. 四份规则保留既有工作区、任务卡、共享资源、验收快照、重大回归、报告和项目经验约束，角色引用有效；OpenSpec 规划严格校验通过。
    verify：`Get-Content -Raw -Encoding UTF8 AGENTS.md,AGENTS_manager.md,AGENTS_rules.md,AGENTS_test.md; openspec validate agent-development-workflow --strict`
    预期：退出码 0 且输出 change valid；逐项对照基线，不丢失现有约束或引入矛盾。

允许开发修改四份 AGENTS 角色文件；不修改业务代码、脚本、技能文件或 OpenSpec 工件。开发回执列完整变化、实际自检、假设和回滚。测试保存逐项行号、命令输出和快照前后哈希。回滚恢复本阶段基线 ffa7d675cae00d3c079b35a00fae9034d07f4d5a 的四份规则，保留报告。
