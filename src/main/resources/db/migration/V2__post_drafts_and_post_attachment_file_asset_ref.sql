-- MySQL 8.0, InnoDB, utf8mb4
SET NAMES utf8mb4;

-- Post drafts are kept separate from posts to avoid mixing permissions and lists.
CREATE TABLE IF NOT EXISTS post_drafts (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    tenant_id BIGINT UNSIGNED NULL COMMENT '租户ID',
    board_id BIGINT UNSIGNED NOT NULL COMMENT '所属板块ID',
    author_id BIGINT UNSIGNED NOT NULL COMMENT '作者用户ID',
    title VARCHAR(191) NOT NULL DEFAULT '' COMMENT '草稿标题',
    content LONGTEXT NOT NULL COMMENT '草稿内容',
    content_format ENUM('PLAIN','MARKDOWN','HTML') NOT NULL DEFAULT 'MARKDOWN' COMMENT '内容格式',
    metadata JSON NULL COMMENT '扩展元数据(JSON)，可存 tags/attachmentIds 等',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    KEY idx_pd_author_updated (author_id, updated_at),
    KEY idx_pd_board (board_id),
    CONSTRAINT fk_pd_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_pd_board FOREIGN KEY (board_id) REFERENCES boards(id),
    CONSTRAINT fk_pd_author FOREIGN KEY (author_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='帖子草稿箱(独立表)';

-- Optional: keep a reference from post_attachments back to file_assets for traceability.
ALTER TABLE post_attachments
    ADD COLUMN file_asset_id BIGINT UNSIGNED NULL COMMENT '来源 file_assets.id' AFTER post_id;

ALTER TABLE post_attachments
    ADD KEY idx_pa_file_asset (file_asset_id);

ALTER TABLE post_attachments
    ADD CONSTRAINT fk_pa_file_asset FOREIGN KEY (file_asset_id) REFERENCES file_assets(id);

