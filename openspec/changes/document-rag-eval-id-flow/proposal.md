# Document RAG evaluation ID flow

## Why

The relationship among a source article, its policy document, generated evaluation questions, and gold chunks is difficult to understand from abstract descriptions. A traceable real example will make the first-version RAG evaluation data flow clear.

## Scope

Document one existing pair from `golden.v1.jsonl`, including its real metadata IDs, document IDs, chunk IDs, pair ID, question IDs, and gold coordinates. Explain the two document-to-document and question-to-evidence matching stages.

## Acceptance criteria

- The document follows the requested headings: `【文章】`, `【第一次拆分】`, `【第一次匹配】`, `【第二次拆分】`, `【第二次匹配】`.
- Every shown identifier is checked against repository artifacts.
- The example distinguishes stable `docId + chunkIndex` gold coordinates from database `chunkId` values.
- The explanation states which document supplies the question and which document is the retrieval target.

## Non-goals

- Change evaluation data, scripts, or application code.
- Claim that automatically generated v2 questions received full manual review.
