# 2.4 第 1 轮开发过程记录

- 在已指定的任务分支及共享快照上实现；保留既有改动，未写 IssueLog、未提交。
- 先添加上传测试，初次 Maven 编译失败，原因是上传服务尚不存在；随后实现服务后测试通过。初次写测试与控制器时误使用相对于项目根的路径并在 cloud 目录执行，命令报告路径不存在；已在正确目录写入，未删除或覆盖其他文件。
- 指定命令 `mvn -q "-Dtest=AgentPersonalFileUploadTest,AgentPersonalFileStoreTest,AgentPersonalFileSegmentTest" test` 最终退出 0，日志为同目录 `developer-2-4-round1-maven.log`。Surefire：上传 6 通过，存储 12 中 11 通过 1 因 Windows 符号链接权限跳过，分段 5 通过。
- 测试覆盖 50 MiB 实际内容、三种模式、跨用户会话、登录检查、重复和越限分段、无效 UTF-8、不完整、断流、版本冲突、注入提交失败、取消和超时清理。尚未进行真实 HTTP 服务或系统峰值压测；独立验收仍需确认。
- 现有 Nginx 30m 请求限制足够发送 1 MiB 分段，未改动现有部署限制。单进程上传专用临时目录、闲置清理和回滚说明见 deploy/README.md。
