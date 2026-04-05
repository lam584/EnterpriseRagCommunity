package com.example.EnterpriseRagCommunity.service.retrieval;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.example.EnterpriseRagCommunity.entity.semantic.VectorIndicesEntity;
import com.example.EnterpriseRagCommunity.repository.semantic.VectorIndicesRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RagPostIndexVisibilitySyncService {

    private static final Logger log = LoggerFactory.getLogger(RagPostIndexVisibilitySyncService.class);

    private final VectorIndicesRepository vectorIndicesRepository;
    private final RagPostIndexBuildService buildService;

    public void scheduleSyncAfterCommit(Long postId) {
        if (postId == null) return;
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    doSync(postId);
                }
            });
            return;
        }
        doSync(postId);
    }

    private void doSync(Long postId) {
        List<VectorIndicesEntity> indices = vectorIndicesRepository.findAll();
        for (VectorIndicesEntity vi : indices) {
            if (!RagValueSupport.matchesSourceType(vi, "POST")) continue;
            try {
                buildService.syncSinglePost(vi.getId(), postId);
            } catch (Exception ex) {
                log.warn("RAG post visibility sync failed. vectorIndexId={}, postId={}, err={}", vi.getId(), postId, ex.getMessage(), ex);
            }
        }
    }
}
