# S1 角色分工调整

基线 03274a2。1.1 确定共同表，1.2 至 1.4 按同一份提案实施；验收绑定到本阶段快照。

## 1.1 更新共同选型表

范围：AGENTS.md

1.1 开发者更新 AGENTS.md 的角色分配段。对照 proposal.md 逐行核验四个角色、三个默认模型与强度组合（按角色计）及全部例外，确认总管选型通过启动会话生效，常规速度建议为 Standard。

## 1.2 更新显式派单参数

范围：AGENTS_manager.md

1.2 开发者更新 AGENTS_manager.md 的派单说明。用普通开发、批量替换、桌面验收和证据冲突复核四张示例卡推演，预期可分别选择 gpt-6.2-sol/medium、gpt-6-luna/low、gpt-6.1-terra/medium、gpt-6.2-astra/high。逐项核对显式创建参数及 fork_turns="none"；模拟所选组合不可用时，预期停止该派发并上报。

## 1.3 更新开发与独立验收说明

范围：AGENTS_rules.md、AGENTS_test.md

1.3 开发者在 AGENTS_rules.md、AGENTS_test.md 对应角色段引用共同规则。核验复杂并发开发可选 Sol High、普通独立验收默认 Sol Medium；修复后复测仍使用新的独立实例和冻结快照，复核只读且保留原始测试结论。

## 1.4 同步协作模型表并交叉核对

范围：README.md 及五份文档的交叉核对

1.4 开发者同步 README.md 的协作角色表。独立测试者对照五份文档检查旧模型未被列为现行默认值，核对角色表与派单示例一致，运行 git diff --check。记录文件、命令、匹配行和逐项结果，区分文档推演与实际模型调用。
