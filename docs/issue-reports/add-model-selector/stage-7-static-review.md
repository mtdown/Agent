# 阶段 7 静态补充证据索引

测试实例 /root/tester；2026-11-04；分支 feature/model-selector开发，基线 f5ecb69bc2ccc292c44fa7ea1ee372abaa34f612；原快照清单及补丁见 stage-7-round-1.md。本次仅静态检查，未启动服务、发送请求、执行端点或接触密钥；原 Issue 33 不变。具体代码与规范对照见 [双栏证据](stage-7-static-review.html)。

- Issue 35：localEnvPlugin.ts:78–82 对 loopback、指定头且缺失 Origin 的输入，空字符串使条件不触发而返回真；spec.md:49–51 要求 Origin 为 localhost 地址。start-dev.ps1:349、391–394 显示默认可启动通向本机端口的公共隧道；能否由远端实际到达此端点，以及中间代理如何处理 Origin、头部和源地址，均未动态验证，远程风险仅为推断。
- Issue 34：localEnvPlugin.ts:47–54 使用 map 替换每个匹配行，未去重；若原有两行假想 `KEY=old`，依该映射两行均成为 `KEY=new`，违反 spec.md:44–47 写入键恰好出现一次的要求。示例仅用于说明逻辑，未对任何文件或端点执行。

两项作为清单外交付阻断风险交总管仲裁，固定验收结果不改变。本次未进行动态复现或基线比较；静态代码及规范证据见双栏文件。
