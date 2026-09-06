# Spec: KnowledgeItem

## ADDED Requirements

### REQ-1: KnowledgeItem creation

**Scenario**: a logged-in user creates a new knowledge item with mixed text + image blocks
- GIVEN the user has `EDIT` permission on a `WikiSpace`
- WHEN the user calls `POST /knowledgeItem/add` with `{ title, blocks: [{type:TEXT, content:"..."}, {type:IMAGE, refPictureId:123}] }`
- THEN the system MUST persist the item and blocks within a single transaction
- AND the item's `type` MUST be derived as `MIXED` from the block collection

### REQ-2: KnowledgeItem retrieval (mixed sources)

**Scenario**: a user opens the knowledge list
- WHEN the user calls `POST /knowledgeItem/list/page/vis`
- THEN the response MUST include both items created natively (source=NATIVE) and items promoted from old DocumentWiki (source=DOC_WIKI_ADAPT)
- AND items MUST be ordered by `editTime DESC` within their space

### REQ-3: BlockRenderer dispatch

**Scenario**: a user views a knowledge detail
- WHEN the backend serializes blocks to the AST
- THEN each block MUST be rendered by the strategy matching its `blockType`
- AND unknown `blockType` MUST be rendered as a non-blocking empty block with a server log warning

### REQ-4: Block ordering and editing

**Scenario**: a user reorders blocks
- WHEN the user calls `POST /knowledgeItem/edit` with the new block order
- THEN the backend MUST persist the new `sort_order` values atomically
- AND MUST invalidate the item cache key

### REQ-5: Picture promotion

**Scenario**: a user promotes a standalone picture into a knowledge item
- WHEN the user calls `POST /picture/promote` with `{ pictureId }`
- THEN a new `KnowledgeItem` MUST be created with `type=PICTURE` and one IMAGE block referencing that picture
- AND the original picture row remains untouched

### REQ-6: Permission inheritance

**Scenario**: any knowledge item read/write
- WHEN a user accesses a knowledge item
- THEN the system MUST reuse the existing `WikiSpace` visibility rules (`wikiSpaceService.requireVisibleSpace`)
- AND MUST NOT introduce a new permission table

### REQ-7: Layout unification

**Scenario**: a user navigates between knowledge list and detail
- WHEN the user opens the list page
- THEN cards MUST be the only item representation (no table-style rows)
- WHEN the user opens a detail page
- THEN the layout MUST be a 3-column grid (TOC / body / actions)