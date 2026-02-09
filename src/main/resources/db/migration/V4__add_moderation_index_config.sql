INSERT INTO moderation_samples_index_config (
  index_name, ik_enabled, embedding_model, embedding_dims, 
  default_top_k, default_threshold, version, updated_at
)
SELECT 
  'ad_violation_samples_v1', 
  true, 
  NULL, 
  0, 
  5, 
  0.15, 
  0, 
  NOW()
WHERE NOT EXISTS (
  SELECT 1 FROM moderation_samples_index_config
);
