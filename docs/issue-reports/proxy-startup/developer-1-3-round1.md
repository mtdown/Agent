# proxy-startup 开发 1.3 第 1 轮

状态：READY_FOR_TEST，待独立验收。按照新共同规则，代理测试不删除临时目录，仅报告本轮创建的精确路径和管理员验收后的清理命令；已核验归属的容器及网络仍按 ID 清理。创建目录不成功时不报告为已创建。Windows 输出 PowerShell `Remove-Item -LiteralPath`，其他平台输出带引用的 POSIX shell `rm -rf --`；单引号转义有受控测试。未修改部署 workflow、生产配置、依赖或用户数据。

## 自检

- 先添加路径和清理命令的受控断言，空实现退出 1；实施后 `node --test deployProxyLifecycle.test.mjs` 退出 0，23 项通过，包括成功、失败、未创建及 Windows/POSIX 路径引用。
- `node deployProxy.test.mjs` 退出 0，真实本机 nginx，后端地址变化、代理不重启及路径断言通过。本轮目录 `C:\Users\origin\AppData\Local\Temp\cw-proxy-test-38492-0z1ymx` 通过 `Test-Path -PathType Container` 核实仍存在；按对应标签查询容器和网络为空。
- 本机通过既有只读 fake docker 可执行文件对完整 CLI 注入创建失败：脚本退出 1，并报告实际保留目录和清理命令；`C:\Users\origin\AppData\Local\Temp\cw-proxy-test-62260-wn0fxg` 经 `Test-Path` 核实仍存在。该场景没有实际创建 Docker 容器或网络。没有删除或修改该 fake 所在的既有临时目录。
- `git diff --check` 退出 0；`git diff -- .github/workflows/deploy.yml` 输出为空。未执行部署或 CI。

## 待管理员验收后清理

以下目录均为本次测试创建、当前保留；命令仅供管理员审核后手动执行，本开发者没有执行：

```powershell
Remove-Item -LiteralPath 'C:\Users\origin\AppData\Local\Temp\cw-proxy-test-38492-0z1ymx' -Recurse -Force
Remove-Item -LiteralPath 'C:\Users\origin\AppData\Local\Temp\cw-proxy-test-62260-wn0fxg' -Recurse -Force
```

本轮未登记新问题；此前 #15–#19 保留各自已有状态。自检不代表独立验收，CI 当次容器退出根因仍未知。回滚本任务对测试脚本和生命周期测试的改动即可，不修改服务、数据或现存临时目录。
