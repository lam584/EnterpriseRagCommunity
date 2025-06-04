-- Flyway 迁移脚本：V3__Test.sql

-- 1）创建 user_account 表，增加 gender 列
CREATE TABLE IF NOT EXISTS user_account (
                                            id          BIGINT AUTO_INCREMENT PRIMARY KEY,
                                            username    VARCHAR(50)   NOT NULL UNIQUE,
    password    VARCHAR(255)  NOT NULL,
    gender      VARCHAR(10)   NOT NULL DEFAULT '男',
    email       VARCHAR(100)  NOT NULL UNIQUE,
    phone       VARCHAR(20),
    student_id  VARCHAR(20),
    created_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2）插入一条示例数据，注意列名和值一一对应
INSERT INTO user_account
(username, password, gender, email, phone, student_id)
VALUES
    ('admin',
     '$2a$10$LFcJN43rugWXXkmz2JOvl.NbsQ01o5YJ8SsCEwn1Q/bKujnXYG6ne',
     '男',
     'abc@vip.qq.com',
     '12345678910',
     '123456789'
    )
    ON DUPLICATE KEY UPDATE
                         password   = VALUES(password),
                         gender     = VALUES(gender),
                         email      = VALUES(email),
                         phone      = VALUES(phone),
                         student_id = VALUES(student_id);

