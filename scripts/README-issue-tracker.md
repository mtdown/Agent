# 本机问题日志

需要 Python 3（使用标准库，不需安装依赖）。从项目根目录执行；日常操作统一使用脚本，不直接修改 SQLite。输出为 UTF-8 JSON，失败时输出错误并返回非零退出码。

```powershell
python scripts/issue_tracker.py init
python scripts/issue_tracker.py add --change-id agent-tool-file-reference --task-id 1.1 --title "多文档结果错配" --description "复现步骤与现象" --impact "用户看到错误归属" --evidence-path "docs/issue-reports/example.md"
python scripts/issue_tracker.py list --status 待处理 --change-id agent-tool-file-reference
python scripts/issue_tracker.py show 1
python scripts/issue_tracker.py update 1 --status 待验证 --resolution "已修复并自检"
python scripts/issue_tracker.py update 1 --status 已关闭 --resolution "独立复测通过" --evidence-path "docs/issue-reports/example-retest.md"
```

默认数据库为项目根目录 `.local/issue-tracker/issues.db`，不纳入 Git。`init` 不覆盖已有文件，其他命令不会自动创建数据库。可在子命令前用 `--db <路径>` 指定其他数据库，例如测试用临时数据库。

新增时 change、任务、标题、描述、影响必填，状态初始为待处理；证据位置可选。更新可指定一个或多个字段，字段名与新增选项一致，另支持 `--status` 和 `--resolution`。状态可选待处理、待验证、已关闭、暂缓、证据不足。指定关闭状态时必须同时明确提供非空的本次 `--resolution` 与 `--evidence-path`，证据应指向本次复测或包含本次复测的索引，不能自动沿用旧失败证据。更新已关闭问题的其他字段不需重复填写；状态及其他字段在一个事务中更新。时间使用 UTC ISO 8601 格式。

更新为“待验证”或“暂缓”时，须同时使用 `--resolution` 填写本次自检结论或暂缓原因。记录人工接受证据不足并允许交付时，状态保持“证据不足”，在结论中写明人工决定及适用范围；脚本不能验证人工身份或批准是否真实。`resolution` 和 `evidence_path` 的更新会替换原字段值，需要保留多份证据时请填写索引或完整路径列表。

开发者发现需追踪的问题时用 `add`，修复自检后用 `update` 标记待验证；测试者按独立复测结果用 `update` 更新，复测通过时填写验证结论和证据。人工决定由总管或当前协作 Agent 根据明确指示记录，不得自行声称人工批准。

现有 `IssueLog.xlsx` 仅供历史查询，不迁移或写入。测试运行：`python -m unittest scripts.test_issue_tracker`。

## 备份与恢复

备份命令使用 SQLite 在线备份取得一致快照，可在数据库使用期间运行。发布前检查快照完整性和问题表必需字段；空库、缺表或缺必需字段不会生成目标。目标目录须事先存在，目标文件必须不存在，命令不会覆盖已有文件或源数据库。例如将备份保存到 Git 忽略的 `.local/issue-tracker/` 目录（请自行更换文件名）：

```powershell
python scripts/issue_tracker.py backup .local/issue-tracker/backup-20261031.db
```

`.local/issue-tracker/` 受 Git 忽略；`.local/` 下的其他位置及其他目录不一定受忽略，使用前请检查 `git check-ignore <路径>`。Git 忽略规则不影响已被追踪的文件。本机备份仍可能随磁盘损坏丢失，请另存到个人备份位置。

恢复前停止所有访问目标数据库的脚本及程序。先将现有数据库及可能存在的 `-wal`、`-shm` 文件妥善备份到其他位置，确保恢复目标路径不存在且不存在旧的同名 `-wal`、`-shm` 文件；不要在有连接时移动这些文件。然后用备份文件作为源，以原数据库路径为新目标运行：

```powershell
python scripts/issue_tracker.py --db .local/issue-tracker/backup-20261031.db backup .local/issue-tracker/issues.db
python scripts/issue_tracker.py list
```

恢复命令同样拒绝覆盖已有目标。确认恢复内容正确后再重新允许其他程序访问；如果失败，保留原数据库和备份，先排查错误。
