ALTER TABLE email_verifications
    MODIFY COLUMN purpose ENUM(
        'VERIFY_EMAIL',
        'PASSWORD_RESET',
        'REGISTER',
        'LOGIN_2FA',
        'LOGIN_2FA_PREFERENCE',
        'CHANGE_PASSWORD',
        'CHANGE_EMAIL',
        'CHANGE_EMAIL_OLD',
        'TOTP_ENABLE',
        'TOTP_DISABLE'
    ) NOT NULL COMMENT '用途';
