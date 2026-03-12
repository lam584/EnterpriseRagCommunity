ALTER TABLE access_logs
    ADD KEY idx_access_archived_created_id (archived_at, created_at, id),
    ADD KEY idx_access_archived_user_created (archived_at, user_id, created_at, id),
    ADD KEY idx_access_archived_status_created (archived_at, status_code, created_at, id);