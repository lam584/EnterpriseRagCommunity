-- Seed a default board for easier posting tests.
-- Idempotent: insert only when the same default board does not exist.

INSERT INTO boards(
  tenant_id,
  parent_id,
  name,
  description,
  visible,
  sort_order,
  created_at,
  updated_at
)
SELECT
  NULL,
  NULL,
  '默认版块',
  '系统初始化默认版块，用于测试发帖功能',
  1,
  0,
  NOW(3),
  NOW(3)
WHERE NOT EXISTS (
  SELECT 1
  FROM boards b
  WHERE b.tenant_id IS NULL
    AND b.parent_id IS NULL
    AND b.name = '默认版块'
);

