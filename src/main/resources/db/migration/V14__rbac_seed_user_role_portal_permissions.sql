-- Seed a baseline USER role (role_id=1) with all portal_* permissions.
-- Notes:
-- - This project infers roles from role_permissions (no standalone roles table).
-- - Portal routes are guarded by RBAC permissions on the frontend.
-- Idempotent: uses INSERT IGNORE on PK(role_id, permission_id).

UPDATE role_permissions
SET role_name = 'USER'
WHERE role_id = 1
  AND (role_name IS NULL OR role_name = '');

INSERT IGNORE INTO role_permissions(role_id, role_name, permission_id, allow)
SELECT
  1,
  'USER',
  p.id,
  1
FROM permissions p
WHERE p.resource LIKE 'portal\\_%' ESCAPE '\\';

