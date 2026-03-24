-- MySQL 8.0, InnoDB, utf8mb4
-- 统一建表选项
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE tenants (
                         id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
                         code VARCHAR(64) NOT NULL COMMENT '租户编码（唯一）',
                         name VARCHAR(128) NOT NULL COMMENT '租户名称（企业/组织名）',
                         metadata JSON NULL COMMENT '扩展元数据（JSON）',
                         created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
                         updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
                         UNIQUE KEY uk_tenants_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='租户表（多租户可选）';

CREATE TABLE users (
                       id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
                       tenant_id BIGINT UNSIGNED NULL COMMENT '租户ID',
                       email VARCHAR(191) NOT NULL COMMENT '邮箱（登录名）',
                       username VARCHAR(64) NOT NULL COMMENT '用户名（展示名/别名）',
                       password_hash VARCHAR(191) NOT NULL COMMENT '密码哈希',
                       status ENUM('ACTIVE','DISABLED','EMAIL_UNVERIFIED','DELETED') NOT NULL DEFAULT 'ACTIVE' COMMENT '账户状态',
                       last_login_at DATETIME(3) NULL COMMENT '上次登录时间',
                       is_deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '软删除标记',
                       metadata JSON NULL COMMENT '用户扩展元数据（JSON）',
                       created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
                       updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
                       session_invalidated_at DATETIME(3) NULL COMMENT '强制所有会话重新登录时间点',
                       access_version BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'RBAC 权限版本号',
                       UNIQUE KEY uk_users_email_tenant (tenant_id, email),
                       UNIQUE KEY uk_users_username_tenant (tenant_id, username),
                       KEY idx_users_access_version (access_version),
                       CONSTRAINT fk_users_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 角色元数据（前移至此，因被 user_role_links / role_permissions / board_role_permissions 引用）
CREATE TABLE roles (
    role_id BIGINT UNSIGNED PRIMARY KEY COMMENT '角色ID',
    role_name VARCHAR(128) NOT NULL COMMENT '角色名',
    description VARCHAR(255) NULL COMMENT '角色说明',
    risk_level ENUM('LOW','MEDIUM','HIGH') NOT NULL DEFAULT 'LOW' COMMENT '风险级别',
    builtin TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否内置角色',
    immutable TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否不可变更(防止锁死/夺权)',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    UNIQUE KEY uk_roles_name (role_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色元数据';

INSERT INTO roles (role_id, role_name, description, risk_level, builtin, immutable)
VALUES
    (1, 'USER', '默认普通用户', 'LOW', 1, 1),
    (2, 'ADMIN', '系统管理员', 'HIGH', 1, 1) AS new
ON DUPLICATE KEY UPDATE
    role_name = new.role_name,
    description = new.description,
    risk_level = new.risk_level,
    builtin = new.builtin,
    immutable = new.immutable;

-- 用户-角色关联
CREATE TABLE user_role_links (
    user_id BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    role_id BIGINT UNSIGNED NOT NULL COMMENT '角色ID',
    scope_type ENUM('GLOBAL','TENANT','BOARD') NOT NULL DEFAULT 'GLOBAL' COMMENT '作用域类型',
    scope_id BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '作用域ID（GLOBAL 固定为 0）',
    expires_at DATETIME(3) NULL COMMENT '到期时间（NULL 表示永久）',
    assigned_by BIGINT UNSIGNED NULL COMMENT '授予人用户ID',
    assigned_reason VARCHAR(255) NULL COMMENT '授予原因/工单号',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (user_id, role_id, scope_type, scope_id),
    KEY idx_url_role_id (role_id),
    CONSTRAINT fk_url_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_url_role FOREIGN KEY (role_id) REFERENCES roles(role_id) ON DELETE CASCADE,
    CONSTRAINT fk_url_assigned_by FOREIGN KEY (assigned_by) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户与角色关联表（含作用域/有效期）';

-- 可选：细粒度权限
CREATE TABLE permissions (
                             id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
                             resource VARCHAR(64) NOT NULL COMMENT '资源名称（如 post、comment）',
                             action VARCHAR(32) NOT NULL COMMENT '操作名称（如 read、write）',
                             description VARCHAR(255) NULL COMMENT '权限说明',
                             UNIQUE KEY uk_perm (resource, action)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限枚举表';

CREATE TABLE role_permissions (
                                  role_id BIGINT UNSIGNED NOT NULL COMMENT '角色ID',
                                  role_name VARCHAR(128) NULL COMMENT '角色名',
                                  permission_id BIGINT UNSIGNED NOT NULL COMMENT '权限ID',
                                  allow TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否允许',
                                  PRIMARY KEY (role_id, permission_id),
                                  CONSTRAINT fk_rp_role FOREIGN KEY (role_id) REFERENCES roles(role_id) ON DELETE CASCADE,
                                  CONSTRAINT fk_rp_perm FOREIGN KEY (permission_id) REFERENCES permissions(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色-权限矩阵表';

CREATE INDEX idx_role_permissions_role_name ON role_permissions(role_name);

CREATE TABLE auth_sessions (
                               id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
                               user_id BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
                               refresh_token_hash VARCHAR(191) NOT NULL COMMENT '刷新令牌哈希',
                               user_agent VARCHAR(255) NULL COMMENT 'UA 信息',
                               ip VARCHAR(64) NULL COMMENT 'IP 地址',
                               expires_at DATETIME(3) NOT NULL COMMENT '过期时间',
                               revoked_at DATETIME(3) NULL COMMENT '撤销时间',
                               created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
                               KEY idx_auth_user (user_id),
                               CONSTRAINT fk_auth_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='登录会话表';

CREATE TABLE email_verifications (
                                     id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
                                     user_id BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
                                     target_email VARCHAR(191) NULL COMMENT '目标邮箱',
                                     code VARCHAR(64) NOT NULL COMMENT '验证码',
                                     purpose ENUM('VERIFY_EMAIL','PASSWORD_RESET','REGISTER','LOGIN_2FA','LOGIN_2FA_PREFERENCE','CHANGE_PASSWORD','CHANGE_EMAIL','CHANGE_EMAIL_OLD','TOTP_ENABLE','TOTP_DISABLE','ADMIN_STEP_UP') NOT NULL COMMENT '用途',
                                     expires_at DATETIME(3) NOT NULL COMMENT '过期时间',
                                     consumed_at DATETIME(3) NULL COMMENT '使用时间',
                                     created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
                                     KEY idx_ev_user (user_id),
                                     KEY idx_ev_user_purpose_target_created (user_id, purpose, target_email, created_at),
                                     CONSTRAINT fk_ev_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='邮箱验证/找回码表';

CREATE TABLE password_reset_tokens (
                                       id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
                                       user_id BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
                                       token_hash VARCHAR(191) NOT NULL COMMENT '重置令牌哈希',
                                       expires_at DATETIME(3) NOT NULL COMMENT '过期时间',
                                       consumed_at DATETIME(3) NULL COMMENT '使用时间',
                                       created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
                                       UNIQUE KEY uk_prt_user_token (user_id, token_hash),
                                       CONSTRAINT fk_prt_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='密码重置令牌表';

CREATE TABLE totp_secrets (
                              id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
                              user_id BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
                              secret_encrypted VARBINARY(512) NOT NULL COMMENT 'TOTP 密钥（加密）',
                              algorithm VARCHAR(16) NOT NULL DEFAULT 'SHA1' COMMENT 'HMAC 算法（SHA1/SHA256/SHA512）',
                              digits TINYINT UNSIGNED NOT NULL DEFAULT 6 COMMENT '验证码位数（6/8）',
                              period_seconds SMALLINT UNSIGNED NOT NULL DEFAULT 30 COMMENT '时间步长（秒）',
                              skew TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '允许时间偏移窗口（步数）',
                              enabled TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否启用二次验证',
                              verified_at DATETIME(3) NULL COMMENT '验证通过时间',
                              created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
                              CONSTRAINT fk_totp_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='TOTP 二次验证表';

CREATE TABLE login_attempts (
                                id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
                                user_id BIGINT UNSIGNED NULL COMMENT '用户ID（若可识别）',
                                ip VARCHAR(64) NULL COMMENT '来源 IP',
                                success TINYINT(1) NOT NULL COMMENT '是否成功',
                                reason VARCHAR(64) NULL COMMENT '失败原因/备注',
                                occurred_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '发生时间',
                                KEY idx_la_user (user_id),
                                KEY idx_la_occurred (occurred_at),
                                CONSTRAINT fk_la_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='登录尝试日志';

CREATE TABLE audit_logs (
                            id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
                            tenant_id BIGINT UNSIGNED NULL COMMENT '租户ID',
                            actor_user_id BIGINT UNSIGNED NULL COMMENT '操作者用户ID',
                            action VARCHAR(64) NOT NULL COMMENT '动作名称',
                            entity_type VARCHAR(64) NOT NULL COMMENT '实体类型',
                            entity_id BIGINT UNSIGNED NULL COMMENT '实体ID',
                            result ENUM('SUCCESS','FAIL') NOT NULL COMMENT '结果',
                            details JSON NULL COMMENT '详情（JSON）',
                            created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
                            archived_at DATETIME(3) NULL COMMENT '归档时间',
                            KEY idx_audit_entity (entity_type, entity_id),
                            KEY idx_audit_actor (actor_user_id),
                            KEY idx_audit_archived_at (archived_at),
                            CONSTRAINT fk_audit_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
                            CONSTRAINT fk_audit_actor FOREIGN KEY (actor_user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审计日志表';

-- 内容与组织
CREATE TABLE boards (
                        id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
                        tenant_id BIGINT UNSIGNED NULL COMMENT '租户ID',
                        parent_id BIGINT UNSIGNED NULL COMMENT '父板块ID',
                        name VARCHAR(64) NOT NULL COMMENT '板块名称',
                        description VARCHAR(255) NULL COMMENT '板块描述',
                        visible TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否可见',
                        sort_order INT NOT NULL DEFAULT 0 COMMENT '排序权重',
                        created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
                        updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
                        CONSTRAINT fk_boards_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
                        CONSTRAINT fk_boards_parent FOREIGN KEY (parent_id) REFERENCES boards(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='板块/栏目表';

CREATE TABLE posts (
                       id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
                       tenant_id BIGINT UNSIGNED NULL COMMENT '租户ID',
                       board_id BIGINT UNSIGNED NOT NULL COMMENT '所属板块ID',
                       author_id BIGINT UNSIGNED NOT NULL COMMENT '作者用户ID',
                       title VARCHAR(191) NOT NULL COMMENT '帖子标题',
                       content LONGTEXT NOT NULL COMMENT '帖子内容',
                       content_format ENUM('PLAIN','MARKDOWN','HTML') NOT NULL DEFAULT 'MARKDOWN' COMMENT '内容格式',
                       content_length INT NOT NULL DEFAULT 0 COMMENT '内容长度',
                       is_chunked_review TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否分片审核',
                       chunk_threshold_chars INT NULL COMMENT '分片阈值',
                       chunking_strategy VARCHAR(16) NULL COMMENT '分片策略',
                       status ENUM('DRAFT','PENDING','PUBLISHED','REJECTED','ARCHIVED') NOT NULL DEFAULT 'DRAFT' COMMENT '帖子状态',
                       published_at DATETIME(3) NULL COMMENT '发布时间',
                       is_deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '软删除标记',
                       metadata JSON NULL COMMENT '扩展元数据（JSON）',
                       created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
                       updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
                       KEY idx_posts_board (board_id, status),
                       KEY idx_posts_author (author_id),
                       CONSTRAINT fk_posts_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
                       CONSTRAINT fk_posts_board FOREIGN KEY (board_id) REFERENCES boards(id),
                       CONSTRAINT fk_posts_author FOREIGN KEY (author_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='帖子主表';

CREATE TABLE post_versions (
                               id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
                               post_id BIGINT UNSIGNED NOT NULL COMMENT '帖子ID',
                               version INT NOT NULL COMMENT '版本号（从1递增）',
                               editor_id BIGINT UNSIGNED NULL COMMENT '编辑用户ID',
                               title VARCHAR(191) NOT NULL COMMENT '版本标题',
                               content LONGTEXT NOT NULL COMMENT '版本内容',
                               reason VARCHAR(255) NULL COMMENT '编辑原因',
                               created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
                               UNIQUE KEY uk_pv_post_version (post_id, version),
                               CONSTRAINT fk_pv_post FOREIGN KEY (post_id) REFERENCES posts(id) ON DELETE CASCADE,
                               CONSTRAINT fk_pv_editor FOREIGN KEY (editor_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='帖子版本/历史表';

CREATE TABLE file_assets (
                             id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
                             owner_user_id BIGINT UNSIGNED NULL COMMENT '所有者用户ID',
                             path VARCHAR(512) NOT NULL COMMENT '存储路径',
                             url VARCHAR(512) NOT NULL COMMENT '访问URL',
                             original_name VARCHAR(255) NULL COMMENT '原始文件名',
                             size_bytes BIGINT UNSIGNED NOT NULL COMMENT '文件大小（字节）',
                             mime_type VARCHAR(255) NOT NULL COMMENT 'MIME 类型',
                             sha256 CHAR(64) NOT NULL COMMENT '文件SHA256',
                             status ENUM('READY','UPLOADING','DELETED') NOT NULL DEFAULT 'READY' COMMENT '状态',
                             created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
                             UNIQUE KEY uk_file_sha (sha256),
                             CONSTRAINT fk_fa_owner FOREIGN KEY (owner_user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件资源索引表';

CREATE TABLE post_attachments (
                                  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
                                  post_id BIGINT UNSIGNED NOT NULL COMMENT '帖子ID',
                                  file_asset_id BIGINT UNSIGNED NOT NULL COMMENT '来源 file_assets.id（必须关联）',
                                  width INT NULL COMMENT '图片宽（像素）',
                                  height INT NULL COMMENT '图片高（像素）',
                                  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
                                  KEY idx_pa_post (post_id),
                                  KEY idx_pa_file_asset (file_asset_id),
                                  CONSTRAINT fk_pa_post FOREIGN KEY (post_id) REFERENCES posts(id) ON DELETE CASCADE,
                                  CONSTRAINT fk_pa_file_asset FOREIGN KEY (file_asset_id) REFERENCES file_assets(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='帖子附件表（仅图片，元数据从 file_assets 获取）';

CREATE TABLE comments (
                          id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
                          post_id BIGINT UNSIGNED NOT NULL COMMENT '帖子ID',
                          parent_id BIGINT UNSIGNED NULL COMMENT '父评论ID（多层：邻接表）',
                          author_id BIGINT UNSIGNED NOT NULL COMMENT '作者用户ID',
                          content TEXT NOT NULL COMMENT '评论内容',
                          status ENUM('VISIBLE','PENDING','HIDDEN','REJECTED') NOT NULL DEFAULT 'VISIBLE' COMMENT '评论状态',
                          is_deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '软删除标记',
                          metadata JSON NULL COMMENT '扩展元数据（JSON）',
                          created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
                          updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
                          KEY idx_c_post (post_id),
                          KEY idx_c_parent (parent_id),
                          CONSTRAINT fk_c_post FOREIGN KEY (post_id) REFERENCES posts(id) ON DELETE CASCADE,
                          CONSTRAINT fk_c_parent FOREIGN KEY (parent_id) REFERENCES comments(id) ON DELETE SET NULL,
                          CONSTRAINT fk_c_author FOREIGN KEY (author_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评论表（邻接表：parent_id 支持多层回复）';

-- 新增：评论层级闭包表，用于高效获取任意节点的祖先/后代与深度
CREATE TABLE comments_closure (
                                 ancestor_id BIGINT UNSIGNED NOT NULL COMMENT '祖先评论ID',
                                 descendant_id BIGINT UNSIGNED NOT NULL COMMENT '后代评论ID（含自身）',
                                 depth INT UNSIGNED NOT NULL COMMENT '祖先到后代的边数（自身为0）',
                                 PRIMARY KEY (ancestor_id, descendant_id),
                                 KEY idx_cc_descendant (descendant_id),
                                 KEY idx_cc_ancestor_depth (ancestor_id, depth, descendant_id),
                                 CONSTRAINT fk_cc_ancestor FOREIGN KEY (ancestor_id) REFERENCES comments(id) ON DELETE CASCADE,
                                 CONSTRAINT fk_cc_descendant FOREIGN KEY (descendant_id) REFERENCES comments(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评论闭包表（支持多层查询：整棵子树/路径/层级统计）';

-- 提示：插入一条新评论时，应用层需同时维护闭包表：
-- 1) INSERT INTO comments(...) VALUES(...);  -- 获得 new_id

CREATE TABLE reactions (
                           id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
                           user_id BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
                           target_type ENUM('POST','COMMENT') NOT NULL COMMENT '目标类型',
                           target_id BIGINT UNSIGNED NOT NULL COMMENT '目标ID',
                           type ENUM('LIKE','FAVORITE') NOT NULL COMMENT '互动类型',
                           created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
                           UNIQUE KEY uk_react (user_id, target_type, target_id, type),
                           KEY idx_react_target (target_type, target_id),
                           KEY idx_react_target_type (target_type, target_id, type),
                           KEY idx_react_user_type_created (user_id, target_type, type, created_at),
                           CONSTRAINT fk_react_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='互动表（点赞等）';

CREATE TABLE reports (
                         id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
                         reporter_id BIGINT UNSIGNED NOT NULL COMMENT '举报人用户ID',
                         target_type ENUM('POST','COMMENT','PROFILE') NOT NULL COMMENT '被举报对象类型',
                         target_id BIGINT UNSIGNED NOT NULL COMMENT '被举报对象ID',
                         reason_code VARCHAR(64) NOT NULL COMMENT '举报原因编码',
                         reason_text VARCHAR(255) NULL COMMENT '举报详细原因',
                         status ENUM('PENDING','REVIEWING','RESOLVED','REJECTED') NOT NULL DEFAULT 'PENDING' COMMENT '处理状态',
                         handled_by BIGINT UNSIGNED NULL COMMENT '处理人用户ID',
                         handled_at DATETIME(3) NULL COMMENT '处理时间',
                         resolution VARCHAR(255) NULL COMMENT '处理结果说明',
                         created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
                         KEY idx_rep_target (target_type, target_id),
                         CONSTRAINT fk_rep_reporter FOREIGN KEY (reporter_id) REFERENCES users(id),
                         CONSTRAINT fk_rep_handler FOREIGN KEY (handled_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='举报表';

CREATE TABLE tags (
                      id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
                      tenant_id BIGINT UNSIGNED NULL COMMENT '租户ID',
                      type ENUM('TOPIC','LANGUAGE','RISK','SYSTEM') NOT NULL COMMENT '标签类型',
                      name VARCHAR(64) NOT NULL COMMENT '标签名称',
                      slug VARCHAR(96) NOT NULL COMMENT '标签唯一标识（Slug）',
                      description VARCHAR(255) NULL COMMENT '标签描述',
                      is_system TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否系统标签',
                      is_active TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否可用',
                      threshold DOUBLE NULL COMMENT '风险阈值',
                      created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
                      UNIQUE KEY uk_tag (tenant_id, type, slug),
                      CONSTRAINT fk_tags_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='标签表';

CREATE TABLE post_tags (
                           post_id BIGINT UNSIGNED NOT NULL COMMENT '帖子ID',
                           tag_id BIGINT UNSIGNED NOT NULL COMMENT '标签ID',
                           source ENUM('MANUAL','AUTO','LLM','RULE') NOT NULL DEFAULT 'MANUAL' COMMENT '来源',
                           confidence DECIMAL(5,4) NULL COMMENT '置信度',
                           created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
                           PRIMARY KEY (post_id, tag_id, source),
                           CONSTRAINT fk_pt_post FOREIGN KEY (post_id) REFERENCES posts(id) ON DELETE CASCADE,
                           CONSTRAINT fk_pt_tag FOREIGN KEY (tag_id) REFERENCES tags(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='帖子-标签关联表';

CREATE TABLE hot_scores (
                            post_id BIGINT UNSIGNED PRIMARY KEY COMMENT '帖子ID',
                            score_24h DOUBLE NOT NULL DEFAULT 0 COMMENT '24小时热度分',
                            score_7d DOUBLE NOT NULL DEFAULT 0 COMMENT '7天热度分',
                            score_all DOUBLE NOT NULL DEFAULT 0 COMMENT '累计热度分',
                            decay_base DOUBLE NOT NULL DEFAULT 0.85 COMMENT '衰减基数',
                            last_recalculated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '最后重算时间',
                            CONSTRAINT fk_hot_post FOREIGN KEY (post_id) REFERENCES posts(id) ON DELETE CASCADE,
                            CONSTRAINT chk_hs_decay CHECK (decay_base >= 0 AND decay_base <= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='热度分缓存表';

-- 语义与 RAG
CREATE TABLE documents (
                           id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
                           tenant_id BIGINT UNSIGNED NULL COMMENT '租户ID',
                           source_type ENUM('POST','COMMENT','FILE','URL') NOT NULL COMMENT '来源类型',
                           source_id BIGINT UNSIGNED NULL COMMENT '来源对象ID',
                           title VARCHAR(191) NOT NULL COMMENT '文档标题',
                           language VARCHAR(16) NULL COMMENT '语言标记',
                           version INT NOT NULL DEFAULT 1 COMMENT '版本号',
                           is_active TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否有效',
                           metadata JSON NULL COMMENT '元数据（JSON）',
                           created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
                           KEY idx_doc_source (source_type, source_id),
                           CONSTRAINT fk_docs_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='统一文档视图表';

CREATE TABLE document_chunks (
                                 id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
                                 document_id BIGINT UNSIGNED NOT NULL COMMENT '文档ID',
                                 chunk_index INT NOT NULL COMMENT '分片序号（从0开始）',
                                 content_text TEXT NOT NULL COMMENT '分片文本内容',
                                 content_tokens INT NULL COMMENT '分片Token计数',
                                 embedding_provider VARCHAR(64) NULL COMMENT '嵌入提供方/模型',
                                 embedding_dim INT NULL COMMENT '嵌入维度',
                                 embedding_vector BLOB NULL COMMENT '向量数据（可选）',
                                 content_hash CHAR(64) NOT NULL COMMENT '内容哈希（去重）',
                                 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
                                 FULLTEXT KEY ft_chunk_text (content_text),
                                 UNIQUE KEY uk_chunk_doc_idx (document_id, chunk_index),
                                 KEY idx_chunk_hash (content_hash),
                                 CONSTRAINT fk_chunk_doc FOREIGN KEY (document_id) REFERENCES documents(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档分片与嵌入表';

CREATE TABLE vector_indices (
                                id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
                                provider ENUM('FAISS','MILVUS','OTHER') NOT NULL COMMENT '向量引擎',
                                collection_name VARCHAR(128) NOT NULL COMMENT '集合/索引名称',
                                metric VARCHAR(32) NOT NULL COMMENT '相似度度量（如 cosine）',
                                dim INT NOT NULL COMMENT '向量维度',
                                status ENUM('READY','BUILDING','ERROR') NOT NULL DEFAULT 'READY' COMMENT '状态',
                                metadata JSON NULL COMMENT '元数据（JSON）',
                                UNIQUE KEY uk_vi (provider, collection_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='向量索引元信息表';

CREATE TABLE prompts (
                         id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
                         prompt_code VARCHAR(128) NOT NULL COMMENT '提示词编码',
                         name VARCHAR(128) NOT NULL COMMENT '提示词名称',
                         system_prompt TEXT NULL COMMENT '系统提示词',
                         user_prompt_template TEXT NULL COMMENT '用户提示词模板',
                         model_name VARCHAR(128) NULL COMMENT '模型名称',
                         provider_id VARCHAR(64) NULL COMMENT 'ProviderId',
                         temperature DECIMAL(4,3) NULL COMMENT '温度',
                         top_p DECIMAL(4,3) NULL COMMENT 'Top-P',
                         max_tokens INT NULL COMMENT '最大输出Token',
                         enable_deep_thinking TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否启用深度思考',
                         vision_model VARCHAR(128) NULL COMMENT '视觉模型名称',
                         vision_provider_id VARCHAR(64) NULL COMMENT '视觉ProviderId',
                         vision_temperature DECIMAL(4,3) NULL COMMENT '视觉温度',
                         vision_top_p DECIMAL(4,3) NULL COMMENT '视觉Top-P',
                         vision_max_tokens INT NULL COMMENT '视觉最大输出Token',
                         vision_enable_deep_thinking TINYINT(1) NOT NULL DEFAULT 0 COMMENT '视觉深度思考',
                         wait_files_seconds INT NULL DEFAULT 60 COMMENT '等待文件上传秒数',
                         vision_image_token_budget INT NULL DEFAULT 50000 COMMENT '视觉图像token预算',
                         vision_max_images_per_request INT NULL DEFAULT 10 COMMENT '单请求最大图片数',
                         vision_high_resolution_images TINYINT(1) NULL DEFAULT 0 COMMENT '是否启用高分辨率图像',
                         vision_max_pixels INT NULL DEFAULT 2621440 COMMENT '单图最大像素',
                         is_active TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
                         version INT NOT NULL DEFAULT 1 COMMENT '版本号',
                         updated_by BIGINT UNSIGNED NULL COMMENT '更新人',
                         variables JSON NULL COMMENT '模板变量定义',
                         created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
                         updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
                         UNIQUE KEY uk_prompts_code (prompt_code),
                         CONSTRAINT fk_prompts_updated_by FOREIGN KEY (updated_by) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='提示词配置表';

CREATE TABLE generation_jobs (
                                 id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
                                 job_type ENUM('TITLE','TAGS','SUMMARY','TRANSLATE','SUGGESTION','POST_COMPOSE') NOT NULL COMMENT '生成任务类型',
                                 target_type ENUM('POST','COMMENT') NOT NULL COMMENT '目标类型',
                                 target_id BIGINT UNSIGNED NOT NULL COMMENT '目标ID',
                                 status ENUM('PENDING','RUNNING','SUCCEEDED','FAILED') NOT NULL DEFAULT 'PENDING' COMMENT '任务状态',
                                 prompt_code VARCHAR(128) NULL COMMENT '使用的提示词编码',
                                 model VARCHAR(128) NULL COMMENT '使用的模型名称',
                                 provider_id VARCHAR(64) NULL COMMENT 'ProviderId（提供方ID）',
                                 temperature DECIMAL(4,3) NULL COMMENT '温度',
                                 top_p DECIMAL(4,3) NULL COMMENT 'Top-P',
                                 latency_ms BIGINT NULL COMMENT '延迟(ms)',
                                 prompt_version INT NULL COMMENT '提示词版本',
                                 params JSON NULL COMMENT '任务参数（JSON）',
                                 result_json JSON NULL COMMENT '生成结果（JSON）',
                                 tokens_in INT NULL COMMENT '输入Token数',
                                 tokens_out INT NULL COMMENT '输出Token数',
                                 cost_cents INT NULL COMMENT '成本（分）',
                                 error_message VARCHAR(255) NULL COMMENT '错误信息',
                                 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
                                 updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
                                 KEY idx_gj_target (target_type, target_id),
                                 KEY idx_gj_created_at (created_at),
                                 CONSTRAINT fk_gj_prompt FOREIGN KEY (prompt_code) REFERENCES prompts(prompt_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='生成任务记录表';

CREATE TABLE qa_sessions (
                             id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
                             user_id BIGINT UNSIGNED NULL COMMENT '会话所属用户ID',
                             title VARCHAR(191) NULL COMMENT '会话标题',
                             context_strategy ENUM('RECENT_N','SUMMARIZE','NONE') NOT NULL DEFAULT 'RECENT_N' COMMENT '上下文保留策略',
                             is_active TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否有效',
                             created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
                             KEY idx_qs_user_created (user_id, created_at),
                             FULLTEXT KEY ft_qs_title (title),
                             CONSTRAINT fk_qs_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='问答会话表';

CREATE TABLE retrieval_events (
                                  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
                                  user_id BIGINT UNSIGNED NULL COMMENT '触发检索的用户ID',
                                  session_id BIGINT UNSIGNED NULL COMMENT '会话ID',
                                  query_text TEXT NOT NULL COMMENT '查询文本',
                                  bm25_k INT NULL COMMENT 'BM25 召回 TopK',
                                  hybrid_k INT NULL COMMENT '融合后保留 TopK',
                                  rerank_model VARCHAR(64) NULL COMMENT '重排模型',
                                  rerank_k INT NULL COMMENT '重排TopK',
                                  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
                                  KEY idx_re_created_at (created_at),
                                  KEY idx_re_session_id (session_id),
                                  CONSTRAINT fk_re_user FOREIGN KEY (user_id) REFERENCES users(id),
                                  CONSTRAINT fk_re_session FOREIGN KEY (session_id) REFERENCES qa_sessions(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='检索事件表';

CREATE TABLE retrieval_hits (
                                id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
                                event_id BIGINT UNSIGNED NOT NULL COMMENT '检索事件ID',
                                `rank` INT NOT NULL COMMENT '排序名次',
                                hit_type ENUM('BM25','VEC','RERANK') NOT NULL COMMENT '命中类型',
                                post_id BIGINT UNSIGNED NULL COMMENT '帖子ID（关联 posts.id）',
                                chunk_id BIGINT UNSIGNED NULL COMMENT '分片ID',
                                score DOUBLE NOT NULL COMMENT '得分',
                                created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
                                CONSTRAINT fk_rh_event FOREIGN KEY (event_id) REFERENCES retrieval_events(id) ON DELETE CASCADE,
                                CONSTRAINT fk_rh_post FOREIGN KEY (post_id) REFERENCES posts(id) ON DELETE SET NULL,
                                CONSTRAINT fk_rh_chunk FOREIGN KEY (chunk_id) REFERENCES document_chunks(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='检索命中明细表';

CREATE TABLE context_windows (
                                 id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
                                 event_id BIGINT UNSIGNED NOT NULL COMMENT '检索事件ID',
                                 policy ENUM('TOPK','IMPORTANCE','DEDUP','HYBRID') NOT NULL COMMENT '裁剪策略',
                                 total_tokens INT NOT NULL COMMENT '上下文总Token数',
                                 chunk_ids JSON NOT NULL COMMENT '入选分片ID集合（JSON）',
                                 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
                                 CONSTRAINT fk_cw_event FOREIGN KEY (event_id) REFERENCES retrieval_events(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='动态上下文窗口表';

-- 审核流水线
CREATE TABLE moderation_rules (
                                  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
                                  name VARCHAR(96) NOT NULL COMMENT '规则名称',
                                  type ENUM('KEYWORD','REGEX','URL','PATTERN') NOT NULL COMMENT '规则类型',
                                  pattern TEXT NOT NULL COMMENT '匹配表达式/模式',
                                  severity ENUM('LOW','MEDIUM','HIGH') NOT NULL DEFAULT 'LOW' COMMENT '严重级别',
                                  enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
                                  metadata JSON NULL COMMENT '元数据（JSON）',
                                  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审核规则表';

CREATE TABLE moderation_rule_hits (
                                      id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
                                      content_type ENUM('POST','COMMENT','PROFILE') NOT NULL COMMENT '内容类型',
                                      content_id BIGINT UNSIGNED NOT NULL COMMENT '内容ID',
                                      snippet VARCHAR(255) NULL COMMENT '命中文本片段',
                                      matched_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '命中时间',
                                      KEY idx_mrh_target (content_type, content_id),
                                      CONSTRAINT fk_mrh_rule FOREIGN KEY (rule_id) REFERENCES moderation_rules(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='规则命中日志表';

CREATE TABLE moderation_similar_hits (
                                         id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
                                         content_type ENUM('POST','COMMENT','PROFILE') NOT NULL COMMENT '内容类型',
                                         content_id BIGINT UNSIGNED NOT NULL COMMENT '内容ID',
                                         candidate_id BIGINT UNSIGNED NULL COMMENT '相似样本ID/参考ID',
                                         distance DOUBLE NOT NULL COMMENT '相似距离',
                                         threshold DOUBLE NOT NULL COMMENT '距离阈值',
                                         matched_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '命中时间',
                                         KEY idx_msh_target (content_type, content_id),
                                         KEY idx_msh_matched_at (matched_at),
                                         KEY idx_msh_candidate (candidate_id, matched_at),
                                         CONSTRAINT fk_msh_candidate FOREIGN KEY (candidate_id) REFERENCES moderation_samples(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='相似度命中日志表';

CREATE TABLE moderation_queue (
                                  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
                                  case_type ENUM('CONTENT','REPORT') NOT NULL DEFAULT 'CONTENT' COMMENT '案件类型',
                                  review_stage VARCHAR(16) NULL COMMENT '复审场景：default|reported|appeal',
                                  content_type ENUM('POST','COMMENT','PROFILE') NOT NULL COMMENT '内容类型',
                                  content_id BIGINT UNSIGNED NOT NULL COMMENT '内容ID',
                                  status ENUM('PENDING','REVIEWING','HUMAN','APPROVED','REJECTED') NOT NULL DEFAULT 'PENDING' COMMENT '队列状态',
                                  current_stage ENUM('RULE','VEC','LLM','HUMAN') NOT NULL DEFAULT 'RULE' COMMENT '当前阶段',
                                  priority INT NOT NULL DEFAULT 0 COMMENT '优先级',
                                  assigned_to BIGINT UNSIGNED NULL COMMENT '指派审核员用户ID',
                                  locked_by VARCHAR(64) NULL COMMENT '自动审核锁持有者（实例ID）',
                                  locked_at DATETIME(3) NULL COMMENT '自动审核锁时间（租约开始）',
                                  finished_at DATETIME(3) NULL COMMENT '到达终态/转人工的时间',
  version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
                                  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
                                  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
                                  UNIQUE KEY uk_mq_case_target (case_type, content_type, content_id),
                                  KEY idx_mq_status_stage (status, current_stage, priority, created_at),
                                  KEY idx_mq_status_assignee (status, assigned_to),
                                  KEY idx_mq_locked_at (locked_at),
                                  CONSTRAINT fk_mq_assignee FOREIGN KEY (assigned_to) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审核队列表（单一真源）';

CREATE TABLE moderation_actions (
                                    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
                                    queue_id BIGINT UNSIGNED NOT NULL COMMENT '所属队列ID',
                                    actor_user_id BIGINT UNSIGNED NULL COMMENT '操作者用户ID',
                                    action ENUM('APPROVE','REJECT','ESCALATE','NOTE') NOT NULL COMMENT '动作类型',
                                    reason VARCHAR(255) NULL COMMENT '理由/备注',
                                    snapshot JSON NULL COMMENT '快照（当时数据）',
                                    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
                                    CONSTRAINT fk_ma_queue FOREIGN KEY (queue_id) REFERENCES moderation_queue(id) ON DELETE CASCADE,
                                    CONSTRAINT fk_ma_actor FOREIGN KEY (actor_user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审核操作记录表';

CREATE TABLE risk_labeling (
                               id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
                               target_type ENUM('POST','COMMENT','PROFILE') NOT NULL COMMENT '目标类型',
                               target_id BIGINT UNSIGNED NOT NULL COMMENT '目标ID',
                               tag_id BIGINT UNSIGNED NOT NULL COMMENT '风险标签ID',
                               source ENUM('LLM','RULE','HUMAN') NOT NULL COMMENT '来源',
                               confidence DECIMAL(5,4) NULL COMMENT '置信度',
                               created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
                               UNIQUE KEY uk_rl (target_type, target_id, tag_id, source),
                               CONSTRAINT fk_rl_tag FOREIGN KEY (tag_id) REFERENCES tags(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='风险标签关联表';

CREATE TABLE qa_messages (
                             id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
                             session_id BIGINT UNSIGNED NOT NULL COMMENT '会话ID',
                             role ENUM('USER','ASSISTANT','SYSTEM') NOT NULL COMMENT '消息角色',
                             content LONGTEXT NOT NULL COMMENT '消息内容',
                             model VARCHAR(64) NULL COMMENT '生成模型（若为AI）',
                             tokens_in INT NULL COMMENT '输入Token',
                             tokens_out INT NULL COMMENT '输出Token',
                             is_favorite BOOLEAN DEFAULT FALSE NOT NULL COMMENT '是否收藏',
                             created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
                             KEY idx_qm_session_created (session_id, created_at),
                             FULLTEXT KEY ft_qm_content (content),
                             CONSTRAINT fk_qm_session FOREIGN KEY (session_id) REFERENCES qa_sessions(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='问答消息表';

CREATE TABLE qa_turns (
                          id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
                          session_id BIGINT UNSIGNED NOT NULL COMMENT '会话ID',
                          question_message_id BIGINT UNSIGNED NOT NULL COMMENT '问题消息ID',
                          answer_message_id BIGINT UNSIGNED NULL COMMENT '答案消息ID',
                          latency_ms INT NULL COMMENT '问答延迟（毫秒）',
                          first_token_latency_ms INT NULL COMMENT '首字延迟（毫秒）',
                          context_window_id BIGINT UNSIGNED NULL COMMENT '使用的上下文窗口ID',
                          created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
                          CONSTRAINT fk_qt_session FOREIGN KEY (session_id) REFERENCES qa_sessions(id) ON DELETE CASCADE,
                          CONSTRAINT fk_qt_qm FOREIGN KEY (question_message_id) REFERENCES qa_messages(id),
                          CONSTRAINT fk_qt_am FOREIGN KEY (answer_message_id) REFERENCES qa_messages(id),
                          CONSTRAINT fk_qt_cw FOREIGN KEY (context_window_id) REFERENCES context_windows(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='问答轮次表';

CREATE TABLE answer_citations (
                                  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
                                  message_id BIGINT UNSIGNED NOT NULL COMMENT '答案消息ID',
                                  document_id BIGINT UNSIGNED NULL COMMENT '引用文档ID',
                                  chunk_id BIGINT UNSIGNED NULL COMMENT '引用分片ID',
                                  quote_text TEXT NULL COMMENT '引用片段文本',
                                  source_url VARCHAR(512) NULL COMMENT '来源URL（可选）',
                                  start_offset INT NULL COMMENT '片段起始偏移',
                                  end_offset INT NULL COMMENT '片段结束偏移',
                                  score DOUBLE NULL COMMENT '相关性得分',
                                  CONSTRAINT fk_ac_msg FOREIGN KEY (message_id) REFERENCES qa_messages(id) ON DELETE CASCADE,
                                  CONSTRAINT fk_ac_doc FOREIGN KEY (document_id) REFERENCES documents(id) ON DELETE SET NULL,
                                  CONSTRAINT fk_ac_chunk FOREIGN KEY (chunk_id) REFERENCES document_chunks(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='答案引用与溯源表';

-- 评估与监控
CREATE TABLE metrics_events (
                                id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
                                name VARCHAR(96) NOT NULL COMMENT '指标名称',
                                tags JSON NULL COMMENT '标签（JSON）',
                                value DOUBLE NOT NULL COMMENT '数值',
                                ts DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '时间戳',
                                KEY idx_metrics_name_ts (name, ts)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通用指标事件表';

CREATE TABLE rag_eval_runs (
                               id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
                               name VARCHAR(96) NOT NULL COMMENT '评测批次名称',
                               config JSON NULL COMMENT '评测配置（JSON）',
                               is_baseline TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否基线',
                               created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
                               UNIQUE KEY uk_eval_run_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RAG 评测批次表';

CREATE TABLE rag_eval_samples (
                                  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
                                  run_id BIGINT UNSIGNED NOT NULL COMMENT '评测批次ID',
                                  query TEXT NOT NULL COMMENT '问题/查询',
                                  expected_answer TEXT NULL COMMENT '参考答案（可选）',
                                  references_json JSON NULL COMMENT '参考引用集合（JSON）',
                                  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
                                  CONSTRAINT fk_res_run FOREIGN KEY (run_id) REFERENCES rag_eval_runs(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RAG 评测样本表';

CREATE TABLE rag_eval_results (
                                  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
                                  run_id BIGINT UNSIGNED NOT NULL COMMENT '评测批次ID',
                                  sample_id BIGINT UNSIGNED NOT NULL COMMENT '样本ID',
                                  em DOUBLE NULL COMMENT 'EM 指标',
                                  f1 DOUBLE NULL COMMENT 'F1 指标',
                                  hit_rate DOUBLE NULL COMMENT '命中率',
                                  latency_ms INT NULL COMMENT '延迟（毫秒）',
                                  tokens_in INT NULL COMMENT '输入Token',
                                  tokens_out INT NULL COMMENT '输出Token',
                                  cost_cents INT NULL COMMENT '成本（分）',
                                  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
                                  UNIQUE KEY uk_rer (run_id, sample_id),
                                  CONSTRAINT fk_rer_run FOREIGN KEY (run_id) REFERENCES rag_eval_runs(id) ON DELETE CASCADE,
                                  CONSTRAINT fk_rer_sample FOREIGN KEY (sample_id) REFERENCES rag_eval_samples(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RAG 评测结果表';

CREATE TABLE cost_records (
                              id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
                              scope ENUM('GEN','RERANK','MODERATION','OTHER') NOT NULL COMMENT '费用范围/用途',
                              model VARCHAR(64) NULL COMMENT '模型名称',
                              tokens_in INT NULL COMMENT '输入Token',
                              tokens_out INT NULL COMMENT '输出Token',
                              currency VARCHAR(8) NULL COMMENT '货币',
                              unit_price_in DECIMAL(10,6) NULL COMMENT '输入单价',
                              unit_price_out DECIMAL(10,6) NULL COMMENT '输出单价',
                              total_cost DECIMAL(10,4) NULL COMMENT '总费用',
                              ref_type VARCHAR(64) NULL COMMENT '关联对象类型',
                              ref_id BIGINT UNSIGNED NULL COMMENT '关联对象ID',
                              ts DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '时间戳',
                              KEY idx_cost_ref (ref_type, ref_id),
                              KEY idx_cost_ts (ts)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='调用成本明细表';

CREATE TABLE review_efficiency (
                                   id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
                                   window_start DATETIME(3) NOT NULL COMMENT '统计窗口开始',
                                   window_end DATETIME(3) NOT NULL COMMENT '统计窗口结束',
                                   total INT NOT NULL COMMENT '样本总数',
                                   human_share DECIMAL(5,4) NOT NULL COMMENT '人工参与占比',
                                   avg_latency_ms INT NOT NULL COMMENT '平均处理时延（毫秒）',
                                   created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审核效率统计表';

CREATE TABLE search_logs (
                             id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
                             user_id BIGINT UNSIGNED NULL COMMENT '用户ID',
                             query VARCHAR(512) NOT NULL COMMENT '搜索词',
                             latency_ms INT NULL COMMENT '搜索延迟（毫秒）',
                             results_count INT NULL COMMENT '结果数量',
                             created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
                             KEY idx_sl_user (user_id),
                             CONSTRAINT fk_sl_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='搜索日志表';

CREATE TABLE system_events (
                               id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
                               level ENUM('INFO','WARN','ERROR') NOT NULL COMMENT '事件级别',
                               category VARCHAR(64) NOT NULL COMMENT '分类',
                               message VARCHAR(255) NOT NULL COMMENT '消息',
                               extra JSON NULL COMMENT '额外信息（JSON）',
                               created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
                               KEY idx_se_cat_ts (category, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统事件表';

-- 通用辅助
CREATE TABLE notifications (
                               id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
                               user_id BIGINT UNSIGNED NOT NULL COMMENT '接收用户ID',
                               type VARCHAR(64) NOT NULL COMMENT '通知类型',
                               title VARCHAR(191) NOT NULL COMMENT '标题',
                               content TEXT NULL COMMENT '内容',
                               read_at DATETIME(3) NULL COMMENT '阅读时间',
                               created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
                               KEY idx_nt_user (user_id, read_at),
                               CONSTRAINT fk_nt_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='站内通知表';

CREATE TABLE user_settings (
                               id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
                               user_id BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
                               k VARCHAR(64) NOT NULL COMMENT '设置键',
                               v JSON NULL COMMENT '设置值（JSON）',
                               UNIQUE KEY uk_us (user_id, k),
                               CONSTRAINT fk_us_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户个性化设置表';

-- Post drafts are kept separate from posts to avoid mixing permissions and lists.
CREATE TABLE post_drafts (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    tenant_id BIGINT UNSIGNED NULL COMMENT '租户ID',
    board_id BIGINT UNSIGNED NOT NULL COMMENT '所属板块ID',
    author_id BIGINT UNSIGNED NOT NULL COMMENT '作者用户ID',
    title VARCHAR(191) NOT NULL DEFAULT '' COMMENT '草稿标题',
    content LONGTEXT NOT NULL COMMENT '草稿内容',
    content_format ENUM('PLAIN','MARKDOWN','HTML') NOT NULL DEFAULT 'MARKDOWN' COMMENT '内容格式',
                       content_length INT NOT NULL DEFAULT 0 COMMENT '内容长度',
                       is_chunked_review TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否分片审核',
                       chunk_threshold_chars INT NULL COMMENT '分片阈值',
                       chunking_strategy VARCHAR(16) NULL COMMENT '分片策略',
    metadata JSON NULL COMMENT '扩展元数据(JSON)，可存 tags/attachmentIds 等',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    KEY idx_pd_author_updated (author_id, updated_at),
    KEY idx_pd_board (board_id),
    CONSTRAINT fk_pd_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_pd_board FOREIGN KEY (board_id) REFERENCES boards(id),
    CONSTRAINT fk_pd_author FOREIGN KEY (author_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='帖子草稿箱(独立表)';

-- 新增：帖子按自然日聚合的浏览量表（用于热榜与数据展示）
-- 口径：Asia/Shanghai 自然日；每次浏览 +1（不去重）

CREATE TABLE post_views_daily (
    post_id BIGINT UNSIGNED NOT NULL COMMENT '帖子ID',
    day DATE NOT NULL COMMENT '自然日(YYYY-MM-DD)',
    view_count BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '当天浏览次数',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (post_id, day),
    KEY idx_pvd_day (day),
    CONSTRAINT fk_pvd_post FOREIGN KEY (post_id) REFERENCES posts(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='帖子浏览量-按天聚合表';

CREATE TABLE moderation_policy_config (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  content_type VARCHAR(16) NOT NULL COMMENT '内容类型',
  policy_version VARCHAR(64) NOT NULL COMMENT '策略版本',
  config_json JSON NOT NULL COMMENT '策略配置(JSON)',
  version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  updated_by BIGINT UNSIGNED NULL COMMENT '更新人用户ID',
  UNIQUE KEY uk_moderation_policy_config_content_type (content_type),
  CONSTRAINT fk_mpc_updated_by FOREIGN KEY (updated_by) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审核策略配置';

INSERT INTO moderation_policy_config (content_type, policy_version, config_json) VALUES
('POST', 'v1', '{
  "precheck": {
    "rule": {"enabled": true, "high_action": "REJECT", "medium_action": "REJECT", "low_action": "HUMAN"},
    "vec": {"enabled": true, "threshold": 0.2, "hit_action": "REJECT", "miss_action": "LLM"}
  },
  "thresholds": {
    "default": {"T_allow": 0.2, "T_reject": 0.8},
    "by_review_stage": {
      "reported": {"T_allow": 0.15, "T_reject": 0.75},
      "appeal": {"T_allow": 0.25, "T_reject": 0.85}
    }
  },
  "escalate_rules": {
    "require_evidence": true
  },
  "review_trigger": {
    "window_minutes": 10,
    "light": {"unique_reporters_min": 3, "total_reports_min": 5},
    "standard": {"unique_reporters_min": 5, "total_reports_min": 10, "velocity_min_per_window": 5, "trust_min": 0.7},
    "urgent": {"unique_reporters_min": 20, "total_reports_min": 20, "velocity_min_per_window": 20, "trust_min": 0.85}
  },
  "anti_spam": {
    "comment": {"window_seconds": 60, "max_per_author_per_window": 8, "similarity_threshold": 0.9, "max_similar_count_per_10min": 3},
    "profile": {"window_minutes": 60, "max_updates_per_window": 3, "max_updates_per_day": 5, "similarity_threshold": 0.9}
  }
}'),
('COMMENT', 'v1', '{
  "precheck": {
    "rule": {"enabled": true, "high_action": "REJECT", "medium_action": "REJECT", "low_action": "HUMAN"},
    "vec": {"enabled": true, "threshold": 0.2, "hit_action": "REJECT", "miss_action": "LLM"}
  },
  "thresholds": {
    "default": {"T_allow": 0.2, "T_reject": 0.8},
    "by_review_stage": {
      "reported": {"T_allow": 0.15, "T_reject": 0.75},
      "appeal": {"T_allow": 0.25, "T_reject": 0.85}
    }
  },
  "escalate_rules": {
    "require_evidence": true
  },
  "review_trigger": {
    "window_minutes": 10,
    "light": {"unique_reporters_min": 3, "total_reports_min": 5},
    "standard": {"unique_reporters_min": 5, "total_reports_min": 10, "velocity_min_per_window": 5, "trust_min": 0.7},
    "urgent": {"unique_reporters_min": 20, "total_reports_min": 20, "velocity_min_per_window": 20, "trust_min": 0.85}
  },
  "anti_spam": {
    "comment": {"window_seconds": 60, "max_per_author_per_window": 8, "similarity_threshold": 0.9, "max_similar_count_per_10min": 3},
    "profile": {"window_minutes": 60, "max_updates_per_window": 3, "max_updates_per_day": 5, "similarity_threshold": 0.9}
  }
}'),
('PROFILE', 'v1', '{
  "precheck": {
    "rule": {"enabled": true, "high_action": "REJECT", "medium_action": "REJECT", "low_action": "HUMAN"},
    "vec": {"enabled": true, "threshold": 0.2, "hit_action": "REJECT", "miss_action": "LLM"}
  },
  "thresholds": {
    "default": {"T_allow": 0.2, "T_reject": 0.8},
    "by_review_stage": {
      "reported": {"T_allow": 0.15, "T_reject": 0.75},
      "appeal": {"T_allow": 0.25, "T_reject": 0.85}
    }
  },
  "escalate_rules": {
    "require_evidence": true
  },
  "review_trigger": {
    "window_minutes": 10,
    "light": {"unique_reporters_min": 3, "total_reports_min": 5},
    "standard": {"unique_reporters_min": 5, "total_reports_min": 10, "velocity_min_per_window": 5, "trust_min": 0.7},
    "urgent": {"unique_reporters_min": 20, "total_reports_min": 20, "velocity_min_per_window": 20, "trust_min": 0.85}
  },
  "anti_spam": {
    "comment": {"window_seconds": 60, "max_per_author_per_window": 8, "similarity_threshold": 0.9, "max_similar_count_per_10min": 3},
    "profile": {"window_minutes": 60, "max_updates_per_window": 3, "max_updates_per_day": 5, "similarity_threshold": 0.9}
  }
}');

-- LLM moderation config (single-row upsert style)

CREATE TABLE moderation_llm_config (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  text_prompt_code VARCHAR(128) NULL COMMENT '文本审核提示词编码',
  vision_prompt_code VARCHAR(128) NULL COMMENT '视觉审核提示词编码',
  judge_prompt_code VARCHAR(128) NULL COMMENT '判决提示词编码',
  judge_upgrade_prompt_code VARCHAR(128) NULL COMMENT '判决升级提示词编码',
  
  auto_run TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否自动运行',
  
  version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  updated_by BIGINT UNSIGNED NULL COMMENT '更新人用户ID',
  CONSTRAINT fk_mlc_updated_by FOREIGN KEY (updated_by) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='LLM 审核配置（单行）';

CREATE INDEX idx_moderation_llm_config_updated_at ON moderation_llm_config(updated_at);

-- Moderation similar detection samples (authority in MySQL, indexed in Elasticsearch)
-- MySQL 8.0, InnoDB, utf8mb4

CREATE TABLE moderation_samples (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    category ENUM('AD_SAMPLE','HISTORY_VIOLATION') NOT NULL COMMENT '样本类别',
    ref_content_type ENUM('POST','COMMENT','PROFILE') NULL COMMENT '关联内容类型(可选)',
    ref_content_id BIGINT UNSIGNED NULL COMMENT '关联内容ID(可选)',
    raw_text LONGTEXT NOT NULL COMMENT '原始文本(回显/溯源)',
    normalized_text LONGTEXT NOT NULL COMMENT '规范化文本(去噪/截断后,用于embedding与检索)',
    text_hash VARCHAR(64) NOT NULL COMMENT '规范化文本hash(建议SHA-256 hex,用于去重/缓存)',
    risk_level INT NOT NULL DEFAULT 0 COMMENT '风险/违规则等级(0表示未知)',
    labels JSON NULL COMMENT '风险标签(JSON数组)',
    source ENUM('HUMAN','RULE','LLM','IMPORT') NOT NULL DEFAULT 'HUMAN' COMMENT '样本来源',
    enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    UNIQUE KEY uk_ms_text_hash (text_hash),
    KEY idx_ms_enabled (enabled, id),
    KEY idx_ms_category (category, enabled, id),
    KEY idx_ms_ref (ref_content_type, ref_content_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='相似检测样本库(权威源:MySQL; 检索:Elasticsearch)';

-- Moderation similarity (VEC) config (single-row upsert style)

CREATE TABLE moderation_similarity_config (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
  embedding_model VARCHAR(128) NULL COMMENT '向量模型名称',
  embedding_dims INT NOT NULL DEFAULT 0 COMMENT '向量维度',
  max_input_chars INT NOT NULL DEFAULT 0 COMMENT '最大输入字符数',
  default_top_k INT NOT NULL DEFAULT 5 COMMENT '默认TopK',
  default_threshold DOUBLE NOT NULL DEFAULT 0.15 COMMENT '默认阈值',
  default_num_candidates INT NOT NULL DEFAULT 0 COMMENT '默认候选数',
  version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  updated_by BIGINT UNSIGNED NULL COMMENT '更新人用户ID',
  CONSTRAINT fk_msc_updated_by FOREIGN KEY (updated_by) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审核相似检测配置（单行）';

CREATE INDEX idx_moderation_similarity_config_updated_at ON moderation_similarity_config(updated_at);

-- Moderation samples ES index default config (single-row upsert style)

CREATE TABLE moderation_samples_index_config (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  index_name VARCHAR(128) NOT NULL COMMENT '索引名称',
  ik_enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用IK分词',
  embedding_model VARCHAR(128) NULL COMMENT '向量模型名称',
  embedding_dims INT NOT NULL DEFAULT 0 COMMENT '向量维度',
  default_top_k INT NOT NULL DEFAULT 5 COMMENT '默认TopK',
  default_threshold DOUBLE NOT NULL DEFAULT 0.15 COMMENT '默认阈值',
  version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  updated_by BIGINT UNSIGNED NULL COMMENT '更新人用户ID',
  CONSTRAINT fk_msic_updated_by FOREIGN KEY (updated_by) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='相似检测样本库ES索引默认配置(单行)';

CREATE INDEX idx_moderation_samples_index_config_updated_at ON moderation_samples_index_config(updated_at);

-- Moderation confidence fallback config (single-row upsert style)

CREATE TABLE moderation_confidence_fallback_config (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  llm_enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT 'LLM层是否启用',
  llm_reject_threshold DECIMAL(6,4) NOT NULL DEFAULT 0.7500 COMMENT 'LLM拒绝阈值(>=则拒绝)',
  llm_human_threshold DECIMAL(6,4) NOT NULL DEFAULT 0.5000 COMMENT 'LLM人工阈值(介于人工与拒绝之间则转人工)',
  report_human_threshold INT NOT NULL DEFAULT 5 COMMENT '举报触发人工阈值',
  llm_text_risk_threshold DECIMAL(6,4) NOT NULL DEFAULT 0.8000 COMMENT '文本风险阈值',
  llm_image_risk_threshold DECIMAL(6,4) NOT NULL DEFAULT 0.3000 COMMENT '图片风险阈值',
  llm_strong_reject_threshold DECIMAL(6,4) NOT NULL DEFAULT 0.9500 COMMENT '强拒绝阈值',
  llm_strong_pass_threshold DECIMAL(6,4) NOT NULL DEFAULT 0.1000 COMMENT '强通过阈值',
  llm_cross_modal_threshold DECIMAL(6,4) NOT NULL DEFAULT 0.7500 COMMENT '跨模态一致性阈值',
  chunk_threshold_chars INT NOT NULL DEFAULT 20000,
  chunk_llm_reject_threshold DECIMAL(6,4) NOT NULL DEFAULT 0.7500,
  chunk_llm_human_threshold DECIMAL(6,4) NOT NULL DEFAULT 0.5000,
  thresholds_json JSON NULL,
  version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  updated_by BIGINT UNSIGNED NULL COMMENT '更新人用户ID',
  CONSTRAINT fk_mcfc_updated_by FOREIGN KEY (updated_by) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审核置信回退配置（单行）';

CREATE INDEX idx_moderation_confidence_fallback_updated_at ON moderation_confidence_fallback_config(updated_at);

-- Moderation pipeline trace tables: one run + step details for RULE -> VEC -> LLM

CREATE TABLE moderation_pipeline_run (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  queue_id BIGINT UNSIGNED NOT NULL COMMENT '审核队列ID',
  content_type VARCHAR(16) NOT NULL COMMENT '内容类型',
  content_id BIGINT UNSIGNED NOT NULL COMMENT '内容ID',

  status VARCHAR(16) NOT NULL COMMENT '状态(RUNNING/SUCCESS/FAIL)',
  final_decision VARCHAR(16) NULL COMMENT '最终裁决(APPROVE/REJECT/HUMAN)',

  trace_id VARCHAR(64) NOT NULL COMMENT '链路追踪ID',

  started_at DATETIME(3) NOT NULL COMMENT '开始时间',
  ended_at DATETIME(3) NULL COMMENT '结束时间',
  total_ms BIGINT NULL COMMENT '总耗时(ms)',

  error_code VARCHAR(64) NULL COMMENT '错误码',
  error_message VARCHAR(512) NULL COMMENT '错误信息',

  llm_model VARCHAR(128) NULL COMMENT '使用的LLM模型',

  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',

  UNIQUE KEY uk_moderation_pipeline_run_trace_id (trace_id),
  KEY idx_moderation_pipeline_run_queue_id (queue_id),
  KEY idx_moderation_pipeline_run_content (content_type, content_id),
  KEY idx_moderation_pipeline_run_created_at (created_at),
  CONSTRAINT fk_mpr_queue FOREIGN KEY (queue_id) REFERENCES moderation_queue(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审核流水线运行记录';

CREATE TABLE moderation_pipeline_step (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  run_id BIGINT UNSIGNED NOT NULL COMMENT '运行记录ID',

  stage VARCHAR(16) NOT NULL COMMENT '阶段(RULE/VEC/LLM)',
  step_order INT NOT NULL COMMENT '步骤序号',

  decision VARCHAR(32) NULL COMMENT '裁决(PASS/HIT/MISS/APPROVE/REJECT/HUMAN/SKIP/ERROR)',
  score DECIMAL(10,6) NULL COMMENT '得分',
  threshold DECIMAL(10,6) NULL COMMENT '阈值',

  details_json JSON NULL COMMENT '详情(JSON)',

  started_at DATETIME(3) NOT NULL COMMENT '开始时间',
  ended_at DATETIME(3) NULL COMMENT '结束时间',
  cost_ms BIGINT NULL COMMENT '耗时(ms)',

  error_code VARCHAR(64) NULL COMMENT '错误码',
  error_message VARCHAR(512) NULL COMMENT '错误信息',

  UNIQUE KEY uk_moderation_pipeline_step_run_stage (run_id, stage),
  KEY idx_moderation_pipeline_step_run_id (run_id),
  CONSTRAINT fk_moderation_pipeline_step_run_id FOREIGN KEY (run_id) REFERENCES moderation_pipeline_run(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审核流水线步骤明细';

-- Global application settings (DB-backed, runtime configurable from admin UI).

CREATE TABLE app_settings (
  k VARCHAR(64) NOT NULL COMMENT '设置键',
  v LONGTEXT NOT NULL COMMENT '设置值(字符串)',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (k)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='应用级设置（全局）';

CREATE TABLE system_configurations (
  config_key VARCHAR(255) NOT NULL COMMENT '配置键',
  config_value VARCHAR(2048) NULL COMMENT '配置值',
  is_encrypted BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否加密存储',
  description VARCHAR(255) NULL COMMENT '描述',
  PRIMARY KEY (config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统配置（可加密存储）';

CREATE TABLE ai_gen_task_config (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  group_code VARCHAR(64) NOT NULL COMMENT '配置分组/任务族',
  sub_type VARCHAR(32) NOT NULL DEFAULT 'DEFAULT' COMMENT '子类型(如 TITLE/TOPIC_TAG)',

  enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
  prompt_code VARCHAR(128) NOT NULL COMMENT '提示词模板',

  default_count INT NOT NULL DEFAULT 5 COMMENT '默认生成数量(可选)',
  max_count INT NOT NULL DEFAULT 10 COMMENT '最大生成数量(可选)',
  max_content_chars INT NOT NULL DEFAULT 8000 COMMENT '最大内容字符数',
  allowed_target_langs LONGTEXT NULL COMMENT '前台可选的目标语言列表（JSON 数组，可选）',

  history_enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否记录历史(可选)',
  history_keep_days INT NULL COMMENT '历史保留天数(可选)',
  history_keep_rows INT NULL COMMENT '历史保留条数(可选)',

  version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  updated_by BIGINT UNSIGNED NULL COMMENT '更新人用户ID',
  label VARCHAR(128) NULL COMMENT '显示名称',
  category VARCHAR(32) NULL COMMENT '类别(TEXT_GEN/EMBEDDING/RERANK)',
  sort_index INT NOT NULL DEFAULT 0 COMMENT '排序索引',

  UNIQUE KEY uk_ai_gen_task_config_group_sub (group_code, sub_type),
  KEY idx_ai_gen_task_config_group_enabled (group_code, enabled),
  KEY idx_ai_gen_task_config_updated_at (updated_at),
  CONSTRAINT fk_agtc_updated_by FOREIGN KEY (updated_by) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='生成/翻译/摘要/标签等通用配置';

CREATE TABLE post_suggestion_gen_history (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  kind ENUM('TITLE','TOPIC_TAG') NOT NULL COMMENT '历史类型',
  user_id BIGINT UNSIGNED NULL COMMENT '用户ID',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  board_name VARCHAR(128) NULL COMMENT '板块名称',
  title_excerpt VARCHAR(256) NULL COMMENT '标题摘要',
  input_tags_json JSON NULL COMMENT '输入标签(JSON)',
  requested_count INT NOT NULL COMMENT '请求生成数量',
  applied_max_content_chars INT NOT NULL COMMENT '实际使用的最大字符数',
  content_len INT NOT NULL COMMENT '内容长度',
  content_excerpt VARCHAR(512) NULL COMMENT '内容摘要',
  output_json JSON NOT NULL COMMENT '生成结果(JSON)',
  job_id BIGINT UNSIGNED NULL COMMENT '关联 generation_jobs.id',
  KEY idx_post_suggestion_gen_history_created_at (created_at),
  KEY idx_post_suggestion_gen_history_user_id_created_at (user_id, created_at),
  KEY idx_post_suggestion_gen_history_kind_created_at (kind, created_at),
  CONSTRAINT fk_psugh_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL,
  CONSTRAINT fk_psugh_job FOREIGN KEY (job_id) REFERENCES generation_jobs(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='帖子建议生成历史记录（标题/主题标签）';

CREATE TABLE qa_message_sources (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    message_id BIGINT UNSIGNED NOT NULL COMMENT '答案消息ID',
    source_index INT NOT NULL COMMENT '来源序号（从 1 开始）',
    post_id BIGINT UNSIGNED NULL COMMENT '帖子ID（RAG 命中来源）',
    chunk_index INT NULL COMMENT '分片序号（RAG 命中来源）',
    score DOUBLE NULL COMMENT '相关性得分',
    title VARCHAR(512) NULL COMMENT '来源标题（可选）',
    url VARCHAR(512) NULL COMMENT '来源URL（可选）',
    UNIQUE KEY uk_qms_msg_idx (message_id, source_index),
    KEY idx_qms_msg (message_id),
    CONSTRAINT fk_qms_msg FOREIGN KEY (message_id) REFERENCES qa_messages(id) ON DELETE CASCADE,
    CONSTRAINT fk_qms_post FOREIGN KEY (post_id) REFERENCES posts(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='问答答案来源（RAG 溯源）表';

CREATE TABLE semantic_translate_history (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  user_id BIGINT UNSIGNED NULL COMMENT '用户ID',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',

  source_type VARCHAR(16) NOT NULL COMMENT '源类型(POST/COMMENT)',
  source_id BIGINT NOT NULL COMMENT '源ID',
  target_lang VARCHAR(32) NOT NULL COMMENT '目标语言',

  source_hash CHAR(64) NOT NULL COMMENT '源内容Hash',
  config_hash CHAR(64) NOT NULL COMMENT '配置Hash',

  source_title_excerpt VARCHAR(160) NULL COMMENT '源标题摘要',
  source_content_excerpt VARCHAR(512) NULL COMMENT '源内容摘要',

  translated_title VARCHAR(512) NULL COMMENT '翻译后标题',
  translated_markdown LONGTEXT NOT NULL COMMENT '翻译后正文(Markdown)',

  job_id BIGINT UNSIGNED NULL COMMENT '关联 generation_jobs.id',
  CONSTRAINT fk_sth_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL,
  CONSTRAINT fk_sth_job FOREIGN KEY (job_id) REFERENCES generation_jobs(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='翻译历史记录（带缓存键）';

CREATE UNIQUE INDEX uq_translate_cache_key
  ON semantic_translate_history(source_type, source_id, target_lang, source_hash, config_hash);

CREATE INDEX idx_translate_history_created_at ON semantic_translate_history(created_at);
CREATE INDEX idx_translate_history_user_id_created_at ON semantic_translate_history(user_id, created_at);

-- Supported languages for translate preferences and admin config
-- MySQL 8.0, InnoDB, utf8mb4

CREATE TABLE supported_languages (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  language_code VARCHAR(32) NOT NULL COMMENT '语言代码（BCP-47 / ISO）',
  display_name VARCHAR(128) NOT NULL COMMENT '显示名称（UI 展示）',
  native_name VARCHAR(128) NULL COMMENT '本地语言名称（可选）',
  is_active TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
  sort_order INT NOT NULL DEFAULT 0 COMMENT '排序顺序',
  UNIQUE KEY uk_supported_languages_code (language_code),
  KEY idx_supported_languages_active_sort (is_active, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支持语言表';

-- Post AI summary config + history + per-post summary state (admin-managed, DB-backed)

CREATE TABLE post_summary_gen_history (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  actor_user_id BIGINT UNSIGNED NULL COMMENT '操作者用户ID',
  post_id BIGINT UNSIGNED NOT NULL COMMENT '帖子ID',
  status VARCHAR(16) NOT NULL COMMENT '状态(SUCCESS/FAILED)',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  applied_max_content_chars INT NOT NULL COMMENT '实际使用的最大字符数',
  error_message LONGTEXT NULL COMMENT '错误信息',
  job_id BIGINT UNSIGNED NULL COMMENT '关联 generation_jobs.id',
  CONSTRAINT fk_psgh_post FOREIGN KEY (post_id) REFERENCES posts(id) ON DELETE CASCADE,
  CONSTRAINT fk_psgh_actor FOREIGN KEY (actor_user_id) REFERENCES users(id) ON DELETE SET NULL,
  CONSTRAINT fk_psgh_job FOREIGN KEY (job_id) REFERENCES generation_jobs(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='帖子摘要生成日志';

CREATE INDEX idx_post_summary_gen_history_created_at ON post_summary_gen_history(created_at);
CREATE INDEX idx_post_summary_gen_history_post_id_created_at ON post_summary_gen_history(post_id, created_at);

CREATE TABLE post_ai_summary (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  post_id BIGINT UNSIGNED NOT NULL COMMENT '帖子ID',
  status VARCHAR(16) NOT NULL COMMENT '状态(SUCCESS/FAILED/PENDING)',
  summary_title VARCHAR(512) NULL COMMENT '摘要标题',
  summary_text LONGTEXT NULL COMMENT '摘要正文',
  applied_max_content_chars INT NULL COMMENT '实际使用的最大字符数',
  generated_at DATETIME(3) NULL COMMENT '生成时间',
  error_message LONGTEXT NULL COMMENT '错误信息',
  job_id BIGINT UNSIGNED NULL COMMENT '关联 generation_jobs.id',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  CONSTRAINT fk_pas_post FOREIGN KEY (post_id) REFERENCES posts(id) ON DELETE CASCADE,
  CONSTRAINT fk_pas_job FOREIGN KEY (job_id) REFERENCES generation_jobs(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='帖子当前AI摘要状态';

CREATE UNIQUE INDEX uq_post_ai_summary_post_id ON post_ai_summary(post_id);
CREATE INDEX idx_post_ai_summary_updated_at ON post_ai_summary(updated_at);

-- LLM provider/model config tables (admin-managed, DB-backed)

CREATE TABLE llm_provider_settings (
  env VARCHAR(32) NOT NULL DEFAULT 'default' COMMENT '环境',
  active_provider_id VARCHAR(64) NULL COMMENT '当前启用的ProviderId',
  version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  updated_by BIGINT UNSIGNED NULL COMMENT '更新人用户ID',
  PRIMARY KEY (env),
  CONSTRAINT fk_lps_updated_by FOREIGN KEY (updated_by) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='LLM Provider 设置（如 activeProviderId）';

CREATE TABLE llm_providers (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  env VARCHAR(32) NOT NULL DEFAULT 'default' COMMENT '环境',
  provider_id VARCHAR(64) NOT NULL COMMENT 'ProviderId（提供方ID）',
  name VARCHAR(128) NULL COMMENT '名称',
  type VARCHAR(32) NOT NULL DEFAULT 'OPENAI_COMPAT' COMMENT 'Provider类型',
  base_url VARCHAR(512) NULL COMMENT '基础URL',
  api_key_encrypted VARBINARY(2048) NULL COMMENT 'API Key 密文',
  extra_headers_encrypted LONGBLOB NULL COMMENT '额外请求头密文',
  connect_timeout_ms INT NULL COMMENT '连接超时(ms)',
  read_timeout_ms INT NULL COMMENT '读取超时(ms)',
  
  enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
  priority INT NOT NULL DEFAULT 0 COMMENT '优先级',
  default_chat_model VARCHAR(128) NULL COMMENT '默认对话模型',
  default_embedding_model VARCHAR(128) NULL COMMENT '默认向量模型',
  metadata JSON NULL COMMENT '扩展元数据(JSON)',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  created_by BIGINT UNSIGNED NULL COMMENT '创建人用户ID',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  updated_by BIGINT UNSIGNED NULL COMMENT '更新人用户ID',
  UNIQUE KEY uk_llm_providers_env_provider (env, provider_id),
  CONSTRAINT fk_llm_prov_created_by FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL,
  CONSTRAINT fk_llm_prov_updated_by FOREIGN KEY (updated_by) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='LLM Provider 配置（含密钥密文）';

CREATE INDEX idx_llm_providers_env_enabled_priority ON llm_providers(env, enabled, priority);

CREATE TABLE llm_price_configs (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  name VARCHAR(128) NOT NULL COMMENT '配置名称',
  currency VARCHAR(16) NOT NULL DEFAULT 'CNY' COMMENT '货币',
  input_cost_per_1k DECIMAL(18,8) NULL COMMENT '每1K输入Token成本',
  output_cost_per_1k DECIMAL(18,8) NULL COMMENT '每1K输出Token成本',
  metadata JSON NULL COMMENT '扩展元数据(JSON)',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  created_by BIGINT UNSIGNED NULL COMMENT '创建人用户ID',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  updated_by BIGINT UNSIGNED NULL COMMENT '更新人用户ID',
  UNIQUE KEY uk_llm_price_configs_name (name),
  CONSTRAINT fk_lpc_created_by FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL,
  CONSTRAINT fk_lpc_updated_by FOREIGN KEY (updated_by) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='LLM 价格配置';

CREATE TABLE llm_models (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  env VARCHAR(32) NOT NULL DEFAULT 'default' COMMENT '环境',
  provider_id VARCHAR(64) NOT NULL COMMENT 'ProviderId（提供方ID）',
  purpose VARCHAR(64) NOT NULL DEFAULT 'CHAT' COMMENT '用途/场景',
  model_name VARCHAR(128) NOT NULL COMMENT '模型名称',
  enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
  is_default TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否默认',
  weight INT NOT NULL DEFAULT 0 COMMENT '权重',
  priority INT NOT NULL DEFAULT 0 COMMENT '优先级',
  sort_index INT NOT NULL DEFAULT 0 COMMENT '排序索引',
  
  qps DECIMAL(10,3) NULL COMMENT 'QPS限制(可选)',
  price_config_id BIGINT NULL COMMENT '价格配置ID',
  metadata JSON NULL COMMENT '扩展元数据(JSON)',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  created_by BIGINT UNSIGNED NULL COMMENT '创建人用户ID',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  updated_by BIGINT UNSIGNED NULL COMMENT '更新人用户ID',
  UNIQUE KEY uk_llm_models_env_provider_purpose_name (env, provider_id, purpose, model_name),
  CONSTRAINT fk_llm_models_provider FOREIGN KEY (env, provider_id) REFERENCES llm_providers(env, provider_id),
  CONSTRAINT fk_llm_models_price FOREIGN KEY (price_config_id) REFERENCES llm_price_configs(id),
  CONSTRAINT fk_llm_mod_created_by FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL,
  CONSTRAINT fk_llm_mod_updated_by FOREIGN KEY (updated_by) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='LLM 模型配置（按 provider/purpose）';

CREATE INDEX idx_llm_models_select ON llm_models(env, provider_id, purpose, enabled, is_default, weight);
CREATE INDEX idx_llm_models_routing ON llm_models(env, purpose, enabled, priority, weight);
CREATE INDEX idx_llm_models_routing_sort ON llm_models(env, purpose, enabled, sort_index, priority, weight);

-- LLM routing policies (scene/capability level) + model priority

CREATE TABLE llm_routing_policies (
  env VARCHAR(32) NOT NULL DEFAULT 'default' COMMENT '环境',
  task_type VARCHAR(64) NOT NULL COMMENT '任务类型/场景',
  strategy VARCHAR(32) NOT NULL DEFAULT 'WEIGHTED_RR' COMMENT '路由策略',
  max_attempts INT NOT NULL DEFAULT 2 COMMENT '最大尝试次数',
  failure_threshold INT NOT NULL DEFAULT 2 COMMENT '失败阈值(触发熔断)',
  cooldown_ms INT NOT NULL DEFAULT 30000 COMMENT '冷却时间(ms)',
  probe_enabled TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否启用探活',
  probe_interval_ms INT NULL COMMENT '探活间隔(ms)',
  probe_path VARCHAR(128) NULL COMMENT '探活路径',
  version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  updated_by BIGINT UNSIGNED NULL COMMENT '更新人用户ID',
  label VARCHAR(128) NULL COMMENT '显示名称',
  category VARCHAR(32) NULL COMMENT '类别(TEXT_GEN/EMBEDDING/RERANK)',
  sort_index INT NOT NULL DEFAULT 0 COMMENT '排序索引',
  PRIMARY KEY (env, task_type),
  CONSTRAINT fk_lrp_updated_by FOREIGN KEY (updated_by) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='LLM 场景/能力路由策略（权重/优先级/健康检查）';

-- LLM routing scenarios (scenes/capabilities) metadata

CREATE TABLE post_compose_ai_snapshots (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    tenant_id BIGINT UNSIGNED NULL COMMENT '租户ID',
    user_id BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    target_type ENUM('DRAFT','POST') NOT NULL COMMENT '目标类型',
    draft_id BIGINT UNSIGNED NULL COMMENT '目标草稿ID',
    post_id BIGINT UNSIGNED NULL COMMENT '目标帖子ID',
    before_title VARCHAR(191) NOT NULL DEFAULT '' COMMENT '快照标题',
    before_content LONGTEXT NOT NULL COMMENT '快照正文',
    before_board_id BIGINT UNSIGNED NOT NULL COMMENT '快照板块ID',
    before_metadata JSON NULL COMMENT '快照扩展元数据(JSON)',
    after_content LONGTEXT NULL COMMENT 'AI生成的正文(采纳时可写入)',
    instruction TEXT NULL COMMENT '用户对AI的编辑指令',
    provider_id VARCHAR(128) NULL COMMENT '使用的Provider',
    model VARCHAR(128) NULL COMMENT '使用的Model',
    temperature DOUBLE NULL COMMENT '温度',
    top_p DOUBLE NULL COMMENT 'Top-P(核采样概率)',
    status ENUM('PENDING','APPLIED','REVERTED','EXPIRED') NOT NULL DEFAULT 'PENDING' COMMENT '状态',
    expires_at DATETIME(3) NULL COMMENT '过期时间',
    resolved_at DATETIME(3) NULL COMMENT '处理完成时间',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    KEY idx_pcas_user_target (user_id, target_type, draft_id, post_id, status, created_at),
    KEY idx_pcas_created (created_at),
    CONSTRAINT fk_pcas_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_pcas_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_pcas_board FOREIGN KEY (before_board_id) REFERENCES boards(id),
    CONSTRAINT fk_pcas_draft FOREIGN KEY (draft_id) REFERENCES post_drafts(id) ON DELETE SET NULL,
    CONSTRAINT fk_pcas_post FOREIGN KEY (post_id) REFERENCES posts(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='发帖编辑AI快照';

CREATE TABLE board_role_permissions (
  board_id BIGINT UNSIGNED NOT NULL COMMENT '板块ID',
  role_id BIGINT UNSIGNED NOT NULL COMMENT '角色ID',
  perm ENUM('VIEW','POST') NOT NULL COMMENT '权限类型',
  PRIMARY KEY (board_id, role_id, perm),
  KEY idx_board_role_permissions_role (role_id),
  KEY idx_board_role_permissions_perm_role (perm, role_id),
  CONSTRAINT fk_board_role_permissions_board FOREIGN KEY (board_id) REFERENCES boards(id) ON DELETE CASCADE,
  CONSTRAINT fk_brp_role FOREIGN KEY (role_id) REFERENCES roles(role_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='板块权限角色关联表';

CREATE TABLE board_moderators (
  board_id BIGINT UNSIGNED NOT NULL COMMENT '板块ID',
  user_id BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
  PRIMARY KEY (board_id, user_id),
  KEY idx_board_moderators_user (user_id),
  CONSTRAINT fk_board_moderators_board FOREIGN KEY (board_id) REFERENCES boards(id) ON DELETE CASCADE,
  CONSTRAINT fk_board_moderators_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='板块版主关联表';

-- Persist completed LLM queue task history for admin metrics (idempotent)
 
CREATE TABLE llm_queue_task_history (
  task_id VARCHAR(64) PRIMARY KEY COMMENT '任务ID (UUID)',
  seq BIGINT NOT NULL COMMENT '队列序号(递增)',
  priority INT NOT NULL COMMENT '优先级',
  type VARCHAR(64) NOT NULL COMMENT '任务类型/场景',
  status VARCHAR(32) NOT NULL COMMENT '任务状态',
  provider_id VARCHAR(64) NULL COMMENT 'LLM ProviderId（提供方ID）',
  model VARCHAR(128) NULL COMMENT '模型名称',

  created_at DATETIME(3) NOT NULL COMMENT '创建时间',
  started_at DATETIME(3) NULL COMMENT '开始时间',
  finished_at DATETIME(3) NULL COMMENT '完成时间',
  wait_ms BIGINT NULL COMMENT '排队等待耗时(ms)',
  duration_ms BIGINT NULL COMMENT '执行耗时(ms)',

  tokens_in INT NULL COMMENT '输入tokens',
  tokens_out INT NULL COMMENT '输出tokens',
  total_tokens INT NULL COMMENT '总tokens',
  tokens_per_sec DOUBLE NULL COMMENT '输出tokens/sec',

  error VARCHAR(1024) NULL COMMENT '错误信息(失败时)',

  input MEDIUMTEXT NULL COMMENT '输入(可能截断)',
  output MEDIUMTEXT NULL COMMENT '输出(可能截断)',
  input_chars INT NULL COMMENT 'input 字符数',
  output_chars INT NULL COMMENT 'output 字符数',
  input_truncated TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'input 是否截断',
  output_truncated TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'output 是否截断',

  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',

  KEY idx_llm_queue_task_history_finished_at (finished_at),
  KEY idx_llm_queue_task_history_type_finished_at (type, finished_at),
  KEY idx_llm_queue_task_history_provider_model_finished_at (provider_id, model, finished_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='LLM 队列任务完成历史(用于监控/审计)';

-- Persist LLM loadtest summaries for regression compare (idempotent)

CREATE TABLE llm_loadtest_run_history (
  run_id VARCHAR(64) PRIMARY KEY COMMENT '压测 runId',
  created_at DATETIME(3) NOT NULL COMMENT '创建时间(压测汇总生成时间)',

  provider_id VARCHAR(64) NULL COMMENT 'LLM ProviderId（提供方ID）',
  model VARCHAR(128) NULL COMMENT '模型名称',

  stream TINYINT(1) NULL COMMENT '是否流式输出',
  enable_thinking TINYINT(1) NULL COMMENT '是否启用深度思考',
  retries INT NULL COMMENT '重试次数',
  retry_delay_ms INT NULL COMMENT '重试间隔(ms)',
  timeout_ms INT NULL COMMENT '超时(ms)',

  summary_json MEDIUMTEXT NOT NULL COMMENT '前端 summary JSON',

  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',

  KEY idx_llm_loadtest_run_history_created_at (created_at),
  KEY idx_llm_loadtest_run_history_provider_model_created_at (provider_id, model, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='LLM 压测回归对比历史';

-- Persist LLM loadtest per-call details (idempotent)

CREATE TABLE llm_loadtest_run_detail (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '自增主键',
  run_id VARCHAR(64) NOT NULL COMMENT '压测 runId',
  req_index INT NOT NULL COMMENT '请求序号(从0开始)',
  kind VARCHAR(32) NOT NULL COMMENT '请求类型: CHAT_STREAM / MODERATION_TEST',
  ok TINYINT(1) NOT NULL COMMENT '是否成功',

  started_at DATETIME(3) NOT NULL COMMENT '开始时间',
  finished_at DATETIME(3) NOT NULL COMMENT '结束时间',
  latency_ms BIGINT NULL COMMENT '耗时(ms)',

  provider_id VARCHAR(64) NULL COMMENT '实际 providerId',
  model VARCHAR(128) NULL COMMENT '实际 model',

  tokens_in INT NULL COMMENT '输入 tokens',
  tokens_out INT NULL COMMENT '输出 tokens',
  total_tokens INT NULL COMMENT '总 tokens',

  error VARCHAR(1024) NULL COMMENT '错误信息(截断)',

  request_json MEDIUMTEXT NULL COMMENT '请求内容(JSON, 可能截断)',
  response_json MEDIUMTEXT NULL COMMENT '响应内容(JSON, 可能截断)',
  request_chars INT NULL COMMENT 'request_json 字符数(截断后)',
  response_chars INT NULL COMMENT 'response_json 字符数(截断后)',
  request_truncated TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'request_json 是否截断',
  response_truncated TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'response_json 是否截断',

  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '记录创建时间',

  UNIQUE KEY uk_llm_loadtest_run_detail_run_idx (run_id, req_index),
  KEY idx_llm_loadtest_run_detail_run_created (run_id, created_at),
  KEY idx_llm_loadtest_run_detail_run_kind (run_id, kind),
  KEY idx_llm_loadtest_run_detail_run_ok (run_id, ok)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='LLM 压测每次调用明细';

CREATE TABLE rbac_audit_logs (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    actor_user_id BIGINT UNSIGNED NULL COMMENT '操作者用户ID',
    action VARCHAR(64) NOT NULL COMMENT '动作(例如 PERMISSION_CREATE/ROLE_MATRIX_REPLACE/USER_ROLES_ASSIGN)',
    target_type VARCHAR(64) NOT NULL COMMENT '目标类型(permissions/role_permissions/user_role_links 等)',
    target_id VARCHAR(191) NULL COMMENT '目标标识(可为 id 或复合键字符串)',
    reason VARCHAR(255) NULL COMMENT '变更原因(建议必填)',
    diff_json LONGTEXT NULL COMMENT '变更摘要(JSON)',
    request_ip VARCHAR(64) NULL COMMENT '请求IP',
    user_agent VARCHAR(255) NULL COMMENT 'UA 信息',
    request_id VARCHAR(128) NULL COMMENT '请求ID(可选)',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    KEY idx_rbac_audit_actor_time (actor_user_id, created_at),
    KEY idx_rbac_audit_target_time (target_type, target_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RBAC 变更审计日志';

CREATE TABLE access_logs (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    tenant_id BIGINT UNSIGNED NULL COMMENT '租户ID',
    user_id BIGINT UNSIGNED NULL COMMENT '用户ID（若可识别）',
    username VARCHAR(191) NULL COMMENT '用户账号（邮箱/用户名）',
    method VARCHAR(16) NOT NULL COMMENT 'HTTP方法',
    path VARCHAR(512) NOT NULL COMMENT '请求路径（不含域名）',
    query_string VARCHAR(1024) NULL COMMENT '查询串（已脱敏/裁剪）',
    status_code INT NULL COMMENT '响应状态码',
    latency_ms INT NULL COMMENT '耗时（毫秒）',
    client_ip VARCHAR(64) NULL COMMENT '网络源地址（解析 Forwarded/XFF）',
    client_port INT NULL COMMENT '网络源端口',
    server_ip VARCHAR(64) NULL COMMENT '网络目标地址（本机）',
    server_port INT NULL COMMENT '网络目标端口（本机）',
    scheme VARCHAR(16) NULL COMMENT 'scheme（http/https）',
    host VARCHAR(255) NULL COMMENT 'Host',
    request_id VARCHAR(64) NULL COMMENT '请求ID（关联）',
    trace_id VARCHAR(64) NULL COMMENT '链路ID（关联）',
    user_agent VARCHAR(512) NULL COMMENT 'User-Agent（裁剪）',
    referer VARCHAR(512) NULL COMMENT 'Referer（裁剪）',
    details JSON NULL COMMENT '扩展详情（JSON）',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    archived_at DATETIME(3) NULL COMMENT '归档时间',
    KEY idx_access_created (created_at),
    KEY idx_access_user (user_id),
    KEY idx_access_client_ip (client_ip),
    KEY idx_access_request_id (request_id),
    KEY idx_access_trace_id (trace_id),
    KEY idx_access_path (path(191)),
    KEY idx_access_archived_at (archived_at),
    CONSTRAINT fk_access_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_access_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='HTTP访问日志（安全取证）';

-- access_logs_archive 和 audit_logs_archive 已合并到各自主表（通过 archived_at 列区分）

CREATE TABLE file_asset_extractions (
    file_asset_id BIGINT UNSIGNED NOT NULL COMMENT 'file_assets.id',
    extract_status ENUM('PENDING','READY','FAILED') NOT NULL DEFAULT 'PENDING' COMMENT '解析状态',
    extracted_text LONGTEXT NULL COMMENT '抽取文本',
    extracted_metadata_json LONGTEXT NULL COMMENT '抽取元数据(JSON字符串)',
    error_message VARCHAR(1024) NULL COMMENT '错误信息',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (file_asset_id),
    CONSTRAINT fk_fae_file_asset FOREIGN KEY (file_asset_id) REFERENCES file_assets(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件解析产物表';

CREATE TABLE image_upload_logs (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    local_path VARCHAR(500) NOT NULL COMMENT '本地文件路径',
    remote_url VARCHAR(2000) NOT NULL COMMENT '远程 URL (oss:// 或 https://)',
    storage_mode VARCHAR(30) NOT NULL COMMENT '存储模式: LOCAL / DASHSCOPE_TEMP / ALIYUN_OSS',
    model_name VARCHAR(100) NULL COMMENT '绑定模型名 (百炼临时存储)',
    file_size_bytes BIGINT NULL COMMENT '文件大小 (字节)',
    upload_duration_ms INT NULL COMMENT '上传耗时 (毫秒)',
    uploaded_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '上传时间',
    expires_at DATETIME(3) NULL COMMENT '过期时间 (百炼 48h, OSS 为 NULL)',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE / EXPIRED / FAILED',
    KEY idx_upload_logs_local_path (local_path(191)),
    KEY idx_upload_logs_status_expires (status, expires_at),
    KEY idx_upload_logs_uploaded_at (uploaded_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='图片上传日志';

CREATE TABLE moderation_chunk_sets (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    queue_id BIGINT UNSIGNED NOT NULL,
    case_type VARCHAR(16) NOT NULL,
    content_type VARCHAR(16) NOT NULL,
    content_id BIGINT UNSIGNED NOT NULL,
    status VARCHAR(16) NOT NULL,
    chunk_threshold_chars INT NULL,
    chunk_size_chars INT NULL,
    overlap_chars INT NULL,
    total_chunks INT NOT NULL DEFAULT 0,
    completed_chunks INT NOT NULL DEFAULT 0,
    failed_chunks INT NOT NULL DEFAULT 0,
    memory_json LONGTEXT NULL,
    config_json LONGTEXT NULL,
    cancelled_at DATETIME NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    version INT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_moderation_chunk_sets_queue_id (queue_id),
    KEY idx_moderation_chunk_sets_content (content_type, content_id),
    CONSTRAINT fk_moderation_chunk_sets_queue FOREIGN KEY (queue_id) REFERENCES moderation_queue(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审核分片集合';

CREATE TABLE moderation_chunks (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    chunk_set_id BIGINT UNSIGNED NOT NULL,
    source_type VARCHAR(16) NOT NULL,
    source_key VARCHAR(64) NOT NULL,
    file_asset_id BIGINT UNSIGNED NULL,
    file_name VARCHAR(191) NULL,
    chunk_index INT NOT NULL,
    start_offset INT NOT NULL,
    end_offset INT NOT NULL,
    status VARCHAR(16) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    last_error VARCHAR(1000) NULL,
    model VARCHAR(64) NULL,
    verdict VARCHAR(16) NULL,
    confidence DECIMAL(5,4) NULL,
    labels LONGTEXT NULL,
    tokens_in INT NULL,
    tokens_out INT NULL,
    decided_at DATETIME NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    version INT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_moderation_chunks_set_source_idx (chunk_set_id, source_key, chunk_index),
    KEY idx_moderation_chunks_set_status_idx (chunk_set_id, status, chunk_index),
    KEY idx_moderation_chunks_file_asset (file_asset_id),
    CONSTRAINT fk_moderation_chunks_set FOREIGN KEY (chunk_set_id) REFERENCES moderation_chunk_sets(id) ON DELETE CASCADE,
    CONSTRAINT fk_mc_file_asset FOREIGN KEY (file_asset_id) REFERENCES file_assets(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审核分片明细';

CREATE TABLE ai_chat_context_events (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NULL,
    session_id BIGINT UNSIGNED NULL,
    question_message_id BIGINT UNSIGNED NULL,
    kind VARCHAR(32) NOT NULL,
    reason VARCHAR(64) NOT NULL,
    target_prompt_tokens INT NULL,
    reserve_answer_tokens INT NULL,
    before_tokens INT NOT NULL,
    after_tokens INT NOT NULL,
    before_chars INT NOT NULL,
    after_chars INT NOT NULL,
    latency_ms INT NULL,
    detail_json LONGTEXT NULL,
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_ai_chat_ctx_events_created_at (created_at),
    INDEX idx_ai_chat_ctx_events_session_id (session_id),
    CONSTRAINT fk_acce_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT fk_acce_session FOREIGN KEY (session_id) REFERENCES qa_sessions(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI对话上下文治理事件';


-- Initial prompt data insertion
INSERT INTO prompts (prompt_code, name, created_at, updated_at) VALUES
('MODERATION_MULTIMODAL', '多模态审核', NOW(), NOW()),
('MODERATION_JUDGE', '审核裁决', NOW(), NOW()),
('SUMMARY_GEN', '摘要生成', NOW(), NOW()),
('TITLE_GEN', '标题生成', NOW(), NOW()),
('TAG_GEN', '标签生成', NOW(), NOW()),
('LANG_DETECT', '语言检测', NOW(), NOW()),
('TRANSLATE_GEN', '翻译生成', NOW(), NOW()),
('PORTAL_CHAT_ASSISTANT', '门户对话助手', NOW(), NOW()),
('PORTAL_CHAT_ASSISTANT_DEEP_THINK', '门户对话助手(深度思考)', NOW(), NOW()),
('PORTAL_POST_COMPOSE', '帖子润色', NOW(), NOW()),
('PORTAL_POST_COMPOSE_DEEP_THINK', '帖子润色(深度思考)', NOW(), NOW()),
('PORTAL_POST_COMPOSE_PROTOCOL', '帖子润色协议', NOW(), NOW());

SET FOREIGN_KEY_CHECKS = 1;

