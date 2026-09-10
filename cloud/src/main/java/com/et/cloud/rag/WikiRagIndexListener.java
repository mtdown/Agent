package com.et.cloud.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import javax.annotation.Resource;

/**
 * Consumes document lifecycle events and drives the RAG index, strictly AFTER
 * the originating transaction committed (or immediately when no transaction
 * is active). Runs on the dedicated ragIndexExecutor.
 */
@Component
@Slf4j
public class WikiRagIndexListener {

    @Resource
    private WikiRagIndexService wikiRagIndexService;

    @Async("ragIndexExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onDocumentChanged(WikiDocumentChangedEvent event) {
        log.debug("rag index event: {} doc={} space={} target={} version={}",
                event.getChangeType(), event.getDocId(), event.getSpaceId(),
                event.getTargetSpaceId(), event.getContentVersion());
        switch (event.getChangeType()) {
            case DOC_CREATED:
            case DOC_UPDATED:
                wikiRagIndexService.indexDocument(event.getDocId());
                break;
            case DOC_LOGICAL_DELETED:
                wikiRagIndexService.invalidateDocument(event.getDocId());
                break;
            case DOC_RESTORED:
                wikiRagIndexService.reactivateDocument(event.getDocId(), event.getContentVersion());
                break;
            case DOC_PERMANENT_DELETED:
                wikiRagIndexService.deleteDocumentChunks(event.getDocId(), event.getSpaceId());
                break;
            case DOC_MOVED:
                wikiRagIndexService.moveDocumentChunks(event.getDocId(), event.getSpaceId(), event.getTargetSpaceId());
                break;
            case SPACE_LOGICAL_DELETED:
                wikiRagIndexService.invalidateSpace(event.getSpaceId());
                break;
            case SPACE_RESTORED:
                wikiRagIndexService.restoreSpace(event.getSpaceId());
                break;
            case SPACE_PERMANENT_DELETED:
                wikiRagIndexService.deleteSpaceChunks(event.getSpaceId());
                break;
            default:
                log.warn("unhandled rag event type: {}", event.getChangeType());
        }
    }

    /**
     * Daily reconciliation safety net: catches orphans from edge windows
     * (folder-level deletes, rejected executor tasks, races).
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void reconcileDaily() {
        int fixed = wikiRagIndexService.reconcileOrphans();
        log.info("rag daily reconciliation done, orphans fixed: {}", fixed);
    }
}
