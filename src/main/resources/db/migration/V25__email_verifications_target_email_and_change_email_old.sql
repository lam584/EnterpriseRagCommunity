ALTER TABLE email_verifications
    ADD COLUMN target_email VARCHAR(191) NULL COMMENT '目标邮箱' AFTER user_id;

ALTER TABLE email_verifications
    MODIFY COLUMN purpose ENUM('VERIFY_EMAIL','PASSWORD_RESET','REGISTER','CHANGE_PASSWORD','CHANGE_EMAIL','CHANGE_EMAIL_OLD','TOTP_ENABLE') NOT NULL COMMENT '用途';

CREATE INDEX idx_ev_user_purpose_target_created ON email_verifications (user_id, purpose, target_email, created_at);

