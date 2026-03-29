UPDATE vector_indices
SET metadata = JSON_REMOVE(metadata, '$.embeddingModel')
WHERE metadata IS NOT NULL
  AND JSON_CONTAINS_PATH(metadata, 'one', '$.embeddingModel');
