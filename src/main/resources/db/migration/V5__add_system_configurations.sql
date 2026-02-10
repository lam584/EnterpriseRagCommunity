CREATE TABLE system_configurations (
    config_key VARCHAR(255) NOT NULL PRIMARY KEY,
    config_value VARCHAR(2048),
    is_encrypted BOOLEAN DEFAULT FALSE,
    description VARCHAR(255)
);
