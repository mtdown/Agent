# P1-T1 第 1 轮独立测试

Change openspec-local-workflow；阶段 P1；任务 P1-T1；实例 local_propose_test1；2026-10-29 14:07 +08:00。总体结论 PASS。

基线 1ffd9f08982a600719ea484eb7052fe3f3a85a3c；六个冻结文件测试前后哈希均与任务卡一致。新技能差异 SHA256 e49542180914e2056c654804782bc4f95542ae29fd1a39bc9b02575482b18dac。

- A1 PASS：读取 local 技能成功；名称及目录匹配，已有 change 和停止条件见 SKILL.md:2、8，无创建 change 指令。
- A2 PASS：status、依赖顺序、instructions、工件存在性及严格校验见 SKILL.md:18–23；完整任务与纯规划授权交接见第 27 行。
- A3 PASS：原技能 git diff 退出码 0；技能结构校验退出码 0，输出 Skill is valid；change 严格校验退出码 0，输出 change valid。

无固定验收缺陷、阻断问题或未验证项。未修改被测文件，未启停服务。测试原始输出和前后哈希归档于 evidence-p1-t1-round1.txt。

独立只读行为场景复核：对已创建且已授权完整开发的 change，local 技能使用现有 change、不重复创建；规划就绪后交接总管派发任务，不重复请求相同授权；未解决范围决策仍上报。原版场景下会要求创建或询问同名 change，且规划后要求等待新请求。
