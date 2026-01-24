-- Default enable RAG post auto incremental sync (admin/retrieval?active=index)
INSERT IGNORE INTO app_settings(k, v) VALUES ('retrieval.rag.autoSync.enabled', 'true');
INSERT IGNORE INTO app_settings(k, v) VALUES ('retrieval.rag.autoSync.intervalSeconds', '30');
