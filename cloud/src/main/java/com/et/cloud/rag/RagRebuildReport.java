package com.et.cloud.rag;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Report of an admin-triggered backfill run.
 */
@Data
public class RagRebuildReport {

    private int total;

    private int created;

    private int skipped;

    private List<String> failed = new ArrayList<>();
}
