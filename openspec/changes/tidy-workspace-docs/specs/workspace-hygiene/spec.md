# workspace-hygiene Delta

## ADDED Requirements

### Requirement: 仓库文档必须分代且可分辨

仓库根目录 MUST 只保留**现役**文档。已停止维护的文档 MUST 迁入 `docs/` 下的归档目录，且归档目录 MUST 在命名上标明其历史属性（`legacy-notes` 表示跨项目的旧学习笔记，`history` 表示被取代的台账与图表）。任何读者 MUST 能在不阅读文件内容的前提下，仅凭路径分辨现役与历史文档。

#### Scenario: 访客查看根目录

- **WHEN** 访客打开仓库根目录
- **THEN** 只看到现役文档（`README.md`、`AGENTS.md`、`IssueLog.xlsx` 及项目脚本），不出现跨项目的旧笔记与被取代的旧台账

#### Scenario: 需要查阅历史文档

- **WHEN** 需要查阅旧项目学习笔记或被取代的台账
- **THEN** 可在 `docs/legacy-notes/` 与 `docs/history/` 下按其原本组织形式找到，且文档间引用仍然有效

### Requirement: 历史文档迁移不得产生断链

迁移 markdown 文档及其引用的图片资源时，MUST 保持被引用资源与引用者之间的**相对路径关系不变**；由同一份 markdown 直接引用的图片目录 MUST 与该 markdown 一同迁移。迁移完成后 MUST 逐条验证引用可解析。

#### Scenario: 迁移互相引用的 md 与图片目录

- **WHEN** 把一个 markdown 及其同级图片目录整体迁入新目录
- **THEN** markdown 中原有的相对图片引用（如 `README/image-xxx.png`）在新位置依然可解析，不产生任何死链

#### Scenario: 迁移后验证

- **WHEN** 迁移完成
- **THEN** 对每个被迁移的 markdown 抽取其全部相对图片引用并逐一检查目标文件存在，缺失项数为 0

### Requirement: 清理作业必须使用白名单与可恢复删除

删除文件时 MUST 逐项列明待删路径，MUST NOT 使用通配符或目录级批量删除表述。删除 MUST 送入系统回收站，MUST NOT 使用 `rm` / `git rm` / `del /S`。每批删除 MUST 不超过 10 项，且 MUST 在每批完成后立即复核结果；出现任一项失败时 MUST 停止后续批次。

#### Scenario: 执行一批删除

- **WHEN** 执行一批待删文件
- **THEN** 该批不超过 10 项，全部进入回收站（可通过回收站还原），且该批完成后立即列出删除结果供核对

#### Scenario: 符号链接与运行中文件

- **WHEN** 待删项中出现符号链接，或被运行中的服务持续写入的文件
- **THEN** 符号链接 MUST NOT 被删除；被占用的文件 MUST 在停止相关服务后再处理

### Requirement: 被文档引用的文件不得删除

在清理前 MUST 对仓库内文档（`openspec/`、`docs/`、`.workbuddy/memory/`）做路径引用扫描，产出被引用文件清单。被引用文件 MUST NOT 被删除，除非引用语境表明被引用的只是"该路径可查看此类信息"（路径指引）而非"该文件承载某项结论"（证据底稿）。

#### Scenario: 判别证据底稿与路径指引

- **WHEN** 某个 `tmp/` 文件被文档引用
- **THEN** 若引用语境是支撑某项已发布指标（如"底稿""逐题落盘""产出"），该文件 MUST 保留；若引用语境仅为"出问题时查看该日志"，则可删，但 MUST 在清理记录中说明判别理由

#### Scenario: 扫描清单作为保留名单

- **WHEN** 执行删除
- **THEN** 待删清单 MUST 与扫描得到的被引用清单做过二次比对，交集 MUST 为空

### Requirement: 无版本保护的资产必须登记成文

位于 `.gitignore` 覆盖范围内、且属于**唯一副本或不可自动重建**的文件与目录，MUST 在仓库内文档中登记，登记内容 MUST 包含：路径、体积、用途、以及"重建方式或唯一性说明"。登记 MUST 使得第三人无需询问即可判断该资产丢失的后果。

#### Scenario: 新增长期驻留的忽略文件

- **WHEN** 有新的资产被放入 `.gitignore` 覆盖范围并需长期驻留
- **THEN** 该资产 MUST 被登记到资产清单文档，注明其唯一性与重建方式

#### Scenario: 核对登记完整性

- **WHEN** 检查资产清单
- **THEN** 清单覆盖的资产包含但不限于：英文语料与其标准答案、答案层评测脚本归档、数据回滚 SQL、沙箱启动辅助文件
