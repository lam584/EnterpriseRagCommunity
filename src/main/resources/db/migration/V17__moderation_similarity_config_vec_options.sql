ALTER TABLE moderation_similarity_config
  ADD COLUMN embedding_model VARCHAR(128) NULL AFTER enabled,
  ADD COLUMN embedding_dims INT NOT NULL DEFAULT 0 AFTER embedding_model,
  ADD COLUMN max_input_chars INT NOT NULL DEFAULT 0 AFTER embedding_dims,
  ADD COLUMN default_top_k INT NOT NULL DEFAULT 5 AFTER max_input_chars,
  ADD COLUMN default_threshold DOUBLE NOT NULL DEFAULT 0.15 AFTER default_top_k,
  ADD COLUMN default_num_candidates INT NOT NULL DEFAULT 0 AFTER default_threshold;

