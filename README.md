# 企业知识检索增强社区系统

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
| JDK           | Eclipse Temurin OpenJDK | 25 (LTS) | Java 运行时环境（项目默认 toolchain=25；编译 release 默认为 21） |
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
    spring.datasource.url=jdbc:mysql://localhost:3306/test?createDatabaseIfNotExist=true
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
###  使用用 JDK 的单文件运行模式生成目录结构：
   ```bash
    cmd /c E:\EnterpriseRagCommunity-main\gen-tree.cmd
   ```

``` 

```  
---
###  在终端中执行以下命令进行清理和重建：
   ```bash
   .\gradlew.bat clean build -x test
   ```