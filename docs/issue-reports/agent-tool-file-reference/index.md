# agent-tool-file-reference 问题索引

- [ATFR-P1-T1.1-R1-001：写入结果未知后可再次执行写工具](issue-p1-t1-1-round1-retry.md)（第 1 轮，待处理；[代码与验收对照](issue-p1-t1-1-round1-retry.html)）- [ATFR-P1-T1.1-R2-001：写请求响应断开或损坏被报告为失败并允许再次写入](issue-p1-t1-1-round2-transport.md)（第 2 轮，待处理；[代码与验收对照](issue-p1-t1-1-round2-transport.html)）
- [ATFR-P1-T1.1-R2-002：成功文档与后续未知结果同时进入用户可见投影](issue-p1-t1-1-round2-projection.md)（第 2 轮，待处理；[代码与验收对照](issue-p1-t1-1-round2-projection.html)）

- [ATFR-P1-T1.1-R3-001：多个 Wiki 结果的投影位置和覆盖](issue-p1-t1-1-round3-projection.md)（第 3 轮，待处理；[输入和结果对照](issue-p1-t1-1-round3-projection.html)）
- [非正式只读审查：工具结果与历史风险](issue-related-readonly-audit.md)（[对照](issue-related-readonly-audit.html)）
- [ATFR-P1-T1.1-R4-001 / 002：文件夹提示与多次失败明细](issue-p1-t1-1-round4-supplement.md)（第 4 轮补证，待处理；[输入和结果对照](issue-p1-t1-1-round4-supplement.html)）

- [ATFR-AUDIT-001 第5轮受控复现及 ATFR-P1-T1.1-R5-C1 证据不足](issue-p1-t1-1-round5-evidence.md)（[输入与验收对照](issue-p1-t1-1-round5-evidence.html)）

- [ATFR-P1-T1.1-R6-001 / R6-S1：自定义 Skill 兼容性及安全检查证据不足](issue-p1-t1-1-round6-skill.md)（第 6 轮补证；[输入与观察对照](issue-p1-t1-1-round6-skill.html)）

- [第7轮独立验收及问题复核](issue-p1-t1-1-round7-acceptance.md)（逐项状态与证据）

- [ATFR-AUDIT-003 第7轮后受控补证](issue-p1-t1-1-round7-audit003-supplement.md)（暂缓，负责人于 2026-10-30 决定；[代码与结果对照](issue-p1-t1-1-round7-audit003-supplement.html)）

- [ATFR-P1-T1.2-R1-001 / 002 / S1 / S2：第1轮独立验收](issue-p1-t1-2-round1.md)（[输入与观察对照](issue-p1-t1-2-round1.html)）

- [ATFR-P1-T1.2-R2-001 / 002：第2轮 Skill 数值兼容及精度问题](issue-p1-t1-2-round2.md)（[输入与观察对照](issue-p1-t1-2-round2.html)）

- [1.2 第3轮独立验收及问题复核](issue-p1-t1-2-round3.md)（逐项状态与证据）

- [1.3 第1轮独立验收及 ATFR-AUDIT-004 静态风险](issue-p1-t1-3-round1.md)（A1/A2/A3 PASS；风险待评估，[代码对照](issue-p1-t1-3-round1-audit.html)）

- [ATFR-P2-T2.1-R1-001：Windows junction 逃逸与第1轮验收](issue-p2-t2-1-round1.md)（A2 FAIL；[输入与观察对照](issue-p2-t2-1-round1.html)）

- [2.1 第2轮独立验收及符号链接补证](issue-p2-t2-1-round2.md)（junction 已验证修复；Windows 符号链接权限限制与 Linux 补证）

- [2.2 第1轮独立验收及 ATFR-P2-T2.2-R1-IO 摘要 I/O 风险](issue-p2-t2-2-round1.md)（固定验收通过；资源优化暂缓；[代码对照](issue-p2-t2-2-round1-io.html)）

- [2.3 第1轮独立验收及符号链接补证](issue-p2-t2-3-round1.md)（原 A1/A2 PASS，A3 Windows 符号链接证据不足；Linux 补证；[错误码风险对照](issue-p2-t2-3-round1-auth.html)）

- [3.1 第1轮独立验收及失权补证](issue-p3-t3-1-round1.md)（A1/A3 PASS；A2 原证据不足，登出补证范围有限）

- [3.2 第1轮独立验收及补证](issue-p3-t3-2-round1.md)（原结论证据不足；补证范围及限制）

- [ATFR-P4-T4.1-R1-001 / 002：4.1 第1轮独立验收及附加回归失败](issue-p4-t4-1-round1.md)（第1轮 A1 PASS、A2/A3 证据不足；第2轮已验证，归因未确定；[输入与结果对照](issue-p4-t4-1-round1.html)）

- [4.1 第2轮独立验收](issue-p4-t4-1-round2.md)（A1/A2/A3/B1 PASS；两项测试夹具调整后复测通过，原失败来源未判定）

- [2.4 第1轮当前快照独立补证](issue-p2-t2-4-current.md)（A1/A2/A3 PASS；真实会话探针及替身边界）

- [3.1 第1轮当前快照独立补证](issue-p3-t3-1-current.md)（A1/A2/A3 PASS；会话撤销、RAG 共存及替身边界）

- [3.2 第1轮当前快照独立补证](issue-p3-t3-2-current.md)（A1/A2/A3 受控范围 PASS；真实登录、完整跨进程及整体压测未测）
