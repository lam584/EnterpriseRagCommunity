INSERT INTO system_configurations (config_key, config_value, is_encrypted, description)
VALUES ('APP_AI_BASE_URL', '${TEST_APP_AI_BASE_URL}', FALSE, 'Test bootstrap config'),
       ('APP_AI_API_KEY', '${TEST_APP_AI_API_KEY}', FALSE, 'Test bootstrap config'),
       ('APP_AI_MODEL', '${TEST_APP_AI_MODEL}', FALSE, 'Test bootstrap config'),
       ('APP_AI_TOKENIZER_API_KEY', '${TEST_APP_AI_TOKENIZER_API_KEY}', FALSE, 'Test bootstrap config'),
       ('APP_TOTP_MASTER_KEY', '${TEST_APP_TOTP_MASTER_KEY}', FALSE, 'Test bootstrap config'),
       ('spring.elasticsearch.uris', '${TEST_SPRING_ELASTICSEARCH_URIS}', FALSE, 'Test bootstrap config'),
       ('APP_ES_API_KEY', '${TEST_APP_ES_API_KEY}', FALSE, 'Test bootstrap config'),
       ('APP_MAIL_HOST', '${TEST_APP_MAIL_HOST}', FALSE, 'Test bootstrap config'),
       ('APP_MAIL_PORT', '${TEST_APP_MAIL_PORT}', FALSE, 'Test bootstrap config'),
       ('APP_MAIL_INBOX_HOST', '${TEST_APP_MAIL_INBOX_HOST}', FALSE, 'Test bootstrap config'),
       ('APP_MAIL_INBOX_PORT', '${TEST_APP_MAIL_INBOX_PORT}', FALSE, 'Test bootstrap config'),
       ('APP_MAIL_FROM', '${TEST_APP_MAIL_FROM}', FALSE, 'Test bootstrap config'),
       ('APP_MAIL_FROM_ADDRESS', '${TEST_APP_MAIL_FROM_ADDRESS}', FALSE, 'Test bootstrap config'),
       ('APP_MAIL_USERNAME', '${TEST_APP_MAIL_USERNAME}', FALSE, 'Test bootstrap config'),
       ('APP_MAIL_PASSWORD', '${TEST_APP_MAIL_PASSWORD}', FALSE, 'Test bootstrap config')
ON DUPLICATE KEY UPDATE
    config_value = VALUES(config_value),
    is_encrypted = VALUES(is_encrypted),
    description = VALUES(description);
