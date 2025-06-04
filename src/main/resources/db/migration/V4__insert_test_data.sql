-- 插入管理员权限数据
INSERT INTO admin_permissions (roles, can_login, can_manage_announcement, can_manage_help_articles, can_create_super_admin,
                             can_create_admin, can_create_user_account, can_manage_admin_permissions, can_manage_user_permissions,
                             can_reset_admin_password, can_reset_user_password, can_pay_user_overdue, can_lend_books_to_user,
                             can_return_books_for_user, allow_edit_readers_profile, allow_edit_profile, allow_edit_other_admin_profile)
SELECT '超级管理员', 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1 FROM dual WHERE NOT EXISTS (SELECT * FROM admin_permissions WHERE roles = '超级管理员');

INSERT INTO admin_permissions (roles, can_login, can_manage_announcement, can_manage_help_articles, can_create_super_admin,
                             can_create_admin, can_create_user_account, can_manage_admin_permissions, can_manage_user_permissions,
                             can_reset_admin_password, can_reset_user_password, can_pay_user_overdue, can_lend_books_to_user,
                             can_return_books_for_user, allow_edit_readers_profile, allow_edit_profile, allow_edit_other_admin_profile)
SELECT '高级管理员', 1, 1, 1, 0, 1, 1, 0, 1, 0, 1, 1, 1, 1, 1, 1, 0 FROM dual WHERE NOT EXISTS (SELECT * FROM admin_permissions WHERE roles = '高级管理员');

INSERT INTO admin_permissions (roles, can_login, can_manage_announcement, can_manage_help_articles, can_create_super_admin,
                             can_create_admin, can_create_user_account, can_manage_admin_permissions, can_manage_user_permissions,
                             can_reset_admin_password, can_reset_user_password, can_pay_user_overdue, can_lend_books_to_user,
                             can_return_books_for_user, allow_edit_readers_profile, allow_edit_profile, allow_edit_other_admin_profile)
SELECT '普通管理员', 1, 0, 1, 0, 0, 1, 0, 0, 0, 1, 1, 1, 1, 1, 1, 0 FROM dual WHERE NOT EXISTS (SELECT * FROM admin_permissions WHERE roles = '普通管理员');

INSERT INTO admin_permissions (roles, can_login, can_manage_announcement, can_manage_help_articles, can_create_super_admin,
                             can_create_admin, can_create_user_account, can_manage_admin_permissions, can_manage_user_permissions,
                             can_reset_admin_password, can_reset_user_password, can_pay_user_overdue, can_lend_books_to_user,
                             can_return_books_for_user, allow_edit_readers_profile, allow_edit_profile, allow_edit_other_admin_profile)
SELECT '图书管理员', 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 0, 1, 0 FROM dual WHERE NOT EXISTS (SELECT * FROM admin_permissions WHERE roles = '图书管理员');

-- 插入读者权限数据
INSERT INTO readers_permissions (roles, can_login, can_reserve, can_view_announcement, can_view_help_articles,
                               can_reset_own_password, can_borrow_return_books, allow_edit_profile, notes)
SELECT '普通读者', 1, 1, 1, 1, 1, 1, 1, '基本的读者权限' FROM dual WHERE NOT EXISTS (SELECT * FROM readers_permissions WHERE roles = '普通读者');

INSERT INTO readers_permissions (roles, can_login, can_reserve, can_view_announcement, can_view_help_articles,
                               can_reset_own_password, can_borrow_return_books, allow_edit_profile, notes)
SELECT 'VIP读者', 1, 1, 1, 1, 1, 1, 1, '可以借阅更多书籍' FROM dual WHERE NOT EXISTS (SELECT * FROM readers_permissions WHERE roles = 'VIP读者');

INSERT INTO readers_permissions (roles, can_login, can_reserve, can_view_announcement, can_view_help_articles,
                               can_reset_own_password, can_borrow_return_books, allow_edit_profile, notes)
SELECT '黑名单读者', 0, 0, 1, 1, 0, 0, 0, '因违规被限制权限' FROM dual WHERE NOT EXISTS (SELECT * FROM readers_permissions WHERE roles = '黑名单读者');

INSERT INTO readers_permissions (roles, can_login, can_reserve, can_view_announcement, can_view_help_articles,
                               can_reset_own_password, can_borrow_return_books, allow_edit_profile, notes)
SELECT '新读者', 1, 0, 1, 1, 1, 0, 1, '注册后需要验证身份' FROM dual WHERE NOT EXISTS (SELECT * FROM readers_permissions WHERE roles = '新读者');

-- 插入管理员数据 (密码为123456的哈希值)
INSERT INTO administrators (account, password, phone, email, sex, registered_at, permissions_id, is_active)
VALUES ('admin1', '$2a$10$N.zmdr9k7uOCQb376NoUnuTBpkTEpA1jMZNLTTx.mHzLXVVapHnLC', '13800000001', 'admin@library.com', '男', '2023-01-01 08:00:00', 1, 1),
       ('manager', '$2a$10$N.zmdr9k7uOCQb376NoUnuTBpkTEpA1jMZNLTTx.mHzLXVVapHnLC', '13800000002', 'manager@library.com', '女', '2023-01-02 09:00:00', 2, 1),
       ('librarian1', '$2a$10$N.zmdr9k7uOCQb376NoUnuTBpkTEpA1jMZNLTTx.mHzLXVVapHnLC', '13800000003', 'lib1@library.com', '男', '2023-01-03 10:00:00', 3, 1),
       ('librarian2', '$2a$10$N.zmdr9k7uOCQb376NoUnuTBpkTEpA1jMZNLTTx.mHzLXVVapHnLC', '13800000004', 'lib2@library.com', '女', '2023-01-04 11:00:00', 4, 1);

-- 插入图书分类数据
INSERT INTO book_categories (name, description)
VALUES ('文学', '包括小说、诗歌、散文等文学作品'),
       ('科技', '包括科学技术类书籍'),
       ('历史', '历史相关书籍'),
       ('计算机', '计算机科学与技术类书籍'),
       ('经济管理', '经济学与管理学相关书籍'),
       ('教育', '教育理论与实践相关书籍');

-- 插入书架数据
INSERT INTO book_shelves (shelf_code, location_description, capacity)
VALUES ('A-01', '一楼东侧第1排', 200),
       ('A-02', '一楼东侧第2排', 200),
       ('B-01', '一楼西侧第1排', 150),
       ('B-02', '一楼西侧第2排', 150),
       ('C-01', '二楼北侧第1排', 180),
       ('C-02', '二楼北侧第2排', 180);

-- 插入读者数据 (密码为123456的哈希值)
INSERT INTO readers (account, password, phone, email, sex, permissions_id, is_active)
VALUES ('reader1', '$2a$10$N.zmdr9k7uOCQb376NoUnuTBpkTEpA1jMZNLTTx.mHzLXVVapHnLC', '13900000001', 'reader1@example.com', '男', 1, 1),
       ('reader2', '$2a$10$N.zmdr9k7uOCQb376NoUnuTBpkTEpA1jMZNLTTx.mHzLXVVapHnLC', '13900000002', 'reader2@example.com', '女', 1, 1),
       ('vip1', '$2a$10$N.zmdr9k7uOCQb376NoUnuTBpkTEpA1jMZNLTTx.mHzLXVVapHnLC', '13900000003', 'vip1@example.com', '男', 2, 1),
       ('vip2', '$2a$10$N.zmdr9k7uOCQb376NoUnuTBpkTEpA1jMZNLTTx.mHzLXVVapHnLC', '13900000004', 'vip2@example.com', '女', 2, 1),
       ('blocked1', '$2a$10$N.zmdr9k7uOCQb376NoUnuTBpkTEpA1jMZNLTTx.mHzLXVVapHnLC', '13900000005', 'blocked1@example.com', '男', 3, 0),
       ('new1', '$2a$10$N.zmdr9k7uOCQb376NoUnuTBpkTEpA1jMZNLTTx.mHzLXVVapHnLC', '13900000006', 'new1@example.com', '女', 4, 1);

-- 插入图书数据
INSERT INTO books (isbn, title, author, publisher, edition, price, category_id, shelves_id, status, print_times, administrator_id)
VALUES ('9787111495482', '深入理解Java虚拟机', '周志明', '机械工业出版社', '第二版', 79.00, 4, 1, '可借阅', '2018年10月第15次印刷', 1),
       ('9787115428028', 'Python编程：从入门到实践', 'Eric Matthes', '人民邮电出版社', '第一版', 89.00, 4, 1, '可借阅', '2019年6月第10次印刷', 1),
       ('9787111213826', 'Java编程思想', 'Bruce Eckel', '机械工业出版社', '第四版', 108.00, 4, 1, '可借阅', '2019年1月第18次印刷', 1),
       ('9787115417305', 'C++ Primer中文版', 'Stanley B. Lippman', '人民邮电出版社', '第五版', 128.00, 4, 2, '可借阅', '2018年10月第8次印刷', 2),
       ('9787115547491', '算法导论', 'Thomas H.Cormen', '人民邮电出版社', '第三版', 128.00, 4, 2, '可借阅', '2019年12月第10次印刷', 2),
       ('9787544253994', '百年孤独', '加西亚·马尔克斯', '南海出版公司', '第一版', 55.00, 1, 3, '可借阅', '2017年8月第12次印刷', 3),
       ('9787020002207', '红楼梦', '曹雪芹', '人民文学出版社', '第一版', 59.70, 1, 3, '可借阅', '2018年6月第5次印刷', 3),
       ('9787544291170', '杀死一只知更鸟', '哈珀·李', '南海出版公司', '第一版', 32.00, 1, 3, '可借阅', '2017年3月第8次印刷', 3),
       ('9787201089126', '明朝那些事儿', '当年明月', '天津人民出版社', '第一版', 358.00, 3, 4, '可借阅', '2018年2月第25次印刷', 4),
       ('9787111624837', '经济学原理', '曼昆', '机械工业出版社', '第八版', 128.00, 5, 5, '可借阅', '2020年1月第1次印刷', 4);

-- 插入借阅记录
INSERT INTO book_loans (book_id, reader_id, administrator_id, start_time, end_time, status, price, renew_count, renew_duration)
VALUES (1, 1, 1, '2023-05-01 10:00:00', '2023-05-15 16:30:00', '已归还', 0.00, 0, 0),
       (2, 1, 1, '2023-05-10 14:20:00', NULL, '借阅中', 0.00, 0, 0),
       (3, 2, 2, '2023-05-05 09:15:00', NULL, '借阅中', 0.00, 1, 7),
       (6, 3, 3, '2023-05-08 11:30:00', '2023-05-18 15:45:00', '已归还', 0.00, 0, 0),
       (7, 4, 4, '2023-04-20 16:00:00', NULL, '逾期', 0.00, 0, 0),
       (9, 3, 3, '2023-05-12 10:25:00', NULL, '借阅中', 0.00, 0, 0);

-- 插入逾期记录
INSERT INTO fines_rules (name, day_min, day_max, fine_per_day, status, admin_id)
VALUES ('轻微逾期', 1, 7, 0.50, 1, 1),
       ('一般逾期', 8, 30, 1.00, 1, 1),
       ('严重逾期', 31, 90, 2.00, 1, 1),
       ('长期逾期', 91, 999, 5.00, 1, 1);

-- 插入逾期付款记录
INSERT INTO overdue_payments (reader_id, loan_order_id, overdue_days, amount, due_date, is_cleared, paid_amount, repaid_date, remarks)
VALUES (4, 5, 15, 15.00, '2023-06-05 00:00:00', 0, 0.00, NULL, '图书《红楼梦》逾期未还');

-- 插入支付账单数据
INSERT INTO payment_bills (amount_paid, total_paid, change_given, payment_date, admin_id, reader_id, remarks)
VALUES (50.00, 50.00, 0.00, '2023-05-20 14:30:00', 1, 1, '预存款'),
       (100.00, 100.00, 0.00, '2023-05-21 10:15:00', 2, 3, '预存款');

-- 插入帮助文章
INSERT INTO help_articles (title, content_html, content_text, administrator_id)
VALUES ('如何借阅图书', '<h1>如何借阅图书</h1><p>在本图书馆借阅图书非常简单，只需要按照以下步骤：</p><ol><li>登录您的账号</li><li>搜索您想借阅的图书</li><li>将图书添加到借阅车</li><li>确认借阅</li><li>到前台取书</li></ol>', '如何借阅图书\n在本图书馆借阅图书非常简单，只需要按照以下步骤：\n1. 登录您的账号\n2. 搜索您想借阅的图书\n3. 将图书添加到借阅车\n4. 确认借阅\n5. 到前台取书', 1),
       ('如何归还图书', '<h1>如何归还图书</h1><p>归还图书的方法：</p><ol><li>将图书带到图书馆前台</li><li>提供您的借阅证或账号信息</li><li>工作人员会为您办理归还手续</li></ol>', '如何归还图书\n归还图书的方法：\n1. 将图书带到图书馆前台\n2. 提供您的借阅证或账号信息\n3. 工作人员会为您办理归还手续', 1),
       ('逾期规则说明', '<h1>逾期规则说明</h1><p>为了保证图书资源的合理利用，我们对逾期未还的图书收取一定的费用：</p><ul><li>逾期1-7天：每天0.5元</li><li>逾期8-30天：每天1元</li><li>逾期31-90天：每天2元</li><li>逾期超过90天：每天5元</li></ul>', '逾期规则说明\n为了保证图书资源的合理利用，我们对逾期未还的图书收取一定的费用：\n· 逾期1-7天：每天0.5元\n· 逾期8-30天：每天1元\n· 逾期31-90天：每天2元\n· 逾期超过90天：每天5元', 2);

-- 插入公告数据
INSERT INTO announcements (title, content, administrator_id)
VALUES ('图书馆开放时间调整通知', '尊敬的读者：\n自2023年6月1日起，图书馆开放时间调整为：\n周一至周五：8:00-22:00\n周六至周日：9:00-21:00\n特此通知。', 1),
       ('新书到馆通知', '尊敬的读者：\n本馆已新增200余册各类图书，包括最新的计算机技术、文学作品等，欢迎前来借阅。', 2),
       ('系统维护通知', '尊敬的读者：\n本图书馆管理系统将于2023年6月10日凌晨2:00-5:00进行系统维护，期间所有在线服务暂停使用。给您带来不便，敬请谅解。', 1);

-- 管理员系统日志
INSERT INTO system_Admin_logs (level, message, context, ip, Admin_user_agent, Admin_id, created_at)
VALUES ('INFO', '管理员登录系统', '{"action":"login","result":"success"}', '192.168.1.100', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)', 1, '2023-05-25 08:30:00'),
       ('INFO', '创建新用户账号', '{"action":"create_user","userId":6,"result":"success"}', '192.168.1.100', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)', 1, '2023-05-25 09:15:00'),
       ('INFO', '添加新图书', '{"action":"add_book","bookId":10,"result":"success"}', '192.168.1.101', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)', 2, '2023-05-25 10:30:00'),
       ('WARNING', '尝试重置密码失败', '{"action":"reset_password","userId":5,"result":"failed","reason":"权限不足"}', '192.168.1.102', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)', 3, '2023-05-25 11:45:00');

-- 读者系统日志
INSERT INTO system_readers_logs (level, message, context, ip, readers_agent, readers_id, created_at)
VALUES ('INFO', '读者登录系统', '{"action":"login","result":"success"}', '192.168.1.200', 'Mozilla/5.0 (iPhone; CPU iPhone OS 13_2_3)', 1, '2023-05-25 14:20:00'),
       ('INFO', '搜索图书', '{"action":"search","keyword":"Java","result":"success"}', '192.168.1.200', 'Mozilla/5.0 (iPhone; CPU iPhone OS 13_2_3)', 1, '2023-05-25 14:25:00'),
       ('INFO', '预约图书', '{"action":"reserve","bookId":3,"result":"success"}', '192.168.1.201', 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)', 2, '2023-05-25 15:10:00'),
       ('WARNING', '尝试借阅超过限额', '{"action":"borrow","bookId":5,"result":"failed","reason":"超过最大借阅数量"}', '192.168.1.202', 'Mozilla/5.0 (Linux; Android 10)', 3, '2023-05-25 16:30:00');
