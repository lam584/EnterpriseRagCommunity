-- Add indexes/fulltext indexes for QA history search & listing
-- MySQL 8.0 InnoDB supports FULLTEXT.

SET NAMES utf8mb4;

-- Sessions: list my sessions quickly
ALTER TABLE qa_sessions
    ADD INDEX idx_qs_user_created (user_id, created_at);

-- Messages: search within a user's sessions quickly
ALTER TABLE qa_messages
    ADD INDEX idx_qm_session_created (session_id, created_at);

-- Fulltext search (title + message content)
-- Note: FULLTEXT on LONGTEXT is supported in MySQL 8.0 InnoDB.
ALTER TABLE qa_sessions
    ADD FULLTEXT INDEX ft_qs_title (title);

ALTER TABLE qa_messages
    ADD FULLTEXT INDEX ft_qm_content (content);

