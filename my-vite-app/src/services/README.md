# services 目录约定

1. 分层规则

- services 根目录只放领域目录与共享目录，不直接新增业务 service 文件。
- 第一层目录按业务领域划分，例如 admin、auth、content、permissions、search、users。
- 当某个领域目录继续变宽时，优先按职责或使用者拆到第二层，例如 admin/ai、admin/content、auth/mfa。

2. 命名规则

- 文件名统一使用小驼峰，后缀固定为 Service.ts 或 Service.test.ts，例如 accountService.ts、postSummaryAdminService.ts。
- 目录名统一使用语义清晰的领域名，优先选择 users、permissions、search、initialization、siteConfig 这类可直读名称，避免 access、account、retrieval 这类边界模糊的命名。
- 工具类与错误类放 shared，名称保持语义前缀，例如 serviceUrlUtils.ts、apiError.ts。
- 避免同层同时混用领域名、页面名、临时名；文件名应直接表达接口职责。

3. 何时允许新增子目录

- 同一目录中的业务 service 文件达到 8 到 12 个，或已经出现两个以上清晰子主题时，可以新增子目录。
- 新增子目录前，先确认它表达的是单一维度，不要同时混用业务领域、调用身份和实现细节。
- 如果只有 1 到 2 个文件会落入新目录，先不要拆，继续保留在当前领域目录。