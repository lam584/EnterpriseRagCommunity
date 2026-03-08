package com.example.EnterpriseRagCommunity.service.ai;

import com.example.EnterpriseRagCommunity.entity.rag.QaMessagesEntity;
import com.example.EnterpriseRagCommunity.entity.rag.QaSessionsEntity;
import com.example.EnterpriseRagCommunity.entity.rag.QaTurnsEntity;
import com.example.EnterpriseRagCommunity.entity.rag.enums.MessageRole;
import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.entity.access.enums.AuditResult;
import com.example.EnterpriseRagCommunity.exception.ResourceNotFoundException;
import com.example.EnterpriseRagCommunity.repository.rag.QaMessagesRepository;
import com.example.EnterpriseRagCommunity.repository.rag.QaSessionsRepository;
import com.example.EnterpriseRagCommunity.repository.rag.QaTurnsRepository;
import com.example.EnterpriseRagCommunity.service.AdministratorService;
import com.example.EnterpriseRagCommunity.service.access.AuditDiffBuilder;
import com.example.EnterpriseRagCommunity.service.access.AuditLogWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class QaMessageService {

    private final QaSessionsRepository qaSessionsRepository;
    private final QaMessagesRepository qaMessagesRepository;
    private final QaTurnsRepository qaTurnsRepository;
    private final AuditLogWriter auditLogWriter;
    private final AuditDiffBuilder auditDiffBuilder;
    private final AdministratorService administratorService;

    @Transactional
    public void updateMyMessage(Long userId, Long messageId, String content) {
        if (userId == null) throw new IllegalArgumentException("userId is required");
        if (messageId == null) throw new IllegalArgumentException("messageId is required");
        if (content == null || content.isBlank()) throw new IllegalArgumentException("content is required");

        QaMessagesEntity msg = qaMessagesRepository.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("message not found"));
        Map<String, Object> before = summarizeMessageForAudit(msg);

        QaSessionsEntity session = qaSessionsRepository.findByIdAndUserId(msg.getSessionId(), userId)
                .orElseThrow(() -> new ResourceNotFoundException("session not found"));
        if (Boolean.FALSE.equals(session.getIsActive())) {
            throw new IllegalArgumentException("session inactive");
        }

        msg.setContent(content);
        if (msg.getRole() == MessageRole.USER) {
            msg.setTokensIn(null);
        }
        QaMessagesEntity saved = qaMessagesRepository.save(msg);
        auditLogWriter.write(
                userId,
                resolveActorNameOrNull(userId),
                "QA_MESSAGE_UPDATE",
                "QA_MESSAGE",
                messageId,
                AuditResult.SUCCESS,
                "更新对话消息",
                null,
                auditDiffBuilder.build(before, summarizeMessageForAudit(saved))
        );
    }

    @Transactional
    public void deleteMyMessage(Long userId, Long messageId) {
        if (userId == null) throw new IllegalArgumentException("userId is required");
        if (messageId == null) throw new IllegalArgumentException("messageId is required");

        QaMessagesEntity msg = qaMessagesRepository.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("message not found"));
        Map<String, Object> before = summarizeMessageForAudit(msg);

        QaSessionsEntity session = qaSessionsRepository.findByIdAndUserId(msg.getSessionId(), userId)
                .orElseThrow(() -> new ResourceNotFoundException("session not found"));
        if (Boolean.FALSE.equals(session.getIsActive())) {
            throw new IllegalArgumentException("session inactive");
        }

        if (msg.getRole() == MessageRole.USER) {
            Optional<QaTurnsEntity> t = qaTurnsRepository.findByQuestionMessageId(msg.getId());
            if (t.isPresent()) {
                QaTurnsEntity turn = t.get();
                Long answerId = turn.getAnswerMessageId();
                qaTurnsRepository.delete(turn);
                if (answerId != null) {
                    qaMessagesRepository.deleteById(answerId);
                }
                before.put("deletedAnswerMessageId", answerId);
            }
            qaMessagesRepository.delete(msg);
            auditLogWriter.write(
                    userId,
                    resolveActorNameOrNull(userId),
                    "QA_MESSAGE_DELETE",
                    "QA_MESSAGE",
                    messageId,
                    AuditResult.SUCCESS,
                    "删除对话消息",
                    null,
                    before
            );
            return;
        }

        if (msg.getRole() == MessageRole.ASSISTANT) {
            Optional<QaTurnsEntity> t = qaTurnsRepository.findByAnswerMessageId(msg.getId());
            if (t.isPresent()) {
                QaTurnsEntity turn = t.get();
                turn.setAnswerMessageId(null);
                qaTurnsRepository.save(turn);
            }
            qaMessagesRepository.delete(msg);
            auditLogWriter.write(
                    userId,
                    resolveActorNameOrNull(userId),
                    "QA_MESSAGE_DELETE",
                    "QA_MESSAGE",
                    messageId,
                    AuditResult.SUCCESS,
                    "删除对话消息",
                    null,
                    before
            );
        }
    }

    @Transactional
    public boolean toggleMyMessageFavorite(Long userId, Long messageId) {
        if (userId == null) throw new IllegalArgumentException("userId is required");
        if (messageId == null) throw new IllegalArgumentException("messageId is required");

        QaMessagesEntity msg = qaMessagesRepository.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("message not found"));
        Map<String, Object> before = summarizeMessageForAudit(msg);

        QaSessionsEntity session = qaSessionsRepository.findByIdAndUserId(msg.getSessionId(), userId)
                .orElseThrow(() -> new ResourceNotFoundException("session not found"));

        msg.setIsFavorite(!Boolean.TRUE.equals(msg.getIsFavorite()));
        qaMessagesRepository.save(msg);
        auditLogWriter.write(
                userId,
                resolveActorNameOrNull(userId),
                "QA_MESSAGE_FAVORITE_TOGGLE",
                "QA_MESSAGE",
                messageId,
                AuditResult.SUCCESS,
                "收藏/取消收藏对话消息",
                null,
                auditDiffBuilder.build(before, summarizeMessageForAudit(msg))
        );
        return msg.getIsFavorite();
    }

    private String resolveActorNameOrNull(Long userId) {
        if (userId == null) return null;
        try {
            return administratorService.findById(userId).map(UsersEntity::getEmail).orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    private static Map<String, Object> summarizeMessageForAudit(QaMessagesEntity m) {
        Map<String, Object> out = new HashMap<>();
        if (m == null) return out;
        out.put("id", m.getId());
        out.put("sessionId", m.getSessionId());
        out.put("role", m.getRole() == null ? null : m.getRole().name());
        out.put("contentLen", m.getContent() == null ? 0 : m.getContent().length());
        out.put("model", m.getModel());
        out.put("tokensIn", m.getTokensIn());
        out.put("tokensOut", m.getTokensOut());
        out.put("isFavorite", m.getIsFavorite());
        return out;
    }
}
