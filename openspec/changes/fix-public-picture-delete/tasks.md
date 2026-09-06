# Tasks: Fix Public Picture Delete

> **执行规则**：本变更只修后端 `deletePicture` 的额度判空缺陷。不碰前端、不碰缓存问题、不做对象存储清理。
> **分支**：实施时从最新 `main` 切出 `fix/public-picture-delete修复`。
> **回归数据**：公共图库残留测试图 `2096509129564987394`、`2096509302319980545`；admin 自有空间 `1987063349924573185`。

## 0. 基线与分支

- [ ] 0.1 从最新 `main` 创建分支 `fix/public-picture-delete修复`；`git status --short --branch` 确认分支正确且无无关暂存文件
- [ ] 0.2 启动后端（local profile），复现基线：`POST /api/picture/delete {"id":"2096509129564987394"}` 返回 `50001 额度更新失败`，将请求/响应存档供 IssueLog 记录

## 1. 修复实现

- [ ] 1.1 修改 `PictureServiceImpl.deletePicture`：仅当 `oldPicture.getSpaceId() != null` 时执行空间额度更新，公共图库图片跳过该分支；`mvn -DskipTests package` 编译通过
- [ ] 1.2 全仓检索额度更新点，确认无其他遗漏的 `spaceId` 未判空调用（上传路径已有守卫），检索结果记录到本任务

## 2. 回归验证

- [ ] 2.1 公共图库路径：删除 2 张残留测试图，均返回 code 0；按 id 再查返回数据不存在；公共图库列表 total 相应减少
- [ ] 2.2 空间额度路径：删除前记录空间 `1987063349924573185` 的 totalSize/totalCount → 空间内上传 1 张图 → 断言额度增加 → 删除该图 → 断言 totalSize/totalCount 回落到删除前水平
- [ ] 2.3 权限回归：非成员账号对他人空间图片删除仍被拦截（40101/40100），公共图库他人图片非管理员删除仍被拦截

## 3. 记录与汇报

- [ ] 3.1 将基线报错、修复方案、回归结果写入 `IssueLog.xlsx`（更新原暂缓条目状态为已修复）
- [ ] 3.2 汇报测试结果与问题清单，等待用户确认；未经确认不合并 `main`
