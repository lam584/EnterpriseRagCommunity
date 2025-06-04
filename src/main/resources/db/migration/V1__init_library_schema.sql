-- admin_permissions 表
CREATE TABLE IF NOT EXISTS admin_permissions (
                                                 id                            BIGINT AUTO_INCREMENT PRIMARY KEY,
                                                 roles                         VARCHAR(100) NOT NULL UNIQUE COMMENT '角色',
                                                 can_login                     TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否可以登录',
                                                 can_manage_announcement       TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否可以管理公告',
                                                 can_manage_help_articles      TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否可以管理帮助文章',
                                                 can_create_super_admin        TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否可以创建超级管理员账号',
                                                 can_create_admin              TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否可以创建管理员账号',
                                                 can_create_user_account       TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否可以创建用户账号',
                                                 can_manage_admin_permissions  TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否可以管理管理员权限',
                                                 can_manage_user_permissions   TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否可以管理用户权限',
                                                 can_reset_admin_password      TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否可以重置管理员密码',
                                                 can_reset_user_password       TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否可以重置用户密码',
                                                 can_pay_user_overdue          TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否可以为用户缴纳逾期欠款',
                                                 can_lend_books_to_user        TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否可以为用户借阅图书',
                                                 can_return_books_for_user     TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否可以为用户归还图书',
                                                 allow_edit_readers_profile    TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否可以为用户编辑账号信息',
                                                 allow_edit_profile            TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否可以编辑自己的账号信息',
                                                 allow_edit_other_admin_profile TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '是否可以为其他管理员编辑账号信息',
                                                 created_at                    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                                 updated_at                    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- book_categories 表
CREATE TABLE IF NOT EXISTS book_categories (
                                               id          BIGINT AUTO_INCREMENT PRIMARY KEY,
                                               name        VARCHAR(255)       NOT NULL COMMENT '分类名称',
                                               description TEXT               NULL COMMENT '分类描述',
                                               created_at  TIMESTAMP          NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                               updated_at  TIMESTAMP          NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- book_shelves 表
CREATE TABLE IF NOT EXISTS book_shelves (
                                            id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
                                            shelf_code           VARCHAR(255)       NOT NULL UNIQUE COMMENT '书架编码',
                                            location_description VARCHAR(255)       NOT NULL COMMENT '书架位置描述',
                                            capacity             INT UNSIGNED       NOT NULL COMMENT '容量',
                                            created_at           TIMESTAMP          NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                            updated_at           TIMESTAMP          NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- readers_permissions 表
CREATE TABLE IF NOT EXISTS readers_permissions (
                                                   id                      BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '权限ID',
                                                   roles                   VARCHAR(100)       NOT NULL UNIQUE COMMENT '角色',
                                                   can_login               TINYINT(1)         NOT NULL DEFAULT 0 COMMENT '是否可以登录',
                                                   can_reserve             TINYINT(1)         NOT NULL DEFAULT 0 COMMENT '是否可以预约',
                                                   can_view_announcement   TINYINT(1)         NOT NULL DEFAULT 0 COMMENT '自己是否可以查看公告',
                                                   can_view_help_articles  TINYINT(1)         NOT NULL DEFAULT 0 COMMENT '自己是否可以查看帮助文章',
                                                   can_reset_own_password  TINYINT(1)         NOT NULL DEFAULT 0 COMMENT '自己是否可以重置自己的账号密码',
                                                   can_borrow_return_books TINYINT(1)         NOT NULL DEFAULT 0 COMMENT '自己是否可以归还、借阅图书',
                                                   allow_edit_profile      TINYINT(1)         NOT NULL DEFAULT 0 COMMENT '自己是否可以修改账户信息',
                                                   notes                   VARCHAR(100)       NOT NULL UNIQUE COMMENT '备注',
                                                   created_at              TIMESTAMP          NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                                   updated_at              TIMESTAMP          NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- administrators 表
CREATE TABLE IF NOT EXISTS administrators (
                                              id             BIGINT AUTO_INCREMENT PRIMARY KEY,
                                              account        VARCHAR(100)       NOT NULL UNIQUE COMMENT '账号',
                                              password       VARCHAR(255)       NOT NULL COMMENT '密码',
                                              phone          VARCHAR(100)       NOT NULL UNIQUE COMMENT '手机号',
                                              email          VARCHAR(100)       NOT NULL UNIQUE COMMENT '邮箱',
                                              sex            VARCHAR(100)       NOT NULL COMMENT '性别',
                                              registered_at  DATETIME           NOT NULL COMMENT '注册日期',
                                              permissions_id BIGINT             NOT NULL COMMENT '权限ID',
                                              is_active      TINYINT(1)         NOT NULL DEFAULT 0 COMMENT '是否激活',
                                              created_at     TIMESTAMP          NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                              updated_at     TIMESTAMP          NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                              CONSTRAINT fk_admin_perms
                                                  FOREIGN KEY (permissions_id)
                                                      REFERENCES admin_permissions(id)
                                                      ON UPDATE CASCADE
                                                      ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- books 表
CREATE TABLE IF NOT EXISTS books (
                                     id               BIGINT AUTO_INCREMENT PRIMARY KEY,
                                     isbn             VARCHAR(100)      NOT NULL COMMENT 'ISBN号',
                                     title            VARCHAR(100)      NOT NULL COMMENT '标题',
                                     author           VARCHAR(100)      NOT NULL COMMENT '作者',
                                     publisher        VARCHAR(100)      NOT NULL COMMENT '出版社',
                                     edition          VARCHAR(100)      NOT NULL COMMENT '版次',
                                     price            DECIMAL(10,2)     NOT NULL COMMENT '定价',
                                     category_id      BIGINT            NOT NULL COMMENT '分类ID',
                                     shelves_id       BIGINT            NOT NULL COMMENT '书架ID',
                                     status           VARCHAR(100)      NOT NULL COMMENT '状态',
                                     print_times      VARCHAR(255)      NOT NULL COMMENT '印次',
                                     administrator_id BIGINT            NOT NULL COMMENT '管理员ID',
                                     created_at       TIMESTAMP         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                     updated_at       TIMESTAMP         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                     CONSTRAINT fk_books_category
                                         FOREIGN KEY (category_id)
                                             REFERENCES book_categories(id)
                                             ON UPDATE CASCADE
                                             ON DELETE RESTRICT,
                                     CONSTRAINT fk_books_shelves
                                         FOREIGN KEY (shelves_id)
                                             REFERENCES book_shelves(id)
                                             ON UPDATE CASCADE
                                             ON DELETE RESTRICT,
                                     CONSTRAINT fk_books_admin
                                         FOREIGN KEY (administrator_id)
                                             REFERENCES administrators(id)
                                             ON UPDATE CASCADE
                                             ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- readers 表
CREATE TABLE IF NOT EXISTS readers (
                                       id             BIGINT AUTO_INCREMENT PRIMARY KEY,
                                       account        VARCHAR(100)     NOT NULL UNIQUE COMMENT '账号',
                                       password       VARCHAR(255)     NOT NULL COMMENT '密码',
                                       phone          VARCHAR(100)     NOT NULL UNIQUE COMMENT '手机号',
                                       email          VARCHAR(100)     NOT NULL UNIQUE COMMENT '邮箱',
                                       sex            VARCHAR(100)     NOT NULL COMMENT '性别',
                                       permissions_id BIGINT           NOT NULL COMMENT '权限ID',
                                       is_active      TINYINT(1)       NOT NULL DEFAULT 0 COMMENT '是否激活',
                                       created_at     TIMESTAMP        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                       updated_at     TIMESTAMP        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                       CONSTRAINT fk_readers_perms
                                           FOREIGN KEY (permissions_id)
                                               REFERENCES readers_permissions(id)
                                               ON UPDATE CASCADE
                                               ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- book_loans 表
CREATE TABLE IF NOT EXISTS book_loans (
                                          id              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '订单号',
                                          book_id         BIGINT            NOT NULL COMMENT '图书ID',
                                          reader_id       BIGINT            NOT NULL COMMENT '读者ID',
                                          administrator_id BIGINT           NOT NULL COMMENT '管理员ID',
                                          start_time      DATETIME          NOT NULL COMMENT '开始时间',
                                          end_time        DATETIME          NULL COMMENT '结束时间',
                                          status          VARCHAR(100)      NOT NULL COMMENT '状态',
                                          price           DECIMAL(10,2)     NOT NULL COMMENT '单价',
                                          renew_count     INT UNSIGNED      NOT NULL DEFAULT 0 COMMENT '续借次数',
                                          renew_duration  INT               NOT NULL DEFAULT 0 COMMENT '续借时长（天）',
                                          created_at      TIMESTAMP         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                          updated_at      TIMESTAMP         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                          CONSTRAINT fk_loans_book
                                              FOREIGN KEY (book_id)
                                                  REFERENCES books(id)
                                                  ON UPDATE CASCADE
                                                  ON DELETE RESTRICT,
                                          CONSTRAINT fk_loans_reader
                                              FOREIGN KEY (reader_id)
                                                  REFERENCES readers(id)
                                                  ON UPDATE CASCADE
                                                  ON DELETE RESTRICT,
                                          CONSTRAINT fk_loans_admin
                                              FOREIGN KEY (administrator_id)
                                                  REFERENCES administrators(id)
                                                  ON UPDATE CASCADE
                                                  ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- payment_bills 表
CREATE TABLE IF NOT EXISTS payment_bills (
                                             id           BIGINT AUTO_INCREMENT PRIMARY KEY,
                                             amount_paid  DECIMAL(8,2)   NOT NULL COMMENT '支付金额',
                                             total_paid   DECIMAL(8,2)   NOT NULL COMMENT '实际应付金额',
                                             change_given DECIMAL(8,2)   NOT NULL COMMENT '找零金额',
                                             payment_date DATETIME       NOT NULL COMMENT '支付时间',
                                             admin_id     BIGINT         NOT NULL COMMENT '管理员ID',
                                             reader_id    BIGINT         NOT NULL COMMENT '读者ID',
                                             remarks      TEXT           NULL COMMENT '备注',
                                             created_at   TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                             updated_at   TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                             CONSTRAINT fk_pay_admin
                                                 FOREIGN KEY (admin_id)
                                                     REFERENCES administrators(id)
                                                     ON UPDATE CASCADE
                                                     ON DELETE RESTRICT,
                                             CONSTRAINT fk_pay_reader
                                                 FOREIGN KEY (reader_id)
                                                     REFERENCES readers(id)
                                                     ON UPDATE CASCADE
                                                     ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- fines_rules 表
CREATE TABLE IF NOT EXISTS fines_rules (
                                           id            BIGINT AUTO_INCREMENT PRIMARY KEY,
                                           name          VARCHAR(255)    NOT NULL COMMENT '简单描述规则内容',
                                           day_min       INT             NOT NULL COMMENT '逾期天数下限',
                                           day_max       INT             NOT NULL COMMENT '逾期天数上限',
                                           fine_per_day  DECIMAL(10,2)   NOT NULL COMMENT '逾期每日的罚款金额',
                                           status        TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '规则是否有效',
                                           admin_id      BIGINT          NOT NULL COMMENT '管理员ID',
                                           created_at    TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                           updated_at    TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                           CONSTRAINT fk_fines_admin
                                               FOREIGN KEY (admin_id)
                                                   REFERENCES administrators(id)
                                                   ON UPDATE CASCADE
                                                   ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- overdue_payments 表
CREATE TABLE IF NOT EXISTS overdue_payments (
                                                id               BIGINT AUTO_INCREMENT PRIMARY KEY,
                                                reader_id        BIGINT           NOT NULL COMMENT '读者ID',
                                                loan_order_id    BIGINT           NOT NULL COMMENT '借阅订单ID',
                                                overdue_days     INT              NOT NULL COMMENT '逾期天数',
                                                amount           DECIMAL(8,2)     NOT NULL COMMENT '逾期欠款金额',
                                                due_date         DATETIME         NULL COMMENT '截止日期',
                                                is_cleared       TINYINT(1)       NOT NULL DEFAULT 0 COMMENT '是否已缴清',
                                                payment_bill_id  BIGINT           NULL COMMENT '支付账单ID',
                                                paid_amount      DECIMAL(8,2)     NOT NULL DEFAULT 0 COMMENT '已支付金额',
                                                repaid_date      DATETIME         NULL COMMENT '实际还款日期',
                                                remarks          TEXT             NULL COMMENT '备注',
                                                created_at       TIMESTAMP        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                                updated_at       TIMESTAMP        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                                CONSTRAINT fk_overdue_reader
                                                    FOREIGN KEY (reader_id)
                                                        REFERENCES readers(id)
                                                        ON UPDATE CASCADE
                                                        ON DELETE RESTRICT,
                                                CONSTRAINT fk_overdue_loan
                                                    FOREIGN KEY (loan_order_id)
                                                        REFERENCES book_loans(id)
                                                        ON UPDATE CASCADE
                                                        ON DELETE RESTRICT,
                                                CONSTRAINT fk_overdue_bill
                                                    FOREIGN KEY (payment_bill_id)
                                                        REFERENCES payment_bills(id)
                                                        ON UPDATE CASCADE
                                                        ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- help_articles 表
CREATE TABLE IF NOT EXISTS help_articles (
                                             id               BIGINT AUTO_INCREMENT PRIMARY KEY,
                                             title            VARCHAR(255)     NOT NULL COMMENT '文章标题',
                                             content_html     TEXT             NOT NULL COMMENT '文章内容（HTML）',
                                             content_text     TEXT             NOT NULL COMMENT '文章内容（纯文本）',
                                             view_count       BIGINT UNSIGNED  NOT NULL DEFAULT 0 COMMENT '阅读量',
                                             like_count       BIGINT UNSIGNED  NOT NULL DEFAULT 0 COMMENT '点赞量',
                                             administrator_id BIGINT           NOT NULL COMMENT '作者ID',
                                             created_at       TIMESTAMP        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                             updated_at       TIMESTAMP        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                             CONSTRAINT fk_help_admin
                                                 FOREIGN KEY (administrator_id)
                                                     REFERENCES administrators(id)
                                                     ON UPDATE CASCADE
                                                     ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- announcements 表
CREATE TABLE IF NOT EXISTS announcements (
                                             id               BIGINT AUTO_INCREMENT PRIMARY KEY,
                                             title            VARCHAR(255)     NOT NULL COMMENT '公告标题',
                                             content          TEXT             NOT NULL COMMENT '公告内容',
                                             administrator_id BIGINT           NOT NULL COMMENT '作者ID',
                                             view_count       BIGINT UNSIGNED  NOT NULL DEFAULT 0 COMMENT '阅读量',
                                             created_at       TIMESTAMP        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                             updated_at       TIMESTAMP        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                             CONSTRAINT fk_announce_admin
                                                 FOREIGN KEY (administrator_id)
                                                     REFERENCES administrators(id)
                                                     ON UPDATE CASCADE
                                                     ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- system_Admin_logs 表
CREATE TABLE IF NOT EXISTS system_Admin_logs (
                                                 id               BIGINT AUTO_INCREMENT PRIMARY KEY,
                                                 level            VARCHAR(50)      NOT NULL COMMENT '日志级别',
                                                 message          VARCHAR(255)     NOT NULL COMMENT '日志消息',
                                                 context          TEXT             NOT NULL COMMENT '日志上下文(JSON)',
                                                 ip               VARCHAR(45)      NOT NULL COMMENT 'IP地址',
                                                 Admin_user_agent VARCHAR(255)     NOT NULL COMMENT '用户代理',
                                                 Admin_id         BIGINT           NOT NULL COMMENT '管理员ID',
                                                 created_at       TIMESTAMP        NULL COMMENT '记录时间',
                                                 updated_at       TIMESTAMP        NULL,
                                                 INDEX idx_admin_logs_level_time (level, created_at),
                                                 CONSTRAINT fk_sal_admin
                                                     FOREIGN KEY (Admin_id)
                                                         REFERENCES administrators(id)
                                                         ON UPDATE CASCADE
                                                         ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- system_readers_logs 表
CREATE TABLE IF NOT EXISTS system_readers_logs (
                                                   id             BIGINT AUTO_INCREMENT PRIMARY KEY,
                                                   level          VARCHAR(50)      NOT NULL COMMENT '日志级别',
                                                   message        VARCHAR(255)     NOT NULL COMMENT '日志消息',
                                                   context        TEXT             NOT NULL COMMENT '日志上下文(JSON)',
                                                   ip             VARCHAR(45)      NOT NULL COMMENT 'IP地址',
                                                   readers_agent  VARCHAR(255)     NOT NULL COMMENT '用户代理',
                                                   readers_id     BIGINT           NOT NULL COMMENT '读者ID',
                                                   created_at     TIMESTAMP        NULL COMMENT '记录时间',
                                                   updated_at     TIMESTAMP        NULL,
                                                   INDEX idx_reader_logs_level_time (level, created_at),
                                                   CONSTRAINT fk_srl_reader
                                                       FOREIGN KEY (readers_id)
                                                           REFERENCES readers(id)
                                                           ON UPDATE CASCADE
                                                           ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
