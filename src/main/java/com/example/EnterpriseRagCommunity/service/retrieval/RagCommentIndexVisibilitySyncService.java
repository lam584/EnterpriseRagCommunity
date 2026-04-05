package com.example.EnterpriseRagCommunity.service.retrieval;

import com.example.EnterpriseRagCommunity.entity.semantic.VectorIndicesEntity;
import com.example.EnterpriseRagCommunity.repository.semantic.VectorIndicesRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
@Service
@RequiredArgsConstructor
public class RagCommentIndexVisibilitySyncService {

    private static final Logger log = LoggerFactory.getLogger(RagCommentIndexVisibilitySyncService.class);

    private final VectorIndicesRepository vectorIndicesRepository;
    private final RagCommentIndexBuildService buildService;

    public void scheduleSyncAfterCommit(Long commentId) {
        if (commentId == null) return;
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    doSync(commentId);
                }
            });
            return;
        }
        doSync(commentId);
    }

    private void doSync(Long commentId) {
        List<VectorIndicesEntity> indices = vectorIndicesRepository.findAll();
        for (VectorIndicesEntity vi : indices) {
            if (!RagValueSupport.matchesSourceType(vi, "COMMENT")) continue;
            try {
                buildService.syncSingleComment(vi.getId(), commentId);
            } catch (Exception ex) {
                log.warn("RAG comment visibility sync failed. vectorIndexId={}, commentId={}, err={}", vi.getId(), commentId, ex.getMessage(), ex);
            }
        }
    }
}
