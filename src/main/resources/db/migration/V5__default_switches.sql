-- V5：默认开关修正（2FA 登录二次验证作用范围、LLM 自动审核）

INSERT IGNORE INTO app_settings(k, v)
VALUES ('security_login2fa_scope_policy', 'ALLOW_ALL');

INSERT IGNORE INTO app_settings(k, v)
VALUES ('security_login2fa_mode', 'EMAIL_OR_TOTP');

UPDATE moderation_llm_config
SET auto_run = 1,
    updated_at = NOW(3)
WHERE id = 1
  AND (auto_run IS NULL OR auto_run = 0)
  AND (version IS NULL OR version = 0)
  AND updated_by IS NULL;
