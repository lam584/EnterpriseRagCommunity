-- ############################################################
-- Flyway Baseline Script：初始化新闻发布系统表结构
-- 包含：管理员权限表、管理员账号表、普通用户角色表、用户表、
--      主题（topics）、新闻（news）、评论（comments）
-- ############################################################

-- 1. 管理员权限表（admin_permissions）
CREATE TABLE IF NOT EXISTS admin_permissions (
                                                 id                           BIGINT AUTO_INCREMENT PRIMARY KEY,
                                                 roles                        VARCHAR(100) NOT NULL UNIQUE COMMENT '角色',
                                                 can_login                    TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否可以登录',
                                                 can_manage_announcement      TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否可以管理公告',
                                                 can_manage_help_articles     TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否可以管理帮助文章',
                                                 can_create_super_admin       TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否可以创建超级管理员账号',
                                                 can_create_admin             TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否可以创建管理员账号',
                                                 can_create_user_account      TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否可以创建用户账号',
                                                 can_manage_admin_permissions TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否可以管理管理员权限',
                                                 can_manage_user_permissions  TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否可以管理用户权限',
                                                 can_reset_admin_password     TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否可以重置管理员密码',
                                                 can_reset_user_password      TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否可以重置用户密码',
                                                 can_pay_user_overdue         TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否可以为用户缴纳逾期欠款',
                                                 allow_edit_user_profile   TINYINT(1)   NOT NULL DEFAULT 0 COMMENT    '编辑用户信息，保留兼容',
                                                 allow_edit_profile           TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否可以编辑自己的账号信息',
                                                 allow_edit_other_admin_profile TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否可以为其他管理员编辑账号信息',
                                                 can_manage_topics            TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否可以管理主题',
                                                 can_manage_news              TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否可以管理新闻',
                                                 created_at                   TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                                 updated_at                   TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
                                                     ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. 管理员账号表（administrators）
CREATE TABLE IF NOT EXISTS administrators (
                                              id             BIGINT AUTO_INCREMENT PRIMARY KEY,
                                              account        VARCHAR(100) NOT NULL UNIQUE COMMENT '账号',
                                              password       VARCHAR(255) NOT NULL COMMENT '密码（BCrypt 加密存储）',
                                              phone          VARCHAR(100) NOT NULL UNIQUE COMMENT '手机号',
                                              email          VARCHAR(100) NOT NULL UNIQUE COMMENT '邮箱',
                                              sex            VARCHAR(50)  NOT NULL COMMENT '性别',
                                              registered_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '注册日期',
                                              permissions_id BIGINT       NOT NULL COMMENT '权限ID',
                                              is_active      TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否激活',
                                              created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                              updated_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
                                                  ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                              CONSTRAINT fk_admin_perms
                                                  FOREIGN KEY (permissions_id)
                                                      REFERENCES admin_permissions(id)
                                                      ON UPDATE CASCADE
                                                      ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. 普通用户角色表（user_roles）
CREATE TABLE IF NOT EXISTS user_roles (
                                          id                     BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '角色ID',
                                          roles                  VARCHAR(100) NOT NULL UNIQUE COMMENT '角色',
                                          can_login              TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否可以登录',
                                          can_view_announcement  TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否可以查看公告',
                                          can_view_help_articles TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否可以查看帮助文章',
                                          can_reset_own_password TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否可以重置自己密码',
                                          can_comment            TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否可以评论',
                                          notes                  VARCHAR(100) NOT NULL UNIQUE COMMENT '备注',
                                          created_at             TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                          updated_at             TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
                                              ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4. 普通用户表（users）
CREATE TABLE IF NOT EXISTS users (
                                     id             BIGINT AUTO_INCREMENT PRIMARY KEY,
                                     account        VARCHAR(100) NOT NULL UNIQUE COMMENT '账号',
                                     password       VARCHAR(255) NOT NULL COMMENT '密码（BCrypt 加密存储）',
                                     phone          VARCHAR(100) NOT NULL UNIQUE COMMENT '手机号',
                                     email          VARCHAR(100) NOT NULL UNIQUE COMMENT '邮箱',
                                     sex            VARCHAR(50)  NOT NULL COMMENT '性别',
                                     permissions_id BIGINT       NOT NULL COMMENT '权限ID',
                                     is_active      TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否激活',
                                     created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                     updated_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
                                         ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                     CONSTRAINT fk_users_roles
                                         FOREIGN KEY (permissions_id)
                                             REFERENCES user_roles(id)
                                             ON UPDATE CASCADE
                                             ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5. 主题表（topics）
CREATE TABLE IF NOT EXISTS topics (
                                      id          BIGINT AUTO_INCREMENT PRIMARY KEY,
                                      name        VARCHAR(255) NOT NULL COMMENT '主题名称',
                                      description TEXT         NULL COMMENT '主题描述',
                                      created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                      updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
                                          ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 6. 新闻主表（news）
CREATE TABLE IF NOT EXISTS news (
                                    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
                                    title          VARCHAR(255) NOT NULL COMMENT '新闻标题',
                                    summary        VARCHAR(500) NULL COMMENT '摘要',
                                    content_text   TEXT         NOT NULL COMMENT '纯文本内容',
                                    content_html   MEDIUMTEXT   NULL COMMENT 'HTML 内容',
                                    topic_id       BIGINT       NOT NULL COMMENT '主题ID',
                                    author_id      BIGINT       NOT NULL COMMENT '作者（管理员/编辑员）ID',
                                    publish_date   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发布时间',
                                    status         VARCHAR(50)  NOT NULL DEFAULT 'draft' COMMENT '状态(draft/published/archived)',
                                    view_count     BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '阅读量',
                                    like_count     BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '点赞量',
                                    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                    updated_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
                                        ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                    CONSTRAINT fk_news_topic
                                        FOREIGN KEY (topic_id)
                                            REFERENCES topics(id)
                                            ON UPDATE CASCADE
                                            ON DELETE RESTRICT,
                                    CONSTRAINT fk_news_author
                                        FOREIGN KEY (author_id)
                                            REFERENCES administrators(id)
                                            ON UPDATE CASCADE
                                            ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 7. 评论表（comments）
CREATE TABLE IF NOT EXISTS comments (
                                        id           BIGINT AUTO_INCREMENT PRIMARY KEY,
                                        news_id      BIGINT       NOT NULL COMMENT '新闻ID',
                                        user_id      BIGINT       NULL COMMENT '评论用户ID',
                                        content      TEXT         NOT NULL COMMENT '评论内容',
                                        like_count     BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '点赞量',
                                        status       VARCHAR(20)  NOT NULL DEFAULT 'normal' COMMENT '状态(normal/hidden)',
                                        created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '评论时间',
                                        updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
                                            ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                        CONSTRAINT fk_comments_news
                                            FOREIGN KEY (news_id)
                                                REFERENCES news(id)
                                                ON UPDATE CASCADE
                                                ON DELETE CASCADE,
                                        CONSTRAINT fk_comments_user
                                            FOREIGN KEY (user_id)
                                                REFERENCES users(id)
                                                ON UPDATE CASCADE
                                                ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 8. 系统管理员日志表（system_admin_logs）
CREATE TABLE IF NOT EXISTS system_admin_logs (
                                                 id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
                                                 level              VARCHAR(50)  NOT NULL COMMENT '日志级别',
                                                 message            VARCHAR(255) NOT NULL COMMENT '日志消息',
                                                 context            TEXT         NOT NULL COMMENT '日志上下文(JSON)',
                                                 ip                 VARCHAR(45)  NOT NULL COMMENT 'IP地址',
                                                 admin_user_agent   VARCHAR(255) NOT NULL COMMENT '用户代理',
                                                 admin_id           BIGINT       NOT NULL COMMENT '管理员ID',
                                                 created_at         TIMESTAMP    NULL COMMENT '记录时间',
                                                 updated_at         TIMESTAMP    NULL COMMENT '更新时间',
                                                 INDEX idx_admin_logs_level_time (level, created_at),
                                                 CONSTRAINT fk_sal_admin
                                                     FOREIGN KEY (admin_id)
                                                         REFERENCES administrators(id)
                                                         ON UPDATE CASCADE
                                                         ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 9. 系统用户日志表（system_user_logs）
CREATE TABLE IF NOT EXISTS system_user_logs (
                                                id               BIGINT AUTO_INCREMENT PRIMARY KEY,
                                                level            VARCHAR(50)  NOT NULL COMMENT '日志级别',
                                                message          VARCHAR(255) NOT NULL COMMENT '日志消息',
                                                context          TEXT         NOT NULL COMMENT '日志上下文(JSON)',
                                                ip               VARCHAR(45)  NOT NULL COMMENT 'IP地址',
                                                user_agent       VARCHAR(255) NOT NULL COMMENT '用户代理',
                                                user_id          BIGINT       NOT NULL COMMENT '用户ID',
                                                created_at       TIMESTAMP    NULL COMMENT '记录时间',
                                                updated_at       TIMESTAMP    NULL COMMENT '更新时间',
                                                INDEX idx_user_logs_level_time (level, created_at),
                                                CONSTRAINT fk_sul_user
                                                    FOREIGN KEY (user_id)
                                                        REFERENCES users(id)
                                                        ON UPDATE CASCADE
                                                        ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;