package com.et.cloud.model.dto;

import lombok.Data;

/**
 * Outcome of a single url or file inside a batch import submission.
 *
 * Every submitted item gets its own result, so successful imports stay visible when other items
 * fail.
 */
@Data
public class BatchImportItemResult {

    public static final String STATUS_SUCCESS = "SUCCESS";

    public static final String STATUS_FAILED = "FAILED";

    /**
     * What the user submitted: the url, or the uploaded file name.
     */
    private String input;

    /**
     * {@code SUCCESS} or {@code FAILED}.
     */
    private String status;

    /**
     * Actionable message for the item, always filled for failures.
     */
    private String message;

    /**
     * Created document id, present only when the item succeeded.
     */
    private Long documentId;

    /**
     * Created document title, present only when the item succeeded.
     */
    private String title;

    public static BatchImportItemResult success(String input, Long documentId, String title) {
        BatchImportItemResult result = new BatchImportItemResult();
        result.setInput(input);
        result.setStatus(STATUS_SUCCESS);
        result.setMessage("导入成功");
        result.setDocumentId(documentId);
        result.setTitle(title);
        return result;
    }

    public static BatchImportItemResult failed(String input, String message) {
        BatchImportItemResult result = new BatchImportItemResult();
        result.setInput(input);
        result.setStatus(STATUS_FAILED);
        result.setMessage(message == null || message.isBlank() ? "导入失败" : message);
        result.setDocumentId(null);
        result.setTitle(null);
        return result;
    }
}
