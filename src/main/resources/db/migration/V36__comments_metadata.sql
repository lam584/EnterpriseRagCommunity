ALTER TABLE comments
    ADD COLUMN metadata JSON NULL COMMENT '扩展元数据（JSON）' AFTER is_deleted;

