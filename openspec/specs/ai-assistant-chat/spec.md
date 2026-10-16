# ai-assistant-chat Specification

## Purpose
Gives logged-in users a visible "AI 助手" entry and a chat panel that answers policy questions over the RAG pipeline (capability `wiki-rag-pipeline`) with streamed answers, permission-filtered scope selection, and clickable citations whose document numbers come from chunk metadata (never trusted from LLM output).

## Requirements

### Requirement: AI assistant navigation entry

The system SHALL show an "AI 助手" entry in the top navigation for logged-in users, linking to the assistant chat page.

#### Scenario: Logged-in user sees the entry
- **WHEN** a logged-in user browses any page
- **THEN** the top navigation shows the "AI 助手" menu item
- **AND** clicking it navigates to `/aiAssistant`

#### Scenario: Anonymous user does not see the entry
- **WHEN** an anonymous user browses pages
- **THEN** the top navigation does not show the "AI 助手" menu item

### Requirement: Retrieval scope selection

The panel SHALL offer a multi-select of the user's visible spaces plus an "all authorized spaces" shortcut, and the effective retrieval scope SHALL be the intersection of the selection and the user's visible spaces.

#### Scenario: Multiple spaces selected
- **WHEN** the user selects two spaces and asks a question
- **THEN** the backend retrieves only within (selected ∩ visible) spaces
- **AND** the SSE meta event reports that effective set and the authorized document count within it

#### Scenario: Unauthorized spaces are not selectable
- **WHEN** the user opens the scope selector
- **THEN** only visible spaces (personal + joined team + public) appear as options

#### Scenario: All-authorized shortcut
- **WHEN** the user checks the "全部授权空间" shortcut
- **THEN** the effective scope equals all of the user's visible spaces

### Requirement: Streamed answering

Answers SHALL be rendered incrementally, with the model's reasoning output displayed separately from the answer body.

#### Scenario: Streaming render
- **WHEN** an answer is being generated
- **THEN** the answer body renders progressively per delta event
- **AND** reasoning output (if any) is shown in a collapsed grey block

#### Scenario: Out-of-corpus question is refused
- **WHEN** retrieval returns zero hits in the effective scope
- **THEN** the system does not call the LLM and returns an explicit "no supporting evidence found" reply
- **AND** no fabricated document number appears

#### Scenario: LLM not configured
- **WHEN** RAG_LLM_API_KEY is not configured and the user asks a question
- **THEN** a readable error is delivered via the SSE error event
- **AND** other features are unaffected

### Requirement: Citation rendering and jump

Citations SHALL be rendered from retrieval-hit chunk metadata; the LLM only outputs `[n]` markers.

#### Scenario: Citation click navigates to the document
- **WHEN** the user clicks a `[n]` marker or a citation entry
- **THEN** the app navigates to the document detail page (`/documentWiki/{docId}`)

#### Scenario: Citation data comes from metadata
- **WHEN** an answer completes
- **THEN** the citation list shows each hit's document title and document number taken from chunk metadata (docNumber)
- **AND** document numbers inside the LLM text are never used as the citation source

### Requirement: 默认展示 Agent 对话
AI 助手页面 SHALL 默认展示 Agent 对话和历史会话，且不展示进入经典 RAG 问答模式的切换入口；Agent 引用知识库的能力 SHALL 保持可用。

#### Scenario: 首次打开页面
- **WHEN** 登录用户打开 AI 助手页面
- **THEN** 页面展示 Agent 历史列表、新聊天和引用知识库开关，并加载该用户的历史会话；页面不展示经典 RAG 模式开关或其专属深度思考开关

#### Scenario: 使用 Agent 引用知识库
- **WHEN** 用户在 Agent 页面开启引用知识库并选择授权空间或文件夹后提问
- **THEN** 请求仍按 Agent 的 RAG 范围发送，回答继续支持引用预览

### Requirement: Agent 逐轮选择 RAG 范围
Agent 页面 SHALL 允许用户逐轮关闭检索、使用全部授权空间或指定一个以上可见空间或文件夹；每次检索 SHALL 以请求时的实际空间权限为准。

#### Scenario: 关闭 RAG
- **WHEN** 用户关闭本轮 RAG 后提问
- **THEN** 系统不检索知识库，回答不展示文档引用

#### Scenario: 指定空间
- **WHEN** 用户选择一个或多个可见空间后提问
- **THEN** 系统仅在这些空间与当前授权空间的交集内检索

#### Scenario: 按分类勾选空间
- **WHEN** 用户展开公共、团队或个人分类，并勾选分类或其中某个子空间
- **THEN** 分类勾选选择该分类全部可见子空间，子空间勾选只选择该子空间；部分勾选的分类显示半选，发送请求使用原始精确空间 ID

#### Scenario: 连续选择多个子空间
- **WHEN** 用户在分类弹层中连续勾选多个子空间
- **THEN** 每次勾选后弹层保持展开，选中状态累积；弹层右边缘与展开按钮右边缘对齐，点击弹层外部后关闭

#### Scenario: 文件夹树与精确检索
- **WHEN** 用户在公共、团队或个人分类中勾选一个 WIKI 文件夹并提问
- **THEN** 分类展示与 WIKI 相同的空间和多级文件夹，检索只使用该文件夹及其后代中的有效文档，不包含同空间其他文件夹或根目录文档

#### Scenario: 空间与文件夹混选
- **WHEN** 用户同时勾选空间与其他空间中的文件夹
- **THEN** 检索范围为整个已选空间与已选文件夹子树的并集，引用和授权文档数均限于该并集

#### Scenario: 文件夹失权或删除
- **WHEN** 用户提交已删除或当前无权访问的文件夹 ID
- **THEN** 请求被拒绝或该文件夹无候选文档，绝不扩大为其所属整个空间或全部空间

#### Scenario: 从全部范围切到指定范围
- **WHEN** 用户在“全部授权空间”模式下直接勾选分类或子空间
- **THEN** 页面切换为指定范围且只勾选本次点选的分类或子空间，不隐式保留其他空间

#### Scenario: 空选择与越权选择
- **WHEN** 用户提交空的指定空间和文件夹集合，或只提交无权访问的空间与文件夹
- **THEN** 系统不得退化为检索全部授权空间

### Requirement: Agent 历史引用
Agent SHALL 将每条回答的引用关联到该条消息；点击引用 SHALL 再次检查当前文档权限。

#### Scenario: 历史引用正确归属
- **WHEN** 用户打开含多轮 RAG 回答的历史会话
- **THEN** 每轮显示其自身检索得到的引用，点击某轮标记不会跳到另一轮的引用

#### Scenario: 空检索结果
- **WHEN** 开启 RAG 但指定范围内无支持证据
- **THEN** 系统不给出伪造引用，并明确告知未找到依据

#### Scenario: 引用来源失权
- **WHEN** 用户打开旧会话中来自已失权空间的引用
- **THEN** 文档入口拒绝打开，旧会话文本仍可由会话所有者查看
