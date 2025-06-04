-- V2__insert_super_admin.sql
-- 1）插入或更新 SuperAdministrator 权限
INSERT INTO admin_permissions (
    roles,
    can_login,
    can_manage_announcement,
    can_manage_help_articles,
    can_create_super_admin,
    can_create_admin,
    can_create_user_account,
    can_manage_admin_permissions,
    can_manage_user_permissions,
    can_reset_admin_password,
    can_reset_user_password,
    can_pay_user_overdue,
    can_lend_books_to_user,
    can_return_books_for_user,
    allow_edit_readers_profile,
    allow_edit_profile,
    allow_edit_other_admin_profile
) VALUES (
             'SuperAdministrator',
             1, 1, 1,
             1, 1, 1,
             1, 1,
             1, 1,
             1, 1, 1,
             1, 1, 1
         )
ON DUPLICATE KEY UPDATE
                     can_login                     = VALUES(can_login),
                     can_manage_announcement       = VALUES(can_manage_announcement),
                     can_manage_help_articles      = VALUES(can_manage_help_articles),
                     can_create_super_admin        = VALUES(can_create_super_admin),
                     can_create_admin              = VALUES(can_create_admin),
                     can_create_user_account       = VALUES(can_create_user_account),
                     can_manage_admin_permissions  = VALUES(can_manage_admin_permissions),
                     can_manage_user_permissions   = VALUES(can_manage_user_permissions),
                     can_reset_admin_password      = VALUES(can_reset_admin_password),
                     can_reset_user_password       = VALUES(can_reset_user_password),
                     can_pay_user_overdue          = VALUES(can_pay_user_overdue),
                     can_lend_books_to_user        = VALUES(can_lend_books_to_user),
                     can_return_books_for_user     = VALUES(can_return_books_for_user),
                     allow_edit_readers_profile    = VALUES(allow_edit_readers_profile),
                     allow_edit_profile            = VALUES(allow_edit_profile),
                     allow_edit_other_admin_profile= VALUES(allow_edit_other_admin_profile);

-- 2）插入或更新 管理员账号
INSERT INTO administrators (
    account,
    password,
    phone,
    email,
    permissions_id,
    registered_at,
    sex,
    is_active
) VALUES (
             'admin',
             '$2a$10$XvkfedUmWlHHmbBihk7mz.NelET1zu.FmESqgmYRg9OX/GQd8e.V.',        -- 如果需要修改默认密码或加密请在java/com/example/FinalAssignments/utils/PasswordEncoderUtil.java 生成 bcrypt/hash 值并写入这里
             '12345678901',
             'admin@admin.com',
             (SELECT id FROM admin_permissions WHERE roles = 'SuperAdministrator'),
             NOW(),
             '男',
             0
         )
ON DUPLICATE KEY UPDATE
                     password       = VALUES(password),
                     phone          = VALUES(phone),
                     email          = VALUES(email),
                     permissions_id = VALUES(permissions_id),
                     registered_at  = VALUES(registered_at),
                     sex            = VALUES(sex),
                     is_active      = VALUES(is_active);