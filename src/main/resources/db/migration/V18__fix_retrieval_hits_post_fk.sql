-- Fix retrieval_hits.document_id foreign key: retrieval logs for chat RAG store post_id here.
-- Old FK incorrectly referenced documents(id), causing FK 1452 during chat when inserting post-based hits.

ALTER TABLE retrieval_hits DROP FOREIGN KEY fk_rh_doc;

UPDATE retrieval_hits rh
LEFT JOIN posts p ON rh.document_id = p.id
SET rh.document_id = NULL
WHERE rh.document_id IS NOT NULL AND p.id IS NULL;

ALTER TABLE retrieval_hits
    ADD CONSTRAINT fk_rh_post FOREIGN KEY (document_id) REFERENCES posts(id) ON DELETE SET NULL;
