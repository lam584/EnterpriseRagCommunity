# 企业 RAG 社区系统

一个围绕社区内容生产、审核治理、检索增强和大模型接入构建的全栈 Web 系统。项目当前已经不是单纯的“社区 + 聊天”原型，而是包含前台社区、版主工作台、后台管理台、审核流水线、RAG 检索链路、LLM 接入配置和监控面板的完整工程化实现。

默认访问地址：

- 前后台统一服务地址：http://127.0.0.1:8099
- 前台首页：http://127.0.0.1:8099/portal/discover/home

## 项目定位

本系统面向“企业知识沉淀 + 社区协作 + RAG 问答”场景，核心目标包括：

- 让用户通过帖子、评论、附件文件持续沉淀知识内容
- 让智能助手基于社区内容和附件解析结果完成检索增强问答
- 让审核链路同时支持规则、向量相似检测和 LLM 多层治理
- 让管理员能够在统一后台中配置模型、检索、审核、权限和监控策略

## 当前已实现的主要能力

### 前台社区

- 浏览与发现：首页、热榜、版块浏览
- 统一搜索：支持检索帖子、评论、附件文件
- 通知中心：回复、点赞、提及、举报、安全、审核通知
- 智能助手：多轮对话、历史、收藏夹、默认参数设置、RAG 开关、文件与图片上传
- 发帖系统：Markdown 编辑、AI 发帖辅助、附件上传、草稿箱
- 账户中心：资料、安全、偏好、我的帖子、收藏
- 版主中心：审核队列、治理日志、导出 CSV

### 后台管理

- 内容管理：版块、帖子、评论、标签、上传格式、帖子文件解析
- 审核中心：审核队列、规则过滤、相似检测、LLM 审核、审核策略、分片审核、追溯日志、风险标签
- 语义增强：标题生成、多任务标签、摘要、翻译
- 检索与 RAG：向量索引、Hybrid 检索、动态上下文裁剪、引用展示配置
- 评估与监控：Token 成本、LLM 队列、路由状态、全局日志、审核成本、内容安全熔断
- 用户与权限：用户、角色、权限、高权限 2FA 策略
- LLM 接入配置：模型提供商、前台对话默认参数、上下文治理、图片存储、负载均衡

更细的页面级能力清单见 docs/功能清单.md。

## 技术架构

### 后端

- Java 21 Toolchain
- Spring Boot 3.5.11
- Spring MVC / Validation / Security / JPA / Actuator
- MyBatis
- MySQL + Flyway
- Elasticsearch / OpenSearch 相关检索能力
- Apache POI / PDFBox / Tika 等文件处理组件

### 前端

- React 18
- TypeScript 5
- Vite 6
- Tailwind CSS
- Radix UI / Shadcn 风格组件
- Axios / React Router
- Vitest

### 构建方式

- 根项目使用 Gradle
- 前端位于 my-vite-app
- Gradle 的 processResources 会将 my-vite-app/dist 复制到后端静态资源目录
- 执行 bootRun 或常规构建时会自动触发前端构建

## 快速开始

### 1. 环境要求

- JDK 21
- MySQL 8+
- Node.js 18+，建议 Node.js 20+
- npm

可选但对完整功能有帮助的依赖：

- Elasticsearch / OpenSearch
- 可用的大模型提供商配置，如 DashScope 或 OpenAI 兼容接口
- SMTP 邮件配置

### 2. 配置数据库

项目默认数据库连接配置位于 src/main/resources/application.properties，默认端口为 8099，数据库名为 EnterpriseRagCommunity。

默认数据源 URL：

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/EnterpriseRagCommunity?createDatabaseIfNotExist=true&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
spring.datasource.username=${DB_USERNAME:}
spring.datasource.password=${DB_PASSWORD:}
```

推荐通过环境变量提供数据库账号密码：

```powershell
$env:DB_USERNAME="root"
$env:DB_PASSWORD="your-password"
```

首次启动时 Flyway 会自动执行 src/main/resources/db/migration 下的迁移脚本。

### 3. 安装前端依赖

```powershell
cd my-vite-app
npm install
cd ..
```

### 4. 启动后端应用

```powershell
.\gradlew.bat bootRun
```

说明：

- bootRun 依赖 build
- build 会触发前端构建并将静态资源复制到后端
- 启动成功后访问 http://127.0.0.1:8099

### 5. 前端独立调试（可选）

如果只想调试前端界面，可以单独启动 Vite 开发服务器：

```powershell
cd my-vite-app
npm run dev
```

Vite 已配置对以下路径的反向代理到后端 http://localhost:8099：

- /api
- /uploads
- /admin

通常前端开发地址为 Vite 默认端口，例如 http://127.0.0.1:5173。

## 常用命令

### 后端与整站

```powershell
.\gradlew.bat build
.\gradlew.bat test
.\gradlew.bat integrationTest
.\gradlew.bat jacocoTestReport
.\gradlew.bat buildVite
```

### 前端

```powershell
cd my-vite-app
npm run dev
npm run build
npm run test
npm run lint
```

## 目录结构

```text
.
├─ build.gradle                      # 根构建脚本，整合后端与前端构建
├─ gradlew.bat                       # Gradle Wrapper
├─ src/
│  ├─ main/
│  │  ├─ java/com/example/EnterpriseRagCommunity/
│  │  │  ├─ config/                  # 配置类
│  │  │  ├─ controller/              # 前台、后台、审核、监控等控制器
│  │  │  ├─ dto/                     # DTO
│  │  │  ├─ entity/                  # JPA/MyBatis 相关实体
│  │  │  ├─ repository/              # 数据访问层
│  │  │  ├─ security/                # 安全相关逻辑
│  │  │  ├─ service/                 # 业务服务层
│  │  │  └─ utils/                   # 工具类
│  │  └─ resources/
│  │     ├─ application.properties   # 主配置
│  │     ├─ db/migration/            # Flyway 迁移脚本
│  │     └─ logback-spring.xml       # 日志配置
│  ├─ test/                          # 单元测试
│  └─ integrationTest/               # 集成测试
├─ my-vite-app/                      # React + Vite 前端工程
├─ docs/                             # 功能清单、测试记录、部署说明等文档
├─ uploads/                          # 本地上传文件目录
└─ logs/                             # 运行日志目录
```

## 配置说明

### 应用基础配置

- 服务端口：server.port=8099
- 上下文路径：server.servlet.context-path=/
- 上传根目录：app.upload.root=uploads
- 日志文件：logs/EnterpriseRagCommunity.log

### 与高级功能相关的配置

以下功能依赖额外配置或运行环境，未准备时不会影响基础页面启动，但相关能力可能不可用：

- 大模型调用与负载均衡
- 向量检索与重排
- 邮件验证码与 TOTP 相关流程
- 图片上传到外部模型平台前的压缩与存储流程

建议先确保“数据库 + 后端 + 前端静态资源”能正常启动，再逐步补齐 LLM、检索和邮件相关配置。

## 文档索引

- docs/功能清单.md：基于浏览器实测整理的实际功能清单
- docs/自动化测试与指标说明.md：测试与指标说明
- docs/腾讯云 Ubuntu 24.04 项目部署方案.md：部署方案参考
- docs/评估报告.md：项目评估相关文档

## 说明

- README 以当前仓库的实际代码、构建脚本和已落地页面为准
- 页面能力的细节更新优先维护在 docs/功能清单.md
- 如果 README 与具体代码行为不一致，应以 build.gradle、application.properties 和实际页面实现为准