# 项目技术栈

## 后端
| 组件             | 技术                        | 版本       |
|------------------|-----------------------------|----------|
| 框架             | Spring Boot                 | 3.5.0    |
| 构建工具         | Gradle                      | 8.12.1   |
| 模板引擎         | FreeMarker                  | 2.3.34   |
| 数据库           | MySQL                       | 8.0.33   |
| 数据库迁移工具   | Flyway                      | 10.20.1  |
| JDK              | Eclipse Temurin OpenJDK     | 23.0.2+7 |

## 前端
| 分类 | 技术 | 版本 | 说明 |
|------|------|------|------|
| **核心框架** | React | 18.3.1 | 用户界面构建库 |
| | TypeScript | 5.7.2 | JavaScript 类型扩展 |
| **构建与环境** | Vite | 6.2.6 | 现代前端构建工具 |
| | SWC | - | 快速 JavaScript/TypeScript 编译器 |
| | Node.js | 20.12.0 | JavaScript 运行环境 |
| | npm | 10.5.0 | 包管理工具 |
| **UI 组件与样式** | Tailwind CSS | 3.4.17 | 实用优先的 CSS 框架 |
| | Shadcn UI | 0.9.5 | 基于 Tailwind 的组件库 |
| **图标与资源** | Font Awesome | 6.7.2 | 矢量图标库 |
| | React Icons | 5.5.0 | 流行图标集的 React 组件 |
| **插件与工具** | @vitejs/plugin-react-swc | 3.8.0 | Vite 的 React SWC 插件 |

> 注: 所有依赖版本在 `package.json` 中定义，实际使用时会根据语义化版本控制自动更新补丁版本。


### 开发工具
- IDE：IntelliJ IDEA 2025.1.1.1


# 项目目录结构
```  

📁 .
├─ 📁 gradle
│   └─ 📁 wrapper
│       ├─ 📄 gradle-wrapper.jar
│       └─ 📄 gradle-wrapper.properties                            - 配置文件
├─ 📁 my-vite-app
│   ├─ 📁 public                                                   - 公共静态资源目录;不经打包,直接拷贝至 dist 根目录;适合 favicon、HTML 模板等
│   │   └─ 📄 main.tsx                                             - React TypeScript 组件文件
│   ├─ 📁 src                                                      - 前端源代码根目录;存放应用逻辑/组件/样式/类型声明/工具函数
│   │   ├─ 📁 assets                                               - 通用静态资源目录;fonts:自定义字体(woff/ttf等)通过 @font-face 加载;images:位图图片;svg:矢量图标或插画;styles:全局 CSS(重置/变量/mixins等)
│   │   │   ├─ 📁 fonts                                            - 字体文件目录(woff/woff2/ttf 等);兼容多浏览器
│   │   │   │   └─ 📄 fa-solid-900.woff2                           - 字体文件
│   │   │   ├─ 📁 images                                           - 位图图片目录(png/jpg/gif 等),组件中可直接 import
│   │   │   ├─ 📁 styles                                           - 全局样式目录(reset.css/variables.css/mixins.css 等);在入口统一引入
│   │   │   │   └─ 📄 css2
│   │   │   └─ 📁 svg                                              - SVG 矢量图目录;支持 SVGR 转为 React 组件或作为 img src
│   │   ├─ 📁 components                                           - 纯 UI 组件目录(.tsx+.module.css 同名放置);关注展示无需业务逻辑
│   │   │   ├─ 📁 account-management
│   │   │   │   ├─ 📄 account-management.tsx                       - React TypeScript 组件文件
│   │   │   │   ├─ 📄 ChangePassword.tsx
│   │   │   │   ├─ 📄 EditProfile.tsx
│   │   │   │   └─ 📄 Logout.tsx
│   │   │   ├─ 📁 book-management
│   │   │   │   ├─ 📄 AddBookForm.tsx                              - React TypeScript 组件文件
│   │   │   │   ├─ 📄 BookManagement.tsx
│   │   │   │   ├─ 📄 CategoryManage.tsx
│   │   │   │   ├─ 📄 DeleteBookForm.tsx
│   │   │   │   ├─ 📄 EditBookForm.tsx
│   │   │   │   ├─ 📄 SearchBook.tsx
│   │   │   │   └─ 📄 ShelfManage.tsx
│   │   │   ├─ 📁 borrow-management
│   │   │   │   ├─ 📄 borrow-management.tsx                        - React TypeScript 组件文件
│   │   │   │   ├─ 📄 BorrowBook.tsx
│   │   │   │   ├─ 📄 BorrowQuery.tsx
│   │   │   │   ├─ 📄 RenewBook.tsx
│   │   │   │   └─ 📄 ReturnBook.tsx
│   │   │   ├─ 📁 fine-management
│   │   │   │   ├─ 📄 fine-management.tsx                          - React TypeScript 组件文件
│   │   │   │   ├─ 📄 FineRecords.tsx
│   │   │   │   ├─ 📄 FineRules.tsx
│   │   │   │   └─ 📄 PayFine.tsx
│   │   │   ├─ 📁 help-center
│   │   │   │   └─ 📄 HelpCenter.tsx                               - React TypeScript 组件文件
│   │   │   ├─ 📁 lib
│   │   │   │   └─ 📄 utils.ts                                     - TypeScript 文件
│   │   │   ├─ 📁 login
│   │   │   │   ├─ 📄 AdminSetup.tsx                               - React TypeScript 组件文件
│   │   │   │   └─ 📄 Login.tsx
│   │   │   ├─ 📁 reader-management
│   │   │   │   ├─ 📄 AddReader.tsx                                - React TypeScript 组件文件
│   │   │   │   ├─ 📄 AddReaderForm.tsx
│   │   │   │   ├─ 📄 DeleteReader.tsx
│   │   │   │   ├─ 📄 DeleteReaderForm.tsx
│   │   │   │   ├─ 📄 EditReader.tsx
│   │   │   │   ├─ 📄 EditReaderForm.tsx
│   │   │   │   ├─ 📄 reader-management.tsx
│   │   │   │   ├─ 📄 SearchReader.tsx
│   │   │   │   └─ 📄 SearchReaderForm.tsx
│   │   │   ├─ 📁 stats-management
│   │   │   │   ├─ 📄 BookStats.tsx                                - React TypeScript 组件文件
│   │   │   │   ├─ 📄 FineStats.tsx
│   │   │   │   ├─ 📄 InventoryStats.tsx
│   │   │   │   ├─ 📄 ReaderStats.tsx
│   │   │   │   └─ 📄 stats-management.tsx
│   │   │   ├─ 📁 system-management
│   │   │   │   ├─ 📄 AddAdmin.tsx                                 - React TypeScript 组件文件
│   │   │   │   ├─ 📄 AdminPermissions.tsx
│   │   │   │   ├─ 📄 BackupRestore.tsx
│   │   │   │   ├─ 📄 NewAddAdmin.tsx
│   │   │   │   ├─ 📄 ReaderPermissions.tsx
│   │   │   │   ├─ 📄 ResetPassword.tsx
│   │   │   │   ├─ 📄 system-management.tsx
│   │   │   │   └─ 📄 SystemLogs.tsx
│   │   │   └─ 📁 ui                                               - Shadcn UI 组件目录，包含全局样式和组件
│   │   │       ├─ 📄 alert.tsx                                    - React TypeScript 组件文件
│   │   │       ├─ 📄 button.tsx
│   │   │       ├─ 📄 card.tsx
│   │   │       ├─ 📄 input.tsx
│   │   │       ├─ 📄 label.tsx
│   │   │       └─ 📄 select.tsx
│   │   ├─ 📁 contexts
│   │   │   └─ 📄 AuthContext.tsx                                  - React TypeScript 组件文件
│   │   ├─ 📁 lib
│   │   ├─ 📁 pages                                                - 路由级页面组件目录;每个子目录对应一个页面或路由
│   │   │   └─ 📄 LibraryLayout.tsx                                - React TypeScript 组件文件
│   │   ├─ 📁 services                                             - 后端 API 封装目录;基于 fetch/axios 统一处理请求/响应/错误
│   │   │   ├─ 📄 accountService.ts                                - TypeScript 文件
│   │   │   ├─ 📄 authService.ts
│   │   │   ├─ 📄 bookLoanService.ts
│   │   │   ├─ 📄 bookService.ts
│   │   │   ├─ 📄 categoryService.ts
│   │   │   ├─ 📄 readerPermissionService.ts
│   │   │   ├─ 📄 readerService.ts
│   │   │   └─ 📄 shelfService.ts
│   │   ├─ 📁 utils                                                - 工具函数目录(日期格式化/深拷贝/节流防抖/校验等纯函数)
│   │   │   └─ 📄 csrfUtils.ts                                     - TypeScript 文件
│   │   ├─ 📄 App.tsx                                              - 根组件;配置路由/Provider/顶级布局等全局逻辑
│   │   ├─ 📄 bootstrap.js                                         - Vite 启动脚本，初始化 Vue 应用和全局配置
│   │   ├─ 📄 css2
│   │   ├─ 📄 icons.ts                                             - TypeScript 文件
│   │   ├─ 📄 index.js                                             - Vite 入口文件，挂载 Vue 应用到 #app 元素
│   │   ├─ 📄 main.tsx                                             - 入口挂载文件;调用 ReactDOM 将 <App/> 挂载到 #root 并初始化 HMR 等
│   │   ├─ 📄 [REMOVED]
│   │   ├─ 📄 vite-env.d.ts                                        - Vite 环境类型声明;支持 import.meta.env 及静态资源导入
│   │   └─ 📄 vue.global.js                                        - Vue 全局配置文件，定义 Vue 组件全局注册和配置
│   ├─ 📄 .gitignore                                               - Git 忽略文件配置，指定不需要纳入版本控制的文件类型
│   ├─ 📄 .shadcnrc
│   ├─ 📄 components.json
│   ├─ 📄 eslint.config.js                                         - ESLint 配置文件，配置前端格式化和代码校验规则
│   ├─ 📄 index.html                                               - Vite HTML 模板，应用入口文件，用来挂载 #root 并注入 script/style
│   ├─ 📄 package-lock.json                                        - NPM 依赖锁定文件，确保安装一致性
│   ├─ 📄 package.json                                             - NPM 包管理配置文件，定义依赖、开发/构建脚本等
│   ├─ 📄 postcss.config.js
│   ├─ 📄 README.md                                                - 前端项目说明文档，介绍启动、开发及发布流程
│   ├─ 📄 tailwind.config.js
│   ├─ 📄 tsconfig.app.json                                        - Vite 前端 TypeScript 项目编译配置
│   ├─ 📄 tsconfig.json                                            - TypeScript 通用编译选项配置
│   ├─ 📄 tsconfig.node.json                                       - Node 运行时 TypeScript 配置，用于脚本/工具调用
│   ├─ 📄 vite.config.ts                                           - Vite 配置文件，包含开发服务器、插件和构建选项
│   └─ 📄 vite.config.ts_1
├─ 📁 src
│   ├─ 📁 main                                                     - 主要源代码和资源文件目录
│   │   ├─ 📁 java                                                 - Java 源代码目录
│   │   │   ├─ 📁 com
│   │   │   │   └─ 📁 example
│   │   │   │       └─ 📁 FinalAssignments
│   │   │   │           ├─ 📁 config                               - 配置类目录，存放 Spring 配置、Bean 定义等
│   │   │   │           │   ├─ 📄 FreemarkerGlobalConfig.java
│   │   │   │           │   ├─ 📄 InitialAdminSetupChecker.java
│   │   │   │           │   ├─ 📄 InitialAdminSetupState.java
│   │   │   │           │   ├─ 📄 SecurityConfig.java
│   │   │   │           │   └─ 📄 SpaWebMvcConfig.java
│   │   │   │           ├─ 📁 controller                           - 控制器层，处理 HTTP 请求
│   │   │   │           │   ├─ 📄 AdminAccountController.java
│   │   │   │           │   ├─ 📄 AuthController.java
│   │   │   │           │   ├─ 📄 BookCategoryController.java
│   │   │   │           │   ├─ 📄 BookController.java
│   │   │   │           │   ├─ 📄 BookLoanController.java
│   │   │   │           │   ├─ 📄 BookShelfController.java
│   │   │   │           │   ├─ 📄 GlobalExceptionHandler.java
│   │   │   │           │   ├─ 📄 ReaderController.java
│   │   │   │           │   ├─ 📄 ReaderPermissionController.java
│   │   │   │           │   └─ 📄 SelfIntroController.java
│   │   │   │           ├─ 📁 dto                                  - 数据传输对象目录
│   │   │   │           │   ├─ 📄 AdministratorDTO.java
│   │   │   │           │   ├─ 📄 AdminPermissionDTO.java
│   │   │   │           │   ├─ 📄 AdminResponseDTO.java
│   │   │   │           │   ├─ 📄 AnnouncementDTO.java
│   │   │   │           │   ├─ 📄 BookCategoryDTO.java
│   │   │   │           │   ├─ 📄 BookDTO.java
│   │   │   │           │   ├─ 📄 BookLoanDTO.java
│   │   │   │           │   ├─ 📄 BookShelfDTO.java
│   │   │   │           │   ├─ 📄 BulkBookLoanRequestDTO.java
│   │   │   │           │   ├─ 📄 FineRuleDTO.java
│   │   │   │           │   ├─ 📄 HelpArticleDTO.java
│   │   │   │           │   ├─ 📄 InitialAdminRegisterRequest.java
│   │   │   │           │   ├─ 📄 OverduePaymentDTO.java
│   │   │   │           │   ├─ 📄 PaymentBillDTO.java
│   │   │   │           │   ├─ 📄 ReaderDTO.java
│   │   │   │           │   ├─ 📄 ReaderPermissionDTO.java
│   │   │   │           │   ├─ 📄 SystemAdminLogDTO.java
│   │   │   │           │   └─ 📄 SystemReaderLogDTO.java
│   │   │   │           ├─ 📁 entity                               - 实体类层，存放与数据库表对应的实体
│   │   │   │           │   ├─ 📄 Administrator.java
│   │   │   │           │   ├─ 📄 AdminPermission.java
│   │   │   │           │   ├─ 📄 Announcement.java
│   │   │   │           │   ├─ 📄 Book.java
│   │   │   │           │   ├─ 📄 BookCategory.java
│   │   │   │           │   ├─ 📄 BookLoan.java
│   │   │   │           │   ├─ 📄 BookShelf.java
│   │   │   │           │   ├─ 📄 FineRule.java
│   │   │   │           │   ├─ 📄 HelpArticle.java
│   │   │   │           │   ├─ 📄 OverduePayment.java
│   │   │   │           │   ├─ 📄 PaymentBill.java
│   │   │   │           │   ├─ 📄 Reader.java
│   │   │   │           │   ├─ 📄 ReaderPermission.java
│   │   │   │           │   ├─ 📄 SystemAdminLog.java
│   │   │   │           │   └─ 📄 SystemReaderLog.java
│   │   │   │           ├─ 📁 repository                           - 数据访问层（DAO）
│   │   │   │           │   ├─ 📄 AdministratorRepository.java
│   │   │   │           │   ├─ 📄 AdminPermissionRepository.java
│   │   │   │           │   ├─ 📄 AnnouncementRepository.java
│   │   │   │           │   ├─ 📄 BookCategoryRepository.java
│   │   │   │           │   ├─ 📄 BookLoanRepository.java
│   │   │   │           │   ├─ 📄 BookRepository.java
│   │   │   │           │   ├─ 📄 BookShelfRepository.java
│   │   │   │           │   ├─ 📄 FineRuleRepository.java
│   │   │   │           │   ├─ 📄 HelpArticleRepository.java
│   │   │   │           │   ├─ 📄 OverduePaymentRepository.java
│   │   │   │           │   ├─ 📄 PaymentBillRepository.java
│   │   │   │           │   ├─ 📄 ReaderPermissionRepository.java
│   │   │   │           │   ├─ 📄 ReaderRepository.java
│   │   │   │           │   ├─ 📄 SystemAdminLogRepository.java
│   │   │   │           │   └─ 📄 SystemReaderLogRepository.java
│   │   │   │           ├─ 📁 service                              - 业务逻辑层
│   │   │   │           │   ├─ 📄 AdministratorService.java
│   │   │   │           │   ├─ 📄 AdminPermissionService.java
│   │   │   │           │   ├─ 📄 BookCategoryService.java
│   │   │   │           │   ├─ 📄 BookLoanService.java
│   │   │   │           │   ├─ 📄 BookService.java
│   │   │   │           │   ├─ 📄 BookShelfService.java
│   │   │   │           │   ├─ 📄 ReaderPermissionService.java
│   │   │   │           │   ├─ 📄 ReaderService.java
│   │   │   │           │   └─ 📄 ViteManifestService.java
│   │   │   │           ├─ 📁 utils                                - 工具类目录
│   │   │   │           │   ├─ 📄 MyFunctions.java
│   │   │   │           │   └─ 📄 PasswordEncoderUtil.java
│   │   │   │           └─ 📄 FinalAssignmentsApplication.java
│   │   │   ├─ 📄 DirectoryTreeMarkdownGenerator.java
│   │   │   └─ 📄 SimpleDirectoryTreeMarkdown.java
│   │   ├─ 📁 resources                                            - 资源文件目录，存放配置文件、模板和静态资源
│   │   │   ├─ 📁 db
│   │   │   │   └─ 📁 migration                                    - Flyway 数据库迁移脚本
│   │   │   │       ├─ 📄 V1__init_library_schema.sql
│   │   │   │       ├─ 📄 V2__insert_super_admin.sql
│   │   │   │       └─ 📄 V3__insert_test_data.sql
│   │   │   ├─ 📁 static                                           - 静态资源目录,此文件夹目前由my-vite-app/src文件夹替代，非必要情况下请勿往此目录中添加静态资源文件
│   │   │   │   └─ 📁 assets                                       - 存放静态资源
│   │   │   ├─ 📁 templates                                        - 模板文件目录，此目录已弃用，前端文件已移至 my-vite-app/src 目录，非必要情况下请勿往此目录中添加模板文件
│   │   │   │   ├─ 📄 login.ftl                                    - FreeMarker 模板
│   │   │   │   └─ 📄 welcome.ftl
│   │   │   └─ 📄 application.properties                           - Spring Boot 配置文件
│   │   └─ 📁 webapp                                               - 传统 Java Web 应用目录，此目录存放 JSP/HTML 文件等，此目录已弃用，前端文件已移至 my-vite-app/src 目录
│   │       └─ 📁 WEB-INF                                          - WEB-INF 目录
│   │           ├─ 📁 jsp
│   │           │   ├─ 📄 converted.jsp                            - JSP 视图
│   │           │   └─ 📄 home.jsp
│   │           └─ 📁 tlds
│   │               └─ 📄 MyFunctions.tld                          - 标签库描述文件
│   └─ 📁 test                                                     - 测试代码目录
│       └─ 📁 java                                                 - Java 测试代码目录
│           └─ 📁 com
│               └─ 📁 example
│                   └─ 📁 FinalAssignments
│                       └─ 📄 HelloSringBootApplicationTests.java
├─ 📄 .gitattributes
├─ 📄 .gitignore
├─ 📄 build.gradle                                                 - Gradle 构建脚本
├─ 📄 gradlew
├─ 📄 gradlew.bat                                                  - 批处理脚本
├─ 📄 [REMOVED]
├─ 📄 [REMOVED]
├─ 📄 package-lock.json
├─ 📄 [REMOVED]
├─ 📄 README.md
├─ 📄 [REMOVED]
├─ 📄 settings.gradle
├─ 📄 [REMOVED]
├─ 📄 [REMOVED]
├─ 📄 [REMOVED]
└─ 📄 [REMOVED]
```  

# 说明及注释

## 1. 项目结构概览

```
FinalAssignments  
├─ gradle/…             # Gradle Wrapper  
├─ my-vite-app/…        # 前端：Vite + React + TypeScript  
├─ src/  
│  ├─ main/  
│  │  ├─ java/…         # 后端 Java 源码（Spring Boot）  
│  │  └─ resources/…    # 配置、Flyway 脚本、静态资源  
│  └─ test/…            # 后端单元测试  
└─ 根目录脚本 & 配置文件  
```

## 2. 后端模块（Spring Boot + Gradle）

### 2.1 构建逻辑

- 使用 `gradlew` 保证环境一致
- 插件：java、war（JSP 支持）、spring-boot、dependency-management、flyway
- 打包：`bootWar()` 输出 WAR；禁用 `bootJar()`
- 前端集成：
    - `buildVite` 任务在 `my-vite-app` 目录执行 `npm run build`
    - `processResources` 拷贝打包产物到 `src/main/resources/static`

### 2.2 核心依赖

- Web / Freemarker / JPA / Security / MyBatis / Flyway / MySQL
- JSP 支持：Tomcat-embed-jasper、EL、JSTL

### 2.3 包与目录

```
com.example.FinalAssignments  
├─ config/      # Spring 配置（Security、视图解析、Freemarker 等）  
├─ controller/  # HTTP 接口 & MVC 控制器  
├─ service/     # 业务逻辑层  
├─ repository/  # 数据访问层（DAO）  
├─ entity/      # JPA 实体映射  
├─ utils/       # 工具类（加密、通用方法）  
└─ FinalAssignmentsApplication.java  
```

### 2.4 视图与模板

- FreeMarker（*.ftl）：`resources/templates/`
- JSP（*.jsp）：`src/main/webapp/WEB-INF/jsp/`
- ViewResolver 顺序：Freemarker > JSP（JSP 限定 viewNames 且 order=LOWEST）
- Vite 资源注入：通过 `ViteManifestService` 在模板中按需加载

### 2.5 配置（application.properties）

```properties
spring.application.name=HelloSringBoot
spring.datasource.url=jdbc:mysql://localhost:3306/test?createDatabaseIfNotExist=true
spring.datasource.username=root
spring.datasource.password=password
spring.flyway.enabled=true
spring.flyway.locations=classpath:db/migration
spring.freemarker.template-loader-path=classpath:/templates/
spring.freemarker.suffix=.ftl
# spring.flyway.enabled=false
spring.flyway.baseline-on-migrate=true
spring.flyway.baseline-version=1
logging.level.com.example.FinalAssignments.service.ViteManifestService=DEBUG
logging.level.org.springframework.web=DEBUG
logging.level.org.springframework.web.servlet.resource.ResourceHttpRequestHandler=TRACE
#spring.mvc.view.prefix=/WEB-   INF/jsp/
#spring.mvc.view.suffix=.jsp
server.servlet.context-path=/
spring.freemarker.expose-request-attributes=true
spring.freemarker.expose-session-attributes=true
spring.freemarker.expose-spring-macro-helpers=true
debug=true
logging.level.root=DEBUG
spring.freemarker.settings.incompatible_improvements=2.3.34
```

## 3. 前端模块（my-vite-app）

### 3.1 技术栈

Vite 6.2.6 + React 18.3.1 + TypeScript 5.7.2 + SWC + TailwindCSS + Font-Awesome

### 3.2 目录结构

```
my-vite-app/  
├─ public/        # 原样拷贝到 dist  
├─ src/  
│  ├─ assets/     # 图片、字体、全局样式  
│  ├─ components/ # 纯 UI 组件（.tsx + .module.css）  
│  ├─ hooks/      # 自定义 Hooks  
│  ├─ pages/      # 路由页面  
│  ├─ services/   # 后端 API 封装（fetch/axios）  
│  ├─ types/      # 全局 TS 类型声明  
│  ├─ utils/      # 工具函数  
│  ├─ main.tsx    # React 挂载入口  
│  └─ App.tsx     # 根组件，路由 & Provider 配置  
├─ vite.config.ts # 别名、静态资源复制、manifest 等  
└─ package.json   # 依赖 & 脚本  
```

### 3.3 常用脚本

```bash
cd my-vite-app  
npm install       # 安装依赖  
npm run dev       # 本地开发 HMR  
npm run build     # 生产打包，生成 manifest.json  
npm run preview   # 预览打包产物  
```

## 4. 数据库 & Flyway 迁移

- 脚本目录：`src/main/resources/db/migration/V{version}__{desc}.sql`
- 应用启动自动执行增量迁移；`baseline-on-migrate` 支持已有库首次校准
- 如需重置：手动清空数据库 → 重启应用

## 5. 开发 & 运行流程

1. 克隆仓库
2. 后端构建 & 启动

```bash
./gradlew build   # 包含前端打包
./gradlew bootRun
```  

3. （可选）前端单独启动

```bash
cd my-vite-app
npm install
npm run build
npm run dev
```  

---

# 注意事项

- JSP 与 FreeMarker 共存时务必控制 ViewResolver 顺序及 viewNames，避免路由冲突
- 前端资源通过 Vite Manifest 注入，生产环境按需引用静态文件
- Flyway 脚本版本号递增，已执行版本请勿修改，新增脚本请顺序命名
