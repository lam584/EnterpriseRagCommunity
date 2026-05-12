package tools;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class DirectoryTreeMarkdownGenerator {

    // 全局排除目录名（不区分大小写）
    private static final Set<String> EXCLUDED_DIR_NAMES = Set.of(
            // 构建/IDE 产物
            "build", ".gradle", "dist", "node_modules", "out", ".idea", ".git", ".vscode",
            "target", "obj", "tmp", "temp", "bin", ".venv", "coverage",
            // AI 工具/镜像缓存
            ".qoder", ".workbuddy", ".codebuddy", ".trae", ".trae-temp", ".github",
            // 运行时产物
            "logs", "uploads", "test-reports", "build-reports"
    );

    // 排除的文件后缀
    private static final Set<String> EXCLUDED_FILE_EXTENSIONS = Set.of(
            // 图片/字体
            "css", "png", "jpg", "jpeg", "gif", "svg", "ico", "bmp", "webp",
            "ttf", "woff", "woff2",
            // 日志/patch/锁/压缩包/二进制
            "log", "patch", "diff", "lock", "zip", "jar", "class",
            "exe", "dll", "so",
            // 证书/媒体
            "pem", "key", "crt", "mp3", "mp4"
    );

    // 常见扩展名描述
    private static final Map<String, String> EXTENSION_DESCRIPTIONS = Map.ofEntries(
            Map.entry("properties", "配置文件"),
            Map.entry("ftl", "FreeMarker 模板"),
            Map.entry("jsp", "JSP 视图"),
            Map.entry("ts", "TypeScript 文件"),
            Map.entry("tsx", "React TypeScript 组件文件"),
            Map.entry("jsx", "React JavaScript 组件文件"),
            Map.entry("gradle", "Gradle 构建脚本"),
            Map.entry("bat", "批处理脚本"),
            Map.entry("sh", "Shell 脚本"),
            Map.entry("scss", "SCSS 样式文件"),
            Map.entry("sass", "Sass 样式文件"),
            Map.entry("less", "Less 样式文件"),
            Map.entry("vue", "Vue 组件文件"),
            Map.entry("tld", "标签库描述文件"),
            Map.entry("woff2", "字体文件"),
            Map.entry("woff", "字体文件"),
            Map.entry("ttf", "TrueType 字体文件")
    );

    private static class PatternDesc {
        final Pattern pattern;
        final String desc;

        PatternDesc(Pattern pattern, String desc) {
            this.pattern = pattern;
            this.desc = desc;
        }
    }

    private static final List<PatternDesc> PATH_PATTERN_LIST;

    static {
        Map<String, String> raw = Map.<String, String>ofEntries(
                Map.entry("settings.gradle", "Gradle 设置文件，用于包含各个子模块"),
                Map.entry("gradlew.bat", "Gradle Wrapper Windows 启动脚本"),
                Map.entry("gradlew", "Gradle Wrapper Unix 启动脚本"),
                Map.entry("package-lock.json", "NPM 依赖锁定文件，确保安装一致性"),
                Map.entry("package.json", "NPM 包管理配置文件，定义依赖和脚本命令"),
                Map.entry("build.gradle", "Gradle 构建脚本，配置项目依赖及插件"),
                Map.entry("README.md", "项目说明文档，介绍项目背景、使用方法及其他信息"),
                Map.entry("gradle.properties", "Gradle 属性配置文件（如版本号、编码等）"),
                Map.entry("tsconfig.json", "TypeScript 配置文件，配置编译选项与路径别名等"),
                Map.entry(".gitignore", "Git 忽略文件配置，指定不需要纳入版本控制的文件类型"),
                Map.entry(".gitattributes", "Git 属性配置文件，定义文本文件的换行符等"),

                Map.entry("src/main/resources/application.properties", "Spring Boot 配置文件"),
                Map.entry("src/main/resources/application.yml", "Spring Boot YAML 配置文件"),
                Map.entry("src/main/resources/bootstrap.properties", "Spring Cloud 配置文件，比 application 先加载"),
                Map.entry("src/main/resources/bootstrap.yml", "Spring Cloud YAML 配置文件"),

                Map.entry("src/main", "主要源代码和资源文件目录"),
                Map.entry("src/main/java", "Java 源代码目录"),
                Map.entry("src/main/resources", "资源文件目录，存放配置文件、模板和静态资源"),
                Map.entry("src/test", "测试代码目录"),
                Map.entry("src/test/java", "Java 测试代码目录"),
                Map.entry("src/test/resources", "测试资源文件目录"),
                Map.entry("gradle", "Gradle 相关文件夹，存放 Gradle Wrapper 脚本"),

                Map.entry("src/main/java/com/example/*/config", "配置类目录，存放 Spring 配置、Bean 定义等"),
                Map.entry("src/main/java/com/example/*/controller", "控制器层，处理 HTTP 请求"),
                Map.entry("src/main/java/com/example/*/entity", "实体类层，存放与数据库表对应的实体"),
                Map.entry("src/main/java/com/example/*/repository", "数据访问层（DAO）"),
                Map.entry("src/main/java/com/example/*/service", "业务逻辑层"),
                Map.entry("src/main/java/com/example/*/utils", "工具类目录"),
                Map.entry("src/main/java/com/example/*/exception", "异常处理类目录"),
                Map.entry("src/main/java/com/example/*/dto", "数据传输对象目录"),
                Map.entry("src/main/java/com/example/*/vo", "视图对象目录"),
                Map.entry("src/main/java/com/example/*/aop", "面向切面编程目录"),
                Map.entry("src/main/java/com/example/*/interceptor", "拦截器目录"),
                Map.entry("src/main/java/com/example/*/filter", "过滤器目录"),

                Map.entry("src/main/resources/db/migration", "Flyway 数据库迁移脚本"),
                Map.entry("src/main/resources/static", "静态资源目录,此文件夹目前由my-vite-app/src文件夹替代，非必要情况下请勿往此目录中添加静态资源文件"),
                Map.entry("src/main/resources/static/assets", "存放静态资源"),
                Map.entry("src/main/resources/static/assets/images", "图片文件"),
                Map.entry("src/main/resources/static/assets/css", "CSS 样式文件"),
                Map.entry("src/main/resources/static/assets/js", "JavaScript 脚本"),
                Map.entry("src/main/resources/static/assets/fonts", "字体文件"),
                Map.entry("src/main/resources/templates", "模板文件目录，此目录已弃用，前端文件已移至 my-vite-app/src 目录，非必要情况下请勿往此目录中添加模板文件"),

                Map.entry("src/main/webapp", "传统 Java Web 应用目录，此目录存放 JSP/HTML 文件等，此目录已弃用，前端文件已移至 my-vite-app/src 目录"),
                Map.entry("src/main/webapp/WEB-INF", "WEB-INF 目录"),

                Map.entry("my-vite-app", "Vite/React前端项目根目录;包含 package.json/tsconfig*/vite.config.ts/README.md 等基础配置"),
                Map.entry("my-vite-app/public", "公共静态资源目录;不经打包,直接拷贝至 dist 根目录;适合 favicon、HTML 模板等"),
                Map.entry("my-vite-app/src", "前端源代码根目录;存放应用逻辑/组件/样式/类型声明/工具函数"),
                Map.entry("my-vite-app/src/assets", "通用静态资源目录;fonts:自定义字体(woff/ttf等)通过 @font-face 加载;images:位图图片;svg:矢量图标或插画;styles:全局 CSS(重置/变量/mixins等)"),
                Map.entry("my-vite-app/src/assets/fonts", "字体文件目录(woff/woff2/ttf 等);兼容多浏览器"),
                Map.entry("my-vite-app/src/assets/images", "位图图片目录(png/jpg/gif 等),组件中可直接 import"),
                Map.entry("my-vite-app/src/assets/svg", "SVG 矢量图目录;支持 SVGR 转为 React 组件或作为 img src"),
                Map.entry("my-vite-app/src/assets/styles", "全局样式目录(reset.css/variables.css/mixins.css 等);在入口统一引入"),
                Map.entry("my-vite-app/src/components", "纯 UI 组件目录(.tsx+.module.css 同名放置);关注展示无需业务逻辑"),
                Map.entry("my-vite-app/src/pages", "路由级页面组件目录;每个子目录对应一个页面或路由"),
                Map.entry("my-vite-app/src/hooks", "自定义 React Hooks 目录;封装可复用逻辑(如 useFetch/useAuth/useDebounce)"),
                Map.entry("my-vite-app/src/services", "后端 API 封装目录;基于 fetch/axios 统一处理请求/响应/错误"),
                Map.entry("my-vite-app/src/types", "全局 TypeScript 类型声明目录(interface/type/enum 等)"),
                Map.entry("my-vite-app/src/utils", "工具函数目录(日期格式化/深拷贝/节流防抖/校验等纯函数)"),
                Map.entry("my-vite-app/src/App.tsx", "根组件;配置路由/Provider/顶级布局等全局逻辑"),
                Map.entry("my-vite-app/src/main.tsx", "入口挂载文件;调用 ReactDOM 将 <App/> 挂载到 #root 并初始化 HMR 等"),
                Map.entry("my-vite-app/src/vite-env.d.ts", "Vite 环境类型声明;支持 import.meta.env 及静态资源导入"),
                Map.entry("my-vite-app/.gitignore", "前端项目 Git 忽略文件，指定不需要纳入版本控制的文件类型"),
                Map.entry("my-vite-app/eslint.config.js", "ESLint 配置文件，配置前端格式化和代码校验规则"),
                Map.entry("my-vite-app/index.html", "Vite HTML 模板，应用入口文件，用来挂载 #root 并注入 script/style"),
                Map.entry("my-vite-app/package.json", "NPM 包管理配置文件，定义依赖、开发/构建脚本等"),
                Map.entry("my-vite-app/package-lock.json", "NPM 依赖锁定文件，确保前后端一致的版本"),
                Map.entry("my-vite-app/README.md", "前端项目说明文档，介绍启动、开发及发布流程"),
                Map.entry("my-vite-app/tsconfig.app.json", "Vite 前端 TypeScript 项目编译配置"),
                Map.entry("my-vite-app/tsconfig.json", "TypeScript 通用编译选项配置"),
                Map.entry("my-vite-app/tsconfig.node.json", "Node 运行时 TypeScript 配置，用于脚本/工具调用"),
                Map.entry("my-vite-app/vite.config.ts", "Vite 配置文件，包含开发服务器、插件和构建选项"),
                Map.entry("my-vite-app/vite-env.d.ts", "Vite 环境变量类型声明，支持 import.meta.env 等"),
                Map.entry("my-vite-app/src/vue.global.js", "Vue 全局配置文件，定义 Vue 组件全局注册和配置"),
                Map.entry("my-vite-app/src/bootstrap.js", "Vite 启动脚本，初始化 Vue 应用和全局配置"),
                Map.entry("my-vite-app/src/index.js", "Vite 入口文件，挂载 Vue 应用到 #app 元素"),
                Map.entry("my-vite-app/src/components/ui", "Shadcn UI 组件目录，包含全局样式和组件"),

                // ========== 项目辅助目录（docs/perf/scripts/logs 等） ==========
                Map.entry("docs", "项目文档根目录，包含论文、部署、审核流程、测试说明等"),
                Map.entry("docs/论文", "毕业论文源文件与答辩材料（含 md/docx/历史答辩提问/压力测试记录等）"),
                Map.entry("docs/审核", "内容审核业务流程文档（帖子/评论/个人简介审核与举报流程）"),
                Map.entry("docs/测试", "测试相关文档（功能测试用例、测试报告等）"),
                Map.entry("docs/评估报告", "系统评估与分析报告"),
                Map.entry("docs/compliance", "合规相关文档（第三方许可例外等）"),
                Map.entry("perf", "性能测试根目录"),
                Map.entry("perf/jmeter", "JMeter 压测脚本与结果（EnterpriseRagCommunity_basic_load.jmx 为主脚本）"),
                Map.entry("perf/jmeter/results", "JMeter 压测结果输出目录"),
                Map.entry("scripts", "运维辅助脚本（Kafka 启停、一键测试、端口调优等）"),
                Map.entry("testdata", "测试数据集目录（如 reddit-vaccine-myths.ipynb 用于 RAG 测试）"),
                Map.entry("logs", "运行时日志目录（含 kafka 子目录）"),
                Map.entry("uploads", "用户上传文件存储目录（file_assets 表对应物理文件）"),
                Map.entry("licenses", "第三方开源许可证目录"),
                Map.entry("temp", "临时文件目录（JMeter setenv、Linux 工具等）"),
                Map.entry("test-reports", "测试报告输出目录（npm audit 等）"),

                // ========== Gradle/构建相关 ==========
                Map.entry("gradle/wrapper", "Gradle Wrapper 目录（gradle-wrapper.jar 与 properties）"),
                Map.entry("dependency-check-suppressions.xml", "OWASP 依赖漏洞检查抑制规则"),
                Map.entry("qodana.yaml", "JetBrains Qodana 代码质量扫描配置"),
                Map.entry("NOTICE", "开源项目版权声明"),
                Map.entry("LICENSE", "开源许可证文件"),
                Map.entry(".env", "环境变量文件（数据库/ES/AI 供应商等敏感配置）"),
                Map.entry(".env.example", "环境变量示例模板"),

                // ========== 后端主包与核心文件 ==========
                Map.entry("src/main/java/com/example/EnterpriseRagCommunity", "后端主包根目录"),
                Map.entry("src/main/java/com/example/EnterpriseRagCommunity/EnterpriseRagCommunityApplication.java", "Spring Boot 启动类（@SpringBootApplication 入口）"),
                Map.entry("src/integrationTest", "集成测试源码根目录（Testcontainers + @SpringBootTest）"),
                Map.entry("src/integrationTest/java", "集成测试 Java 源码目录"),
                Map.entry("src/test/java/tools", "测试辅助工具目录（如本目录树生成器 DirectoryTreeMarkdownGenerator）"),

                // ========== 后端 service 子包（核心业务分层） ==========
                Map.entry("src/main/java/com/example/EnterpriseRagCommunity/service", "业务逻辑层根目录，按领域划分子包"),
                Map.entry("src/main/java/com/example/EnterpriseRagCommunity/service/access", "访问控制域：RBAC 角色权限、TOTP 二次验证、访问日志、会话管理"),
                Map.entry("src/main/java/com/example/EnterpriseRagCommunity/service/ai", "AI 模型服务域：统一 LLM 网关、路由、队列、嵌入、重排、聊天、流式 SSE"),
                Map.entry("src/main/java/com/example/EnterpriseRagCommunity/service/content", "社区内容域：帖子、评论、版块、标签、附件、互动（点赞/收藏/举报）"),
                Map.entry("src/main/java/com/example/EnterpriseRagCommunity/service/moderation", "内容审核域：规则/向量/LLM/人工多阶段流水线、违规样本库、风险标签、审核队列调度"),
                Map.entry("src/main/java/com/example/EnterpriseRagCommunity/service/retrieval", "检索增强 RAG 域：混合检索（BM25+向量）、RRF 融合、重排、上下文裁剪、引用溯源"),
                Map.entry("src/main/java/com/example/EnterpriseRagCommunity/service/monitor", "监控域：LLM 队列监控、路由遥测、令牌消耗统计、事件日志"),
                Map.entry("src/main/java/com/example/EnterpriseRagCommunity/service/safety", "内容安全熔断域（ContentSafetyCircuitBreakerService 在审核异常时熔断）"),
                Map.entry("src/main/java/com/example/EnterpriseRagCommunity/service/notify", "站内通知域：系统通知、评论/点赞提醒"),
                Map.entry("src/main/java/com/example/EnterpriseRagCommunity/service/es", "Elasticsearch 客户端工具与索引辅助"),
                Map.entry("src/main/java/com/example/EnterpriseRagCommunity/service/init", "系统初始化服务（首次启动引导、默认配置注入）"),
                Map.entry("src/main/java/com/example/EnterpriseRagCommunity/service/impl", "服务实现类目录（部分接口的实现分离）"),
                Map.entry("src/main/java/com/example/EnterpriseRagCommunity/service/config", "业务域配置服务"),

                // ========== 后端 controller 子包（按业务域划分 REST API） ==========
                Map.entry("src/main/java/com/example/EnterpriseRagCommunity/controller", "REST 控制器根目录，按业务域划分子包"),
                Map.entry("src/main/java/com/example/EnterpriseRagCommunity/controller/access", "访问控制 API：认证/授权/TOTP/会话"),
                Map.entry("src/main/java/com/example/EnterpriseRagCommunity/controller/admin", "后台管理 API：管理员专用接口"),
                Map.entry("src/main/java/com/example/EnterpriseRagCommunity/controller/ai", "AI 对话 API：AiChatController 提供 SSE 流式聊天接口"),
                Map.entry("src/main/java/com/example/EnterpriseRagCommunity/controller/content", "社区内容 API：PostsController 发帖 / 评论 / 版块 / 标签 / 互动"),
                Map.entry("src/main/java/com/example/EnterpriseRagCommunity/controller/moderation", "审核 API：审核队列、处置操作、治理记录"),
                Map.entry("src/main/java/com/example/EnterpriseRagCommunity/controller/retrieval", "检索/RAG API：搜索、RAG 调优、命中事件"),
                Map.entry("src/main/java/com/example/EnterpriseRagCommunity/controller/monitor", "监控 API：队列/令牌/路由/指标采集"),
                Map.entry("src/main/java/com/example/EnterpriseRagCommunity/controller/safety", "内容安全熔断 API"),
                Map.entry("src/main/java/com/example/EnterpriseRagCommunity/controller/semantic", "语义处理 API：分词、向量索引、提示工程"),
                Map.entry("src/main/java/com/example/EnterpriseRagCommunity/controller/portal", "门户/首页 API（公共入口）"),
                Map.entry("src/main/java/com/example/EnterpriseRagCommunity/controller/debug", "调试接口（仅开发/内部使用）"),

                // ========== 后端其他包 ==========
                Map.entry("src/main/java/com/example/EnterpriseRagCommunity/security", "Spring Security 扩展目录：AccessChangedFilter（权限版本热刷新）/ ThreatPathBlockFilter（威胁路径阻断）/ IpPathRateLimitFilter（IP 限流）/ AccessLogsFilter（访问日志）"),
                Map.entry("src/main/java/com/example/EnterpriseRagCommunity/config", "Spring 配置类目录：SecurityConfig（过滤器链）/ 数据源 / 异步 / CORS 等"),
                Map.entry("src/main/java/com/example/EnterpriseRagCommunity/entity", "JPA 实体类目录，对应 MySQL 表（UsersEntity/PostsEntity/CommentsEntity 等）"),
                Map.entry("src/main/java/com/example/EnterpriseRagCommunity/repository", "Spring Data JPA 仓储接口目录（UsersRepository/PostsRepository 等）"),
                Map.entry("src/main/java/com/example/EnterpriseRagCommunity/dto", "数据传输对象目录（按业务域拆分子包）"),
                Map.entry("src/main/java/com/example/EnterpriseRagCommunity/exception", "全局异常处理目录"),
                Map.entry("src/main/java/com/example/EnterpriseRagCommunity/util", "通用工具类（一）"),
                Map.entry("src/main/java/com/example/EnterpriseRagCommunity/utils", "通用工具类（二）"),

                // ========== 核心业务类（答辩高频定位） ==========
                Map.entry("src/main/java/com/example/EnterpriseRagCommunity/config/SecurityConfig.java", "★ Spring Security 配置：API 链 + Web 链、过滤器顺序（答辩高频）"),
                Map.entry("src/main/java/com/example/EnterpriseRagCommunity/service/ai/LlmGateway.java", "★ 统一 LLM 网关：chatOnce/chatStream/embedOnceRouted/rerankOnceRouted（答辩高频）"),
                Map.entry("src/main/java/com/example/EnterpriseRagCommunity/service/ai/LlmRoutingService.java", "★ 模型路由服务：平滑加权轮询、健康冷却、策略切换"),
                Map.entry("src/main/java/com/example/EnterpriseRagCommunity/service/ai/LlmCallQueueService.java", "LLM 调用队列服务：令牌桶限流、并发控制"),
                Map.entry("src/main/java/com/example/EnterpriseRagCommunity/service/ai/AiChatService.java", "★ AI 对话服务：SSE 流式 streamChat() / 非流式 chatOnce()"),
                Map.entry("src/main/java/com/example/EnterpriseRagCommunity/service/ai/AiEmbeddingService.java", "向量嵌入服务（对接 qwen3-embedding-8b）"),
                Map.entry("src/main/java/com/example/EnterpriseRagCommunity/service/ai/AiRerankService.java", "重排服务（对接 qwen3-rerank-8b）"),
                Map.entry("src/main/java/com/example/EnterpriseRagCommunity/service/ai/TokenCountService.java", "Token 估算服务（用于上下文预算控制）"),
                Map.entry("src/main/java/com/example/EnterpriseRagCommunity/service/retrieval/HybridRagRetrievalService.java", "★ 混合检索核心：bm25Search/fuse（RRF/线性）/rerank（答辩高频）"),
                Map.entry("src/main/java/com/example/EnterpriseRagCommunity/service/monitor/LlmQueueMonitorService.java", "LLM 队列监控（队列长度/QPS/延迟采集）"),
                Map.entry("src/main/java/com/example/EnterpriseRagCommunity/service/safety/ContentSafetyCircuitBreakerService.java", "内容安全熔断服务（审核服务异常时自动熔断）"),
                Map.entry("src/main/java/com/example/EnterpriseRagCommunity/service/access/TotpService.java", "★ TOTP 算法服务（基于 RFC 6238，支持 SHA1/256/512 与 6/8 位）"),
                Map.entry("src/main/java/com/example/EnterpriseRagCommunity/service/access/AccountTotpService.java", "TOTP 账户业务服务（绑定/校验/解绑）"),
                Map.entry("src/main/java/com/example/EnterpriseRagCommunity/controller/AccountTotpController.java", "TOTP 绑定/校验接口"),
                Map.entry("src/main/java/com/example/EnterpriseRagCommunity/controller/AuthController.java", "认证主接口：登录/注册/登出/会话"),
                Map.entry("src/main/java/com/example/EnterpriseRagCommunity/controller/ai/AiChatController.java", "★ AI 聊天控制器：/api/ai/chat/stream（SSE）/ /api/ai/chat（非流式）"),
                Map.entry("src/main/java/com/example/EnterpriseRagCommunity/security/AccessChangedFilter.java", "权限版本号热刷新过滤器（users.access_version 同步）"),
                Map.entry("src/main/java/com/example/EnterpriseRagCommunity/security/ThreatPathBlockFilter.java", "威胁路径阻断过滤器（拦截 .env/.git/wp-admin 等扫描）"),
                Map.entry("src/main/java/com/example/EnterpriseRagCommunity/security/IpPathRateLimitFilter.java", "IP + 路径维度限流过滤器"),
                Map.entry("src/main/java/com/example/EnterpriseRagCommunity/security/AccessLogsFilter.java", "访问日志过滤器（写入 ES 访问日志索引）"),

                // ========== Flyway 迁移脚本（关键） ==========
                Map.entry("src/main/resources/db/migration/V1__table_design.sql", "★ 数据库主表结构（users/posts/comments/moderation_queue/qa_messages 等）"),
                Map.entry("src/main/resources/db/migration/V2__llm_model_default_configs.sql", "LLM 模型默认配置数据"),
                Map.entry("src/main/resources/db/migration/V3__system_default_configs.sql", "系统默认配置数据"),
                Map.entry("src/main/resources/db/migration/V4__llm_price_configs.sql", "LLM 调用价格配置数据"),

                // ========== application 相关 ==========
                Map.entry("src/main/resources/application-perf.properties", "性能测试 profile 配置"),
                Map.entry("src/main/resources/logback-spring.xml", "Logback 日志配置（被 Log4j2 替代时仍可能保留）"),

                // ========== 前端补充 ==========
                Map.entry("my-vite-app/src/contexts", "React Context 目录（全局状态：登录态、主题、通知等）"),
                Map.entry("my-vite-app/src/lib", "前端工具库目录（如 shadcn utils、cn 合并工具）"),
                Map.entry("my-vite-app/src/testUtils", "前端单元测试辅助工具（Mock/渲染器/测试数据）"),
                Map.entry("my-vite-app/src/pages/admin", "后台管理页面组件目录"),
                Map.entry("my-vite-app/scripts", "前端构建/测试辅助脚本（覆盖率检查等）"),
                Map.entry("my-vite-app/test-reports", "前端测试报告输出目录"),
                Map.entry("my-vite-app/build", "前端产物输出目录（构建时使用）"),
                Map.entry("my-vite-app/dist", "前端生产环境打包产物目录"),
                Map.entry("my-vite-app/tailwind.config.js", "Tailwind CSS 配置文件（主题、插件、内容扫描路径）"),
                Map.entry("my-vite-app/postcss.config.js", "PostCSS 配置（tailwindcss + autoprefixer）"),
                Map.entry("my-vite-app/vitest.config.ts", "Vitest 测试框架配置（覆盖率、JSDOM 等）"),
                Map.entry("my-vite-app/.shadcnrc", "shadcn/ui CLI 配置文件")
        );

        List<PatternDesc> list = new ArrayList<>();
        for (var e : raw.entrySet()) {
            String globKey = e.getKey();
            String regex = globToRegex(globKey);
            Pattern p = Pattern.compile(regex);
            list.add(new PatternDesc(p, e.getValue()));
        }
        PATH_PATTERN_LIST = List.copyOf(list);
    }

    private static String globToRegex(String glob) {
        if (!glob.contains("/") && !glob.contains("*")) {
            glob = "**/" + glob;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("^");
        char[] chs = glob.toCharArray();
        for (int i = 0; i < chs.length; ) {
            char c = chs[i];
            if (c == '*') {
                if (i + 1 < chs.length && chs[i + 1] == '*') {
                    sb.append(".*");
                    i += 2;
                } else {
                    sb.append("[^/]*");
                    i++;
                }
            } else if (c == '?') {
                sb.append(".");
                i++;
            } else if (c == '/') {
                sb.append("/");
                i++;
            } else {
                sb.append(Pattern.quote(String.valueOf(c)));
                i++;
            }
        }
        sb.append("$");
        return sb.toString();
    }

    private static class Node {
        final List<Boolean> lastFlags;
        final boolean isDir;
        final String name;
        final String desc;
        final boolean isLast;

        Node(List<Boolean> lastFlags, boolean isDir, String name, String desc, boolean isLast) {
            this.lastFlags = lastFlags;
            this.isDir = isDir;
            this.name = name;
            this.desc = desc;
            this.isLast = isLast;
        }
    }

    public static void main(String[] args) throws IOException {
        Path projectRoot = resolveProjectRoot(args);
        if (!Files.isDirectory(projectRoot)) {
            System.err.println("无效的项目根目录: " + projectRoot);
            return;
        }
        generateDirectoryTreeMarkdown(projectRoot);
        System.out.println("完成 → " + projectRoot.resolve("tree.md"));
    }

    private static Path resolveProjectRoot(String[] args) {
        Path workspace = Paths.get(".").toAbsolutePath().normalize();
        if (args == null || args.length == 0 || args[0] == null || args[0].isBlank()) {
            return workspace;
        }
        String raw = args[0].trim();
        if (!raw.matches("[A-Za-z0-9_./\\\\:-]+")) {
            throw new IllegalArgumentException("非法路径参数");
        }
        if (raw.startsWith("/") || raw.startsWith("\\") || raw.matches("^[A-Za-z]:.*")) {
            throw new IllegalArgumentException("仅允许工作区内相对路径");
        }
        Path candidate = workspace.resolve(raw).normalize();
        if (!candidate.startsWith(workspace)) {
            throw new IllegalArgumentException("路径越界");
        }
        return candidate;
    }

    private static void generateDirectoryTreeMarkdown(Path root) throws IOException {
        List<Node> nodes = new ArrayList<>();
        collectNodes(root, root, new ArrayList<>(), nodes);

        int maxLen = 0;
        for (Node n : nodes) {
            String namePart = buildNamePart(n);
            maxLen = Math.max(maxLen, namePart.length());
        }

        Path outFile = root.resolve("tree.md");
        try (PrintStream out = new PrintStream(new FileOutputStream(outFile.toFile()), true, StandardCharsets.UTF_8)) {
            out.println("::: treeview");
            for (Node node : nodes) {
                String np = buildNamePart(node);
                StringBuilder line = new StringBuilder(np);
                if (node.desc != null && !node.desc.isBlank()) {
                    int pad = maxLen - np.length() + 1;
                    appendSpaces(line, pad);
                    line.append("- ").append(node.desc);
                }
                out.println(line);
            }
            out.println(":::");
        }
    }

    private static void collectNodes(Path dir, Path root, List<Boolean> ancestorsLastFlags, List<Node> nodes)
            throws IOException {
        Path rel = root.relativize(dir);
        String relStr = rel.toString().replace(dir.getFileSystem().getSeparator(), "/");

        String dirDesc = "";
        for (PatternDesc pd : PATH_PATTERN_LIST) {
            if (pd.pattern.matcher(relStr).matches()) {
                dirDesc = pd.desc;
                break;
            }
        }

        boolean isLastDir = ancestorsLastFlags.isEmpty() || ancestorsLastFlags.getLast();
        nodes.add(new Node(new ArrayList<>(ancestorsLastFlags), true, dir.getFileName().toString(), dirDesc, isLastDir));

        List<Path> all;
        try (Stream<Path> s = Files.list(dir)) {
            all = s.filter(p -> {
                        String name = p.getFileName().toString();
                        if (Files.isDirectory(p)) {
                            return !EXCLUDED_DIR_NAMES.contains(name.toLowerCase());
                        } else {
                            String ext = getExt(name).toLowerCase();
                            return !EXCLUDED_FILE_EXTENSIONS.contains(ext);
                        }
                    })
                    .sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase()))
                    .toList();
        }

        List<Path> dirs = all.stream().filter(Files::isDirectory).toList();
        List<Path> files = all.stream().filter(p -> !Files.isDirectory(p)).toList();

        for (int i = 0; i < dirs.size(); i++) {
            Path child = dirs.get(i);
            boolean isLast = (i == dirs.size() - 1) && files.isEmpty();
            List<Boolean> nextFlags = new ArrayList<>(ancestorsLastFlags);
            nextFlags.add(isLast);
            collectNodes(child, root, nextFlags, nodes);
        }

        Set<String> seenExt = new HashSet<>();
        for (int i = 0; i < files.size(); i++) {
            Path f = files.get(i);
            boolean isLast = (i == files.size() - 1);
            String name = f.getFileName().toString();
            String ext = getExt(name).toLowerCase();

            Path fileRel = root.relativize(f);
            String fileRelStr = fileRel.toString().replace(f.getFileSystem().getSeparator(), "/");
            String fileDesc = "";
            for (PatternDesc pd : PATH_PATTERN_LIST) {
                if (pd.pattern.matcher(fileRelStr).matches()) {
                    fileDesc = pd.desc;
                    break;
                }
            }
            if (fileDesc.isBlank() && !seenExt.contains(ext) && EXTENSION_DESCRIPTIONS.containsKey(ext)) {
                fileDesc = EXTENSION_DESCRIPTIONS.get(ext);
                seenExt.add(ext);
            }

            List<Boolean> nextFlags = new ArrayList<>(ancestorsLastFlags);
            nextFlags.add(isLast);
            nodes.add(new Node(nextFlags, false, name, fileDesc, isLast));
        }
    }

    private static String buildNamePart(Node node) {
        StringBuilder sb = new StringBuilder();
        int depth = node.lastFlags.size() - 1;
        for (int i = 0; i < depth; i++) {
            sb.append(node.lastFlags.get(i) ? "    " : "│   ");
        }
        if (!node.lastFlags.isEmpty()) {
            sb.append(node.isLast ? "└─ " : "├─ ");
        }
        sb.append(node.isDir ? "📁 " : "📄 ").append(node.name);
        return sb.toString();
    }

    private static void appendSpaces(StringBuilder sb, int count) {
        sb.repeat(' ', Math.max(0, count));
    }

    private static String getExt(String filename) {
        int idx = filename.lastIndexOf('.');
        if (idx >= 0 && idx < filename.length() - 1) {
            return filename.substring(idx + 1);
        }
        return "";
    }
}
