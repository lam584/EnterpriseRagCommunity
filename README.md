# 基于Qwen3的企业知识检索增强社区系统

## 一、项目概述
本项目为毕业设计的实现，前后端分离。  
后端基于 Spring Boot + MySQL + Flyway，提供 RESTful API；  
前端基于 Vite + React + TypeScript + Tailwind CSS，提供 SPA 界面。

---

## 二、技术栈一览

### 2.1 后端技术栈

| 分类           | 组件/框架               | 版本      | 说明                                    |
|--------------|-----------------------|---------|---------------------------------------|
| 核心框架         | Spring Boot           | 3.5.7   | 核心应用容器与启动器                          |
| 构建工具         | Gradle                | 8.12.1  | 项目构建与依赖管理                          |
| JDK           | Eclipse Temurin OpenJDK | 25      | Java 运行时环境                           |
| ORM & 数据访问    | Spring Data JPA       | 3.5.7   | 主流 ORM 框架，自动生成 Repository 接口         |
|                | MyBatis               | 3.0.3   | 辅助 SQL 映射，用于复杂查询                      |
| 数据库 & 迁移     | MySQL                 | 8.4.7   | 关系型数据库                                |
|                | Flyway                | 11.7.2  | 数据库版本与迁移管理                          |
| 安全 & 验证       | Spring Security       | 3.5.7   | 认证和授权                                |
|                | Spring Validation     | 3.5.7   | @Valid 注解形式的参数校验                   |
| 嵌入式服务器       | Tomcat Embed          | 10.1.48 | 内置 Servlet 容器                            |
| 其它辅助         | Lombok                | 1.18.42 | 注解简化 Getter/Setter、构造器等样板代码            |
| 开发工具 (仅 dev) | Spring DevTools       | 3.5.7   | 热重载支持，仅开发环境启用                     |

### 2.2 前端技术栈

| 分类         | 组件/框架                 | 版本       | 说明                                    |
|------------|-------------------------|----------|---------------------------------------|
| 核心框架       | React                   | 18.3.1   | 构建单页应用                              |
|            | TypeScript              | 5.7.2    | 强类型支持                                |
| 构建与打包      | Vite                    | 6.2.6    | 极速启动与构建                             |
|            | SWC (via plugin)        | —        | 编译/转译工具                              |
| UI 样式       | Tailwind CSS            | 3.4.17   | 原子化 CSS 框架                           |
|            | Shadcn UI               | 0.9.5    | 组件库（基于 Radix）                    |
| 网络请求       | axios                   | 1.9.0    | HTTP 客户端                              |
| 路由管理       | react-router-dom        | 7.6.1    | 前端路由                               |
| 图标库        | Font Awesome            | 6.7.2    | 通用图标                                |
|            | React Icons             | 5.5.0    | React 版图标组件                           |
| 开发工具       | ESLint                  | 9.21.0   | 代码规范校验                             |
|            | PostCSS & Autoprefixer | 8.5.3 / 10.4.21 | Tailwind 依赖                            |
| Node 版本    | Node.js                 | 20.12.0  | 本地开发环境                             |
| 包管理        | npm                     | 10.5.0   | 依赖安装与脚本                             |


---

## 三、简化版项目结构

```
EnterpriseRagCommunity
├─ build.gradle                      # 后端构建脚本
├─ settings.gradle
├─ gradlew / gradlew.bat             # Gradle Wrapper
├─ my-vite-app/                      # ── 前端模块 (Vite + React + TS)
│   ├─ public/                       # 静态资源直拷目录
│   ├─ src/
│   │   ├─ assets/                   # 全局样式、图片、字体
│   │   ├─ components/               # UI 组件
│   │   ├─ pages/                    # 路由页面
│   │   ├─ services/                 # API 调用封装
│   │   ├─ contexts/                 # Context 管理（Auth 等）
│   │   ├─ utils/                    # 工具函数
│   │   ├─ main.tsx / App.tsx        # 入口与根组件
│   │   └─ vite.config.ts            # 别名 & 插件配置
│   └─ package.json / tsconfig*.json
├─ src/                              # 后端源码
│   ├─ main/
│   │   ├─ java/com/example/...      # package：config/ controller/ service/ repository/ entity/ dto/ utils
│   │   └─ resources/
│   │       ├─ db/migration/         # Flyway 脚本 V1__…sql
│   │       ├─ static/               # 打包后前端文件（由 gradle copy）
│   │       └─ application.properties
│   └─ test/java/com/example/...     # 单元测试
└─ README.md, .gitignore, …          # 根目录说明及工具文件
```

---

## 四、构建与运行

1. 克隆项目  

   `git clone  https://github.com/lam584/EnterpriseRagCommunity.git && cd EnterpriseRagCommunity`


2. 在 `resources/application.properties` 中配置数据库的账号和密码， 示例：
    ```
    spring.datasource.url=jdbc:mysql://localhost:3306/test24031700?createDatabaseIfNotExist=true
    spring.datasource.username=root
    spring.datasource.password=password
    ```

3. 后端打包并运行（含前端打包）
   ```bash
   ./gradlew build     # 会触发 my-vite-app npm run build
   ./gradlew bootRun
   ```
   访问 http://localhost:8080


4. 本地前端调试（可选）
   ```bash
   cd my-vite-app
   npm install
   npm run dev         # 启动 HMR 开发服务器
   ```


---

## 五、注意事项

1. Flyway 脚本请按 V{n}__xxx.sql 递增，不要修改已执行版本
2. 生产环境静态资源通过 Vite Manifest 注入，确保 `my-vite-app` 每次 build 更新
3. 环境变量 & 配置（见 application.properties）可覆盖数据库、上下文路径等



---

# 项目完整目录结构及注释
``` 
📁 .
├─ 📁 gradle
│   └─ 📁 wrapper
│       ├─ 📄 gradle-wrapper.jar
│       └─ 📄 gradle-wrapper.properties                                   - 配置文件
├─ 📁 my-vite-app
│   ├─ 📁 public                                                          - 公共静态资源目录;不经打包,直接拷贝至 dist 根目录;适合 favicon、HTML 模板等
│   │   └─ 📄 main.tsx                                                    - React TypeScript 组件文件
│   ├─ 📁 src                                                             - 前端源代码根目录;存放应用逻辑/组件/样式/类型声明/工具函数
│   │   ├─ 📁 assets                                                      - 通用静态资源目录;fonts:自定义字体(woff/ttf等)通过 @font-face 加载;images:位图图片;svg:矢量图标或插画;styles:全局 CSS(重置/变量/mixins等)
│   │   │   ├─ 📁 fonts                                                   - 字体文件目录(woff/woff2/ttf 等);兼容多浏览器
│   │   │   │   └─ 📄 fa-solid-900.woff2                                  - 字体文件
│   │   │   ├─ 📁 images                                                  - 位图图片目录(png/jpg/gif 等),组件中可直接 import
│   │   │   ├─ 📁 styles                                                  - 全局样式目录(reset.css/variables.css/mixins.css 等);在入口统一引入
│   │   │   │   └─ 📄 css2
│   │   │   └─ 📁 svg                                                     - SVG 矢量图目录;支持 SVGR 转为 React 组件或作为 img src
│   │   ├─ 📁 components                                                  - 纯 UI 组件目录(.tsx+.module.css 同名放置);关注展示无需业务逻辑
│   │   │   ├─ 📁 account-management
│   │   │   │   ├─ 📄 account-management.tsx                              - React TypeScript 组件文件
│   │   │   │   ├─ 📄 ChangePassword.tsx
│   │   │   │   ├─ 📄 EditProfile.tsx
│   │   │   │   └─ 📄 Logout.tsx
│   │   │   ├─ 📁 Admin-management
│   │   │   │   ├─ 📄 AddAdmin.tsx                                        - React TypeScript 组件文件
│   │   │   │   ├─ 📄 AdminManagement.tsx
│   │   │   │   ├─ 📄 AdminPermissions.tsx
│   │   │   │   ├─ 📄 DeleteAdmin.tsx
│   │   │   │   └─ 📄 EditAdmin.tsx
│   │   │   ├─ 📁 comment-management
│   │   │   │   ├─ 📄 CommentManagement.tsx                               - React TypeScript 组件文件
│   │   │   │   ├─ 📄 DeleteComments.tsx
│   │   │   │   ├─ 📄 ReviewComments.tsx
│   │   │   │   └─ 📄 SearchComments.tsx
│   │   │   ├─ 📁 help-center
│   │   │   │   └─ 📄 HelpCenter.tsx                                      - React TypeScript 组件文件
│   │   │   ├─ 📁 login
│   │   │   │   ├─ 📄 AdminSetup.tsx                                      - React TypeScript 组件文件
│   │   │   │   ├─ 📄 Login.tsx
│   │   │   │   └─ 📄 Register.tsx
│   │   │   ├─ 📁 new-management
│   │   │   │   ├─ 📄 AddNews.tsx                                         - React TypeScript 组件文件
│   │   │   │   ├─ 📄 DeleteNews.tsx
│   │   │   │   ├─ 📄 EditNews.tsx
│   │   │   │   ├─ 📄 NewManagement.tsx
│   │   │   │   ├─ 📄 SearchNews.tsx
│   │   │   │   └─ 📄 TopicManage.tsx
│   │   │   ├─ 📁 news
│   │   │   │   ├─ 📄 NewsCard.tsx                                        - React TypeScript 组件文件
│   │   │   │   ├─ 📄 NewsList.tsx
│   │   │   │   └─ 📄 TopicList.tsx
│   │   │   ├─ 📁 stats-management
│   │   │   │   ├─ 📄 BookStats.tsx                                       - React TypeScript 组件文件
│   │   │   │   ├─ 📄 FineStats.tsx
│   │   │   │   ├─ 📄 InventoryStats.tsx
│   │   │   │   ├─ 📄 ReaderStats.tsx
│   │   │   │   └─ 📄 stats-management.tsx
│   │   │   ├─ 📁 system-management
│   │   │   │   ├─ 📄 AddAdmin.tsx                                        - React TypeScript 组件文件
│   │   │   │   ├─ 📄 AdminPermissions.tsx
│   │   │   │   ├─ 📄 BackupRestore.tsx
│   │   │   │   ├─ 📄 NewAddAdmin.tsx
│   │   │   │   ├─ 📄 ReaderPermissions.tsx
│   │   │   │   ├─ 📄 ResetPassword.tsx
│   │   │   │   ├─ 📄 system-management.tsx
│   │   │   │   └─ 📄 SystemLogs.tsx
│   │   │   ├─ 📁 ui                                                      - Shadcn UI 组件目录，包含全局样式和组件
│   │   │   │   ├─ 📄 alert.tsx                                           - React TypeScript 组件文件
│   │   │   │   ├─ 📄 avatar.tsx
│   │   │   │   ├─ 📄 badge.tsx
│   │   │   │   ├─ 📄 button.tsx
│   │   │   │   ├─ 📄 card.tsx
│   │   │   │   ├─ 📄 checkbox.tsx
│   │   │   │   ├─ 📄 input.tsx
│   │   │   │   ├─ 📄 label.tsx
│   │   │   │   ├─ 📄 pagination.tsx
│   │   │   │   ├─ 📄 scroll-area.tsx
│   │   │   │   ├─ 📄 select.tsx
│   │   │   │   ├─ 📄 skeleton.tsx
│   │   │   │   ├─ 📄 tabs.tsx
│   │   │   │   └─ 📄 textarea.tsx
│   │   │   └─ 📁 user-management
│   │   │       ├─ 📄 AddUser.tsx                                         - React TypeScript 组件文件
│   │   │       ├─ 📄 DeleteUser.tsx
│   │   │       ├─ 📄 EditUser.tsx
│   │   │       ├─ 📄 SearchUser.tsx
│   │   │       └─ 📄 UserManagement.tsx
│   │   ├─ 📁 contexts
│   │   │   └─ 📄 AuthContext.tsx                                         - React TypeScript 组件文件
│   │   ├─ 📁 lib
│   │   │   └─ 📄 utils.ts                                                - TypeScript 文件
│   │   ├─ 📁 mockData
│   │   │   └─ 📄 newsData.ts                                             - TypeScript 文件
│   │   ├─ 📁 pages                                                       - 路由级页面组件目录;每个子目录对应一个页面或路由
│   │   │   ├─ 📁 news
│   │   │   │   ├─ 📄 NewsDetailPage.tsx                                  - React TypeScript 组件文件
│   │   │   │   └─ 📄 NewsHomePage.tsx
│   │   │   └─ 📄 NewsSystemLayout.tsx                                    - React TypeScript 组件文件
│   │   ├─ 📁 services                                                    - 后端 API 封装目录;基于 fetch/axios 统一处理请求/响应/错误
│   │   │   ├─ 📄 accountService.ts                                       - TypeScript 文件
│   │   │   ├─ 📄 adminPermissionService.ts
│   │   │   ├─ 📄 adminPermissionService_1.ts
│   │   │   ├─ 📄 adminService.ts
│   │   │   ├─ 📄 adminService_1.ts
│   │   │   ├─ 📄 adminService_2.ts
│   │   │   ├─ 📄 authService.ts
│   │   │   ├─ 📄 mockAdminService.ts
│   │   │   ├─ 📄 MockUserService.ts
│   │   │   ├─ 📄 NewsService.ts
│   │   │   ├─ 📄 TopicService.ts
│   │   │   ├─ 📄 UserRoleService.ts
│   │   │   ├─ 📄 UserRoleService_1.ts
│   │   │   ├─ 📄 UserRoleService_2.ts
│   │   │   ├─ 📄 UserRoleService_3.ts
│   │   │   ├─ 📄 UserService.ts
│   │   │   ├─ 📄 UserService_1.ts
│   │   │   ├─ 📄 UserService_2.ts
│   │   │   └─ 📄 UserService_3.ts
│   │   ├─ 📁 types                                                       - 全局 TypeScript 类型声明目录(interface/type/enum 等)
│   │   │   ├─ 📄 admin.ts                                                - TypeScript 文件
│   │   │   └─ 📄 news.ts
│   │   ├─ 📁 utils                                                       - 工具函数目录(日期格式化/深拷贝/节流防抖/校验等纯函数)
│   │   │   └─ 📄 csrfUtils.ts                                            - TypeScript 文件
│   │   ├─ 📄 App.tsx                                                     - 根组件;配置路由/Provider/顶级布局等全局逻辑
│   │   ├─ 📄 bootstrap.js                                                - Vite 启动脚本，初始化 Vue 应用和全局配置
│   │   ├─ 📄 css2
│   │   ├─ 📄 icons.ts                                                    - TypeScript 文件
│   │   ├─ 📄 index.js                                                    - Vite 入口文件，挂载 Vue 应用到 #app 元素
│   │   ├─ 📄 main.tsx                                                    - 入口挂载文件;调用 ReactDOM 将 <App/> 挂载到 #root 并初始化 HMR 等
│   │   ├─ 📄 temp-file-check-icons.tsx                                   - React TypeScript 组件文件
│   │   ├─ 📄 vite-env.d.ts                                               - Vite 环境类型声明;支持 import.meta.env 及静态资源导入
│   │   └─ 📄 vue.global.js                                               - Vue 全局配置文件，定义 Vue 组件全局注册和配置
│   ├─ 📄 .gitignore                                                      - Git 忽略文件配置，指定不需要纳入版本控制的文件类型
│   ├─ 📄 .shadcnrc
│   ├─ 📄 eslint.config.js                                                - ESLint 配置文件，配置前端格式化和代码校验规则
│   ├─ 📄 index.html                                                      - Vite HTML 模板，应用入口文件，用来挂载 #root 并注入 script/style
│   ├─ 📄 package-lock.json                                               - NPM 依赖锁定文件，确保安装一致性
│   ├─ 📄 package.json                                                    - NPM 包管理配置文件，定义依赖和脚本命令
│   ├─ 📄 postcss.config.js
│   ├─ 📄 README.md                                                       - 前端项目说明文档，介绍启动、开发及发布流程
│   ├─ 📄 tailwind.config.js
│   ├─ 📄 tsconfig.app.json                                               - Vite 前端 TypeScript 项目编译配置
│   ├─ 📄 tsconfig.json                                                   - TypeScript 通用编译选项配置
│   ├─ 📄 tsconfig.node.json                                              - Node 运行时 TypeScript 配置，用于脚本/工具调用
│   └─ 📄 vite.config.ts                                                  - Vite 配置文件，包含开发服务器、插件和构建选项
├─ 📁 src
│   ├─ 📁 main                                                            - 主要源代码和资源文件目录
│   │   ├─ 📁 java                                                        - Java 源代码目录
│   │   │   ├─ 📁 com
│   │   │   │   └─ 📁 example
│   │   │   │       └─ 📁 EnterpriseRagCommunity
│   │   │   │           ├─ 📁 config                                      - 配置类目录，存放 Spring 配置、Bean 定义等
│   │   │   │           │   ├─ 📄 AdminSetupManager.java
│   │   │   │           │   ├─ 📄 FreemarkerGlobalConfig.java
│   │   │   │           │   └─ 📄 SecurityConfig.java
│   │   │   │           ├─ 📁 controller                                  - 控制器层，处理 HTTP 请求
│   │   │   │           │   ├─ 📄 AuthController.java
│   │   │   │           │   ├─ 📄 GlobalExceptionHandler.java
│   │   │   │           │   ├─ 📄 LoginPageController.java
│   │   │   │           │   ├─ 📄 UserController.java
│   │   │   │           │   └─ 📄 UserRoleController.java
│   │   │   │           ├─ 📁 dto                                         - 数据传输对象目录
│   │   │   │           │   ├─ 📁 base
│   │   │   │           │   │   ├─ 📄 BaseDTO.java
│   │   │   │           │   │   └─ 📄 BaseIdDTO.java
│   │   │   │           │   ├─ 📁 content
│   │   │   │           │   │   ├─ 📄 BoardDTO.java
│   │   │   │           │   │   ├─ 📄 CommentDTO.java
│   │   │   │           │   │   ├─ 📄 FavoriteDTO.java
│   │   │   │           │   │   ├─ 📄 HotScoreDTO.java
│   │   │   │           │   │   ├─ 📄 PostAttachmentDTO.java
│   │   │   │           │   │   ├─ 📄 PostDTO.java
│   │   │   │           │   │   ├─ 📄 PostTagDTO.java
│   │   │   │           │   │   ├─ 📄 PostVersionDTO.java
│   │   │   │           │   │   ├─ 📄 ReactionDTO.java
│   │   │   │           │   │   ├─ 📄 ReportDTO.java
│   │   │   │           │   │   └─ 📄 TagDTO.java
│   │   │   │           │   ├─ 📁 identity
│   │   │   │           │   │   ├─ 📄 AuthSessionDTO.java
│   │   │   │           │   │   ├─ 📄 EmailVerificationDTO.java
│   │   │   │           │   │   ├─ 📄 PasswordResetTokenDTO.java
│   │   │   │           │   │   ├─ 📄 PermissionDTO.java
│   │   │   │           │   │   ├─ 📄 RolePermissionDTO.java
│   │   │   │           │   │   ├─ 📄 TenantDTO.java
│   │   │   │           │   │   ├─ 📄 TotpSecretDTO.java
│   │   │   │           │   │   ├─ 📄 UserDTO.java
│   │   │   │           │   │   └─ 📄 UserRoleDTO.java
│   │   │   │           │   ├─ 📁 misc
│   │   │   │           │   │   ├─ 📄 FileAssetDTO.java
│   │   │   │           │   │   ├─ 📄 NotificationDTO.java
│   │   │   │           │   │   └─ 📄 UserSettingDTO.java
│   │   │   │           │   ├─ 📁 moderation
│   │   │   │           │   │   ├─ 📄 ModerationActionDTO.java
│   │   │   │           │   │   ├─ 📄 ModerationQueueDTO.java
│   │   │   │           │   │   ├─ 📄 ModerationRuleDTO.java
│   │   │   │           │   │   └─ 📄 RiskLabelingDTO.java
│   │   │   │           │   ├─ 📁 qa
│   │   │   │           │   │   ├─ 📄 AnswerCitationDTO.java
│   │   │   │           │   │   ├─ 📄 QaMessageDTO.java
│   │   │   │           │   │   ├─ 📄 QaSessionDTO.java
│   │   │   │           │   │   └─ 📄 QaTurnDTO.java
│   │   │   │           │   ├─ 📁 rag
│   │   │   │           │   │   ├─ 📄 DocumentChunkDTO.java
│   │   │   │           │   │   ├─ 📄 DocumentDTO.java
│   │   │   │           │   │   ├─ 📄 GenerationJobDTO.java
│   │   │   │           │   │   ├─ 📄 PromptDTO.java
│   │   │   │           │   │   └─ 📄 VectorIndexDTO.java
│   │   │   │           │   ├─ 📁 request
│   │   │   │           │   │   ├─ 📄 ChangePasswordRequest.java
│   │   │   │           │   │   ├─ 📄 CreateCommentRequest.java
│   │   │   │           │   │   ├─ 📄 CreatePostRequest.java
│   │   │   │           │   │   ├─ 📄 LoginRequest.java
│   │   │   │           │   │   ├─ 📄 PageRequest.java
│   │   │   │           │   │   ├─ 📄 QaQueryRequest.java
│   │   │   │           │   │   └─ 📄 RegisterRequest.java
│   │   │   │           │   ├─ 📁 response
│   │   │   │           │   │   ├─ 📄 ApiResponse.java
│   │   │   │           │   │   ├─ 📄 AuthResponse.java
│   │   │   │           │   │   ├─ 📄 PageResponse.java
│   │   │   │           │   │   ├─ 📄 QaQueryResponse.java
│   │   │   │           │   │   └─ 📄 StatsResponse.java
│   │   │   │           │   ├─ 📄 DTO_GENERATION_REPORT.md
│   │   │   │           │   ├─ 📄 DTO_LAYER_SUMMARY.md
│   │   │   │           │   ├─ 📄 DTO_QUICK_REFERENCE.md
│   │   │   │           │   └─ 📄 README.md                               - 项目说明文档，介绍项目背景、使用方法及其他信息
│   │   │   │           ├─ 📁 entity                                      - 实体类层，存放与数据库表对应的实体
│   │   │   │           │   ├─ 📁 base
│   │   │   │           │   │   ├─ 📄 BaseAuditEntity.java
│   │   │   │           │   │   └─ 📄 BaseIdEntity.java
│   │   │   │           │   ├─ 📁 content
│   │   │   │           │   │   ├─ 📄 Board.java
│   │   │   │           │   │   ├─ 📄 Comment.java
│   │   │   │           │   │   ├─ 📄 Favorite.java
│   │   │   │           │   │   ├─ 📄 HotScore.java
│   │   │   │           │   │   ├─ 📄 Post.java
│   │   │   │           │   │   ├─ 📄 PostAttachment.java
│   │   │   │           │   │   ├─ 📄 PostTag.java
│   │   │   │           │   │   ├─ 📄 PostTagId.java
│   │   │   │           │   │   ├─ 📄 PostVersion.java
│   │   │   │           │   │   ├─ 📄 Reaction.java
│   │   │   │           │   │   ├─ 📄 Report.java
│   │   │   │           │   │   └─ 📄 Tag.java
│   │   │   │           │   ├─ 📁 enums
│   │   │   │           │   │   └─ 📄 AccountStatus.java
│   │   │   │           │   ├─ 📁 identity
│   │   │   │           │   │   ├─ 📄 AuthSession.java
│   │   │   │           │   │   ├─ 📄 EmailVerification.java
│   │   │   │           │   │   ├─ 📄 PasswordResetToken.java
│   │   │   │           │   │   ├─ 📄 Permission.java
│   │   │   │           │   │   ├─ 📄 RolePermission.java
│   │   │   │           │   │   ├─ 📄 RolePermissionId.java
│   │   │   │           │   │   ├─ 📄 Tenant.java
│   │   │   │           │   │   ├─ 📄 TotpSecret.java
│   │   │   │           │   │   ├─ 📄 User.java
│   │   │   │           │   │   └─ 📄 UserRole.java
│   │   │   │           │   ├─ 📁 misc
│   │   │   │           │   │   ├─ 📄 FileAsset.java
│   │   │   │           │   │   ├─ 📄 Notification.java
│   │   │   │           │   │   └─ 📄 UserSetting.java
│   │   │   │           │   ├─ 📁 moderation
│   │   │   │           │   │   ├─ 📄 ModerationAction.java
│   │   │   │           │   │   ├─ 📄 ModerationQueue.java
│   │   │   │           │   │   ├─ 📄 ModerationRule.java
│   │   │   │           │   │   └─ 📄 RiskLabeling.java
│   │   │   │           │   ├─ 📁 qa
│   │   │   │           │   │   ├─ 📄 AnswerCitation.java
│   │   │   │           │   │   ├─ 📄 QaMessage.java
│   │   │   │           │   │   ├─ 📄 QaSession.java
│   │   │   │           │   │   └─ 📄 QaTurn.java
│   │   │   │           │   └─ 📁 rag
│   │   │   │           │       ├─ 📄 Document.java
│   │   │   │           │       ├─ 📄 DocumentChunk.java
│   │   │   │           │       ├─ 📄 GenerationJob.java
│   │   │   │           │       ├─ 📄 Prompt.java
│   │   │   │           │       └─ 📄 VectorIndex.java
│   │   │   │           ├─ 📁 repository                                  - 数据访问层（DAO）
│   │   │   │           │   ├─ 📁 content
│   │   │   │           │   │   ├─ 📄 BoardRepository.java
│   │   │   │           │   │   ├─ 📄 CommentRepository.java
│   │   │   │           │   │   ├─ 📄 FavoriteRepository.java
│   │   │   │           │   │   ├─ 📄 HotScoreRepository.java
│   │   │   │           │   │   ├─ 📄 PostAttachmentRepository.java
│   │   │   │           │   │   ├─ 📄 PostRepository.java
│   │   │   │           │   │   ├─ 📄 PostTagRepository.java
│   │   │   │           │   │   ├─ 📄 PostVersionRepository.java
│   │   │   │           │   │   ├─ 📄 ReactionRepository.java
│   │   │   │           │   │   ├─ 📄 ReportRepository.java
│   │   │   │           │   │   └─ 📄 TagRepository.java
│   │   │   │           │   ├─ 📁 identity
│   │   │   │           │   │   ├─ 📄 AuthSessionRepository.java
│   │   │   │           │   │   ├─ 📄 EmailVerificationRepository.java
│   │   │   │           │   │   ├─ 📄 PasswordResetTokenRepository.java
│   │   │   │           │   │   ├─ 📄 PermissionRepository.java
│   │   │   │           │   │   ├─ 📄 RolePermissionRepository.java
│   │   │   │           │   │   ├─ 📄 TenantRepository.java
│   │   │   │           │   │   ├─ 📄 TotpSecretRepository.java
│   │   │   │           │   │   ├─ 📄 UserRepository.java
│   │   │   │           │   │   └─ 📄 UserRoleRepository.java
│   │   │   │           │   ├─ 📁 misc
│   │   │   │           │   │   ├─ 📄 FileAssetRepository.java
│   │   │   │           │   │   ├─ 📄 NotificationRepository.java
│   │   │   │           │   │   └─ 📄 UserSettingRepository.java
│   │   │   │           │   ├─ 📁 moderation
│   │   │   │           │   │   ├─ 📄 ModerationActionRepository.java
│   │   │   │           │   │   ├─ 📄 ModerationQueueRepository.java
│   │   │   │           │   │   ├─ 📄 ModerationRuleRepository.java
│   │   │   │           │   │   └─ 📄 RiskLabelingRepository.java
│   │   │   │           │   ├─ 📁 qa
│   │   │   │           │   │   ├─ 📄 AnswerCitationRepository.java
│   │   │   │           │   │   ├─ 📄 QaMessageRepository.java
│   │   │   │           │   │   ├─ 📄 QaSessionRepository.java
│   │   │   │           │   │   └─ 📄 QaTurnRepository.java
│   │   │   │           │   ├─ 📁 rag
│   │   │   │           │   │   ├─ 📄 DocumentChunkRepository.java
│   │   │   │           │   │   ├─ 📄 DocumentRepository.java
│   │   │   │           │   │   ├─ 📄 GenerationJobRepository.java
│   │   │   │           │   │   ├─ 📄 PromptRepository.java
│   │   │   │           │   │   └─ 📄 VectorIndexRepository.java
│   │   │   │           │   ├─ 📄 BaseRepository.java
│   │   │   │           │   └─ 📄 REPOSITORY_LAYER_SUMMARY.md
│   │   │   │           ├─ 📁 service                                     - 业务逻辑层
│   │   │   │           │   ├─ 📁 impl
│   │   │   │           │   │   └─ 📄 UserRoleServiceImpl.java
│   │   │   │           │   ├─ 📄 AdministratorService.java
│   │   │   │           │   ├─ 📄 UserRoleService.java
│   │   │   │           │   ├─ 📄 UserService.java
│   │   │   │           │   └─ 📄 ViteManifestService.java
│   │   │   │           ├─ 📁 utils                                       - 工具类目录
│   │   │   │           │   └─ 📄 PasswordEncoderUtil.java
│   │   │   │           └─ 📄 EnterpriseRagCommunityApplication.java
│   │   │   ├─ 📄 DirectoryTreeMarkdownGenerator.java
│   │   │   └─ 📄 SimpleDirectoryTreeMarkdown.java
│   │   └─ 📁 resources                                                   - 资源文件目录，存放配置文件、模板和静态资源
│   │       ├─ 📁 db
│   │       │   └─ 📁 migration                                           - Flyway 数据库迁移脚本
│   │       │       └─ 📄 V1__EnterpriseRagCommunity.sql
│   │       └─ 📄 application.properties                                  - Spring Boot 配置文件
│   └─ 📁 test                                                            - 测试代码目录
│       └─ 📁 java                                                        - Java 测试代码目录
│           └─ 📁 com
│               └─ 📁 example
│                   └─ 📁 EnterpriseRagCommunity
│                       └─ 📄 EnterpriseRagCommunityApplicationTests.java
├─ 📄 .gitattributes
├─ 📄 .gitignore
├─ 📄 build.gradle                                                        - Gradle 构建脚本
├─ 📄 gradlew
├─ 📄 gradlew.bat                                                         - 批处理脚本
├─ 📄 LOGIN_REFACTOR_README.md
├─ 📄 LOGIN_TEST_GUIDE.md
├─ 📄 package-lock.json
├─ 📄 README.md
├─ 📄 settings.gradle
├─ 📄 tree.md
└─ 📄 任务书+开题报告+功能清单.txt

```  