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
            "build", ".gradle", "dist", "node_modules", "out", ".idea", ".git", ".vscode", "target", "obj", "tmp", "temp"
    );

    // 排除的文件后缀
    private static final Set<String> EXCLUDED_FILE_EXTENSIONS = Set.of(
            "css", "png", "jpg", "jpeg", "gif", "svg", "ico", "bmp", "webp"
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
                Map.entry("my-vite-app/src/components/ui", "Shadcn UI 组件目录，包含全局样式和组件")
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
