package com.example.EnterpriseRagCommunity.service.ai;

import com.example.EnterpriseRagCommunity.entity.rag.QaMessagesEntity;
import com.example.EnterpriseRagCommunity.entity.rag.QaSessionsEntity;
import com.example.EnterpriseRagCommunity.entity.rag.QaTurnsEntity;
import com.example.EnterpriseRagCommunity.entity.rag.enums.MessageRole;
import com.example.EnterpriseRagCommunity.exception.ResourceNotFoundException;
import com.example.EnterpriseRagCommunity.repository.rag.QaMessagesRepository;
import com.example.EnterpriseRagCommunity.repository.rag.QaSessionsRepository;
import com.example.EnterpriseRagCommunity.repository.rag.QaTurnsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class QaMessageService {

    private final QaSessionsRepository qaSessionsRepository;
    private final QaMessagesRepository qaMessagesRepository;
    private final QaTurnsRepository qaTurnsRepository;

    @Transactional
    public void updateMyMessage(Long userId, Long messageId, String content) {
        if (userId == null) throw new IllegalArgumentException("userId is required");
        if (messageId == null) throw new IllegalArgumentException("messageId is required");
        if (content == null || content.isBlank()) throw new IllegalArgumentException("content is required");

        QaMessagesEntity msg = qaMessagesRepository.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("message not found"));

        QaSessionsEntity session = qaSessionsRepository.findByIdAndUserId(msg.getSessionId(), userId)
                .orElseThrow(() -> new ResourceNotFoundException("session not found"));
        if (Boolean.FALSE.equals(session.getIsActive())) {
            throw new IllegalArgumentException("session inactive");
        }

        msg.setContent(content);
        qaMessagesRepository.save(msg);
    }

    @Transactional
    public void deleteMyMessage(Long userId, Long messageId) {
        if (userId == null) throw new IllegalArgumentException("userId is required");
        if (messageId == null) throw new IllegalArgumentException("messageId is required");

        QaMessagesEntity msg = qaMessagesRepository.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("message not found"));

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
            }
            qaMessagesRepository.delete(msg);
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
        }
    }
}
