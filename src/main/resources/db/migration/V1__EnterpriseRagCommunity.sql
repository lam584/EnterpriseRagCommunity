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
                       status ENUM('ACTIVE','DISABLED','PENDING','DELETED') NOT NULL DEFAULT 'ACTIVE' COMMENT '账户状态',
                       last_login_at DATETIME(3) NULL COMMENT '上次登录时间',
                       is_deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '软删除标记',
                       metadata JSON NULL COMMENT '用户扩展元数据（JSON）',
                       created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
                       updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
                       UNIQUE KEY uk_users_email_tenant (tenant_id, email),
                       UNIQUE KEY uk_users_username_tenant (tenant_id, username),
                       CONSTRAINT fk_users_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 用户-角色关联
CREATE TABLE user_role_links (
                                 user_id BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
                                 role_id BIGINT UNSIGNED NOT NULL COMMENT '角色ID',
                                 PRIMARY KEY (user_id, role_id),
                                 CONSTRAINT fk_url_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户与角色关联表';

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
                                     code VARCHAR(64) NOT NULL COMMENT '验证码',
                                     purpose ENUM('VERIFY_EMAIL','PASSWORD_RESET') NOT NULL COMMENT '用途',
                                     expires_at DATETIME(3) NOT NULL COMMENT '过期时间',
                                     consumed_at DATETIME(3) NULL COMMENT '使用时间',
                                     created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
                                     KEY idx_ev_user (user_id),
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
                            KEY idx_audit_entity (entity_type, entity_id),
                            KEY idx_audit_actor (actor_user_id),
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
                       status ENUM('DRAFT','PENDING','PUBLISHED','REJECTED','ARCHIVED') NOT NULL DEFAULT 'DRAFT' COMMENT '帖子状态',
                       published_at DATETIME(3) NULL COMMENT '发布时间',
                       is_deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '软删除标记',
                       metadata JSON NULL COMMENT '扩展元数据（JSON）',
                       created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
                       updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
                       FULLTEXT KEY ft_posts (title, content),
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

CREATE TABLE post_attachments (
                                  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
                                  post_id BIGINT UNSIGNED NOT NULL COMMENT '帖子ID',
                                  url VARCHAR(512) NOT NULL COMMENT '附件访问URL',
                                  file_name VARCHAR(191) NOT NULL COMMENT '原始文件名',
                                  mime_type VARCHAR(64) NOT NULL COMMENT 'MIME 类型',
                                  size_bytes BIGINT UNSIGNED NOT NULL COMMENT '文件大小（字节）',
                                  width INT NULL COMMENT '图片宽（像素）',
                                  height INT NULL COMMENT '图片高（像素）',
                                  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
                                  KEY idx_pa_post (post_id),
                                  CONSTRAINT fk_pa_post FOREIGN KEY (post_id) REFERENCES posts(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='帖子附件表（仅图片）';

CREATE TABLE comments (
                          id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
                          post_id BIGINT UNSIGNED NOT NULL COMMENT '帖子ID',
                          parent_id BIGINT UNSIGNED NULL COMMENT '父评论ID（多层：邻接表）',
                          author_id BIGINT UNSIGNED NOT NULL COMMENT '作者用户ID',
                          content TEXT NOT NULL COMMENT '评论内容',
                          status ENUM('VISIBLE','PENDING','HIDDEN','REJECTED') NOT NULL DEFAULT 'VISIBLE' COMMENT '评论状态',
                          is_deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '软删除标记',
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
                           type ENUM('LIKE') NOT NULL COMMENT '互动类型',
                           created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
                           UNIQUE KEY uk_react (user_id, target_type, target_id, type),
                           KEY idx_react_target (target_type, target_id),
                           CONSTRAINT fk_react_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='互动表（点赞等）';

CREATE TABLE favorites (
                           id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
                           user_id BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
                           post_id BIGINT UNSIGNED NOT NULL COMMENT '帖子ID',
                           created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '收藏时间',
                           UNIQUE KEY uk_fav (user_id, post_id),
                           CONSTRAINT fk_fav_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
                           CONSTRAINT fk_fav_post FOREIGN KEY (post_id) REFERENCES posts(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收藏表';

CREATE TABLE reports (
                         id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
                         reporter_id BIGINT UNSIGNED NOT NULL COMMENT '举报人用户ID',
                         target_type ENUM('POST','COMMENT') NOT NULL COMMENT '被举报对象类型',
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
                            CONSTRAINT fk_hot_post FOREIGN KEY (post_id) REFERENCES posts(id) ON DELETE CASCADE
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
                         name VARCHAR(96) NOT NULL COMMENT '模板名称',
                         template MEDIUMTEXT NOT NULL COMMENT '提示词模板内容',
                         variables JSON NULL COMMENT '模板变量定义（JSON）',
                         version INT NOT NULL DEFAULT 1 COMMENT '版本号',
                         is_active TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
                         created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
                         UNIQUE KEY uk_prompt_name_ver (name, version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='提示词模板表';

CREATE TABLE generation_jobs (
                                 id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
                                 job_type ENUM('TITLE','TAGS','SUMMARY','TRANSLATE') NOT NULL COMMENT '生成任务类型',
                                 target_type ENUM('POST','COMMENT') NOT NULL COMMENT '目标类型',
                                 target_id BIGINT UNSIGNED NOT NULL COMMENT '目标ID',
                                 status ENUM('PENDING','RUNNING','SUCCEEDED','FAILED') NOT NULL DEFAULT 'PENDING' COMMENT '任务状态',
                                 prompt_id BIGINT UNSIGNED NULL COMMENT '使用的提示词模板ID',
                                 model VARCHAR(64) NULL COMMENT '使用的模型名称',
                                 params JSON NULL COMMENT '任务参数（JSON）',
                                 result_json JSON NULL COMMENT '生成结果（JSON）',
                                 tokens_in INT NULL COMMENT '输入Token数',
                                 tokens_out INT NULL COMMENT '输出Token数',
                                 cost_cents INT NULL COMMENT '成本（分）',
                                 error_message VARCHAR(255) NULL COMMENT '错误信息',
                                 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
                                 updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
                                 KEY idx_gj_target (target_type, target_id),
                                 CONSTRAINT fk_gj_prompt FOREIGN KEY (prompt_id) REFERENCES prompts(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='生成任务记录表';

CREATE TABLE retrieval_events (
                                  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
                                  user_id BIGINT UNSIGNED NULL COMMENT '触发检索的用户ID',
                                  query_text TEXT NOT NULL COMMENT '查询文本',
                                  bm25_k INT NULL COMMENT 'BM25 TopK',
                                  vec_k INT NULL COMMENT '向量召回 TopK',
                                  hybrid_k INT NULL COMMENT '融合后保留 TopK',
                                  rerank_model VARCHAR(64) NULL COMMENT '重排模型',
                                  rerank_k INT NULL COMMENT '重排TopK',
                                  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
                                  CONSTRAINT fk_re_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='检索事件表';

CREATE TABLE retrieval_hits (
                                id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
                                event_id BIGINT UNSIGNED NOT NULL COMMENT '检索事件ID',
                                `rank` INT NOT NULL COMMENT '排序名次',
                                hit_type ENUM('BM25','VEC','RERANK') NOT NULL COMMENT '命中类型',
                                document_id BIGINT UNSIGNED NULL COMMENT '文档ID',
                                chunk_id BIGINT UNSIGNED NULL COMMENT '分片ID',
                                score DOUBLE NOT NULL COMMENT '得分',
                                CONSTRAINT fk_rh_event FOREIGN KEY (event_id) REFERENCES retrieval_events(id) ON DELETE CASCADE,
                                CONSTRAINT fk_rh_doc FOREIGN KEY (document_id) REFERENCES documents(id) ON DELETE SET NULL,
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
                                      content_type ENUM('POST','COMMENT') NOT NULL COMMENT '内容类型',
                                      content_id BIGINT UNSIGNED NOT NULL COMMENT '内容ID',
                                      rule_id BIGINT UNSIGNED NOT NULL COMMENT '命中规则ID',
                                      snippet VARCHAR(255) NULL COMMENT '命中文本片段',
                                      matched_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '命中时间',
                                      KEY idx_mrh_target (content_type, content_id),
                                      CONSTRAINT fk_mrh_rule FOREIGN KEY (rule_id) REFERENCES moderation_rules(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='规则命中日志表';

CREATE TABLE moderation_similar_hits (
                                         id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
                                         content_type ENUM('POST','COMMENT') NOT NULL COMMENT '内容类型',
                                         content_id BIGINT UNSIGNED NOT NULL COMMENT '内容ID',
                                         candidate_id BIGINT UNSIGNED NULL COMMENT '相似样本ID/参考ID',
                                         distance DOUBLE NOT NULL COMMENT '相似距离',
                                         threshold DOUBLE NOT NULL COMMENT '距离阈值',
                                         matched_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '命中时间',
                                         KEY idx_msh_target (content_type, content_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='相似度命中日志表';

CREATE TABLE moderation_llm_decisions (
                                          id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
                                          content_type ENUM('POST','COMMENT') NOT NULL COMMENT '内容类型',
                                          content_id BIGINT UNSIGNED NOT NULL COMMENT '内容ID',
                                          model VARCHAR(64) NOT NULL COMMENT '判定模型',
                                          labels JSON NOT NULL COMMENT '标签集合（JSON）',
                                          confidence DECIMAL(5,4) NOT NULL COMMENT '综合置信度',
                                          verdict ENUM('APPROVE','REJECT','REVIEW') NOT NULL COMMENT '裁决结果',
                                          prompt_id BIGINT UNSIGNED NULL COMMENT '提示词模板ID',
                                          tokens_in INT NULL COMMENT '输入Token',
                                          tokens_out INT NULL COMMENT '输出Token',
                                          decided_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '判定时间',
                                          KEY idx_mld_target (content_type, content_id),
                                          CONSTRAINT fk_mld_prompt FOREIGN KEY (prompt_id) REFERENCES prompts(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='LLM 审核判定表';

CREATE TABLE moderation_queue (
                                  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
                                  content_type ENUM('POST','COMMENT') NOT NULL COMMENT '内容类型',
                                  content_id BIGINT UNSIGNED NOT NULL COMMENT '内容ID',
                                  status ENUM('PENDING','REVIEWING','HUMAN','APPROVED','REJECTED') NOT NULL DEFAULT 'PENDING' COMMENT '队列状态',
                                  current_stage ENUM('RULE','VEC','LLM','HUMAN') NOT NULL DEFAULT 'RULE' COMMENT '当前阶段',
                                  priority INT NOT NULL DEFAULT 0 COMMENT '优先级',
                                  assigned_to BIGINT UNSIGNED NULL COMMENT '指派审核员用户ID',
                                  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
                                  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
                                  UNIQUE KEY uk_mq_target (content_type, content_id),
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
                               target_type ENUM('POST','COMMENT') NOT NULL COMMENT '目标类型',
                               target_id BIGINT UNSIGNED NOT NULL COMMENT '目标ID',
                               tag_id BIGINT UNSIGNED NOT NULL COMMENT '风险标签ID',
                               source ENUM('LLM','RULE','HUMAN') NOT NULL COMMENT '来源',
                               confidence DECIMAL(5,4) NULL COMMENT '置信度',
                               created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
                               UNIQUE KEY uk_rl (target_type, target_id, tag_id, source),
                               CONSTRAINT fk_rl_tag FOREIGN KEY (tag_id) REFERENCES tags(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='风险标签关联表';

-- 问答与服务
CREATE TABLE qa_sessions (
                             id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
                             user_id BIGINT UNSIGNED NULL COMMENT '会话所属用户ID',
                             title VARCHAR(191) NULL COMMENT '会话标题',
                             context_strategy ENUM('RECENT_N','SUMMARIZE','NONE') NOT NULL DEFAULT 'RECENT_N' COMMENT '上下文保留策略',
                             is_active TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否有效',
                             created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
                             CONSTRAINT fk_qs_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='问答会话表';

CREATE TABLE qa_messages (
                             id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
                             session_id BIGINT UNSIGNED NOT NULL COMMENT '会话ID',
                             role ENUM('USER','ASSISTANT','SYSTEM') NOT NULL COMMENT '消息角色',
                             content LONGTEXT NOT NULL COMMENT '消息内容',
                             model VARCHAR(64) NULL COMMENT '生成模型（若为AI）',
                             tokens_in INT NULL COMMENT '输入Token',
                             tokens_out INT NULL COMMENT '输出Token',
                             created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
                             KEY idx_qm_session (session_id),
                             CONSTRAINT fk_qm_session FOREIGN KEY (session_id) REFERENCES qa_sessions(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='问答消息表';

CREATE TABLE qa_turns (
                          id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
                          session_id BIGINT UNSIGNED NOT NULL COMMENT '会话ID',
                          question_message_id BIGINT UNSIGNED NOT NULL COMMENT '问题消息ID',
                          answer_message_id BIGINT UNSIGNED NULL COMMENT '答案消息ID',
                          latency_ms INT NULL COMMENT '问答延迟（毫秒）',
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

CREATE TABLE file_assets (
                             id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
                             owner_user_id BIGINT UNSIGNED NULL COMMENT '所有者用户ID',
                             path VARCHAR(512) NOT NULL COMMENT '存储路径',
                             url VARCHAR(512) NOT NULL COMMENT '访问URL',
                             size_bytes BIGINT UNSIGNED NOT NULL COMMENT '文件大小（字节）',
                             mime_type VARCHAR(64) NOT NULL COMMENT 'MIME 类型',
                             sha256 CHAR(64) NOT NULL COMMENT '文件SHA256',
                             status ENUM('READY','UPLOADING','DELETED') NOT NULL DEFAULT 'READY' COMMENT '状态',
                             created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
                             UNIQUE KEY uk_file_sha (sha256),
                             CONSTRAINT fk_fa_owner FOREIGN KEY (owner_user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件资源索引表';

SET FOREIGN_KEY_CHECKS = 1;
