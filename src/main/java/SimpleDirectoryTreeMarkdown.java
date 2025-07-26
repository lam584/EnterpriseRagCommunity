import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.FileSystems;
import java.nio.file.FileSystem;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class SimpleDirectoryTreeMarkdown {

    // 全局排除目录名（不区分大小写）
    private static final Set<String> EXCLUDED_DIR_NAMES = Set.of(
            "node_modules"
//            "build", ".gradle", "dist", "node_modules", "out",".idea", ".git", ".vscode", "target",  "obj", "tmp", "temp"
    );

    // 排除的文件后缀
    private static final Set<String> EXCLUDED_FILE_EXTENSIONS = Set.of(
//            "css", "png", "jpg", "jpeg", "gif", "svg", "ico", "bmp", "webp"
    );

    // 常见扩展名描述
    private static final Map<String, String> EXTENSION_DESCRIPTIONS = Map.ofEntries(

    );

    // ==== 1) 把原来那个 raw Map 里的 key（带 “/” 分隔和通配符“*”、“**”的 glob）编成 regex Pattern
    private static class PatternDesc {
        final Pattern pattern;
        final String desc;
        PatternDesc(Pattern pattern, String desc) {
            this.pattern = pattern;
            this.desc = desc;
        }
    }

    // 2) 在这里写死所有要注释的路径或文件名 glob
    private static final List<PatternDesc> PATH_PATTERN_LIST;
    static {
        Map<String,String> raw = Map.ofEntries(


        );

        List<PatternDesc> list = new ArrayList<>();
        for (var e : raw.entrySet()) {
            String globKey = e.getKey();
            String regex = globToRegex(globKey);
            Pattern       p = Pattern.compile(regex);
            list.add(new PatternDesc(p, e.getValue()));
        }
        PATH_PATTERN_LIST = List.copyOf(list);
    }

    //** 把一个类似 "**/src/main/java/**/config" 这样的 glob 转为 "^(.*/)?src/main/java/.*/config$" 形式的正则 */
    private static String globToRegex(String glob) {
        // 如果没有目录分隔符，也没写 '*'，就默认在前面加 "**/"，
        // 这样 bareName → "**/bareName"，既能匹配根目录下的文件，也能匹配子目录。
        if (!glob.contains("/") && !glob.contains("*")) {
            glob = "**/" + glob;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("^");
        char[] chs = glob.toCharArray();
        for (int i = 0; i < chs.length; ) {
            char c = chs[i];
            if (c == '*') {
                if (i+1 < chs.length && chs[i+1] == '*') {
                    // "**" → 匹配任意字符，包括斜杠
                    sb.append(".*");
                    i += 2;
                } else {
                    // "*" → 不跨目录的任意字符
                    sb.append("[^/]*");
                    i ++;
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

    // 表示一个节点（目录/文件）
    private static class Node {
        final List<Boolean> lastFlags;
        final boolean isDir;
        final String name;
        final String desc;
        final boolean isLast;
        Node(List<Boolean> lastFlags, boolean isDir, String name, String desc, boolean isLast) {
            this.lastFlags = lastFlags;
            this.isDir     = isDir;
            this.name      = name;
            this.desc      = desc;
            this.isLast    = isLast;
        }
    }

    public static void main(String[] args) throws IOException {
        Path projectRoot = Paths.get("G:\\期末项目\\未完成\\微信小程序\\考核2项目");
        if (!Files.isDirectory(projectRoot)) {
            System.err.println("无效的项目根目录: " + projectRoot);
            return;
        }
        generateDirectoryTreeMarkdown(projectRoot);
        System.out.println("完成 → " + projectRoot.resolve("tree.md"));
    }

    private static void generateDirectoryTreeMarkdown(Path root) throws IOException {
        List<Node> nodes = new ArrayList<>();
        collectNodes(root, root, new ArrayList<>(), nodes);

        // 先算出最宽的名字列
        int maxLen = 0;
        for (Node n : nodes) {
            String namePart = buildNamePart(n);
            maxLen = Math.max(maxLen, namePart.length());
        }

        Path outFile = root.resolve("tree.md");
        try (PrintStream out = new PrintStream(
                new FileOutputStream(outFile.toFile()), true, "UTF-8")) {
            out.println("::: treeview");
            for (Node node : nodes) {
                String np = buildNamePart(node);
                StringBuilder line = new StringBuilder(np);
                if (node.desc != null && !node.desc.isBlank()) {
                    int pad = maxLen - np.length() + 1;
                    for (int i = 0; i < pad; i++) line.append(' ');
                    line.append("- ").append(node.desc);
                }
                out.println(line);
            }
            out.println(":::");
        }
    }

    private static void collectNodes(Path dir,
                                     Path root,
                                     List<Boolean> ancestorsLastFlags,
                                     List<Node> nodes) throws IOException {
        // 1) 计算相对路径并把分隔符统一成 "/"
        Path rel = root.relativize(dir);
        String relStr = rel.toString().replace(dir.getFileSystem().getSeparator(), "/");

        // 2) 在所有 PatternDesc 中找第一个 match
        String dirDesc = "";
        for (PatternDesc pd : PATH_PATTERN_LIST) {
            if (pd.pattern.matcher(relStr).matches()) {
                dirDesc = pd.desc;
                break;
            }
        }

        // 3) 本目录自己作为一个节点
        boolean isLastDir = ancestorsLastFlags.isEmpty() || ancestorsLastFlags.getLast();
        nodes.add(new Node(
                new ArrayList<>(ancestorsLastFlags),
                true,
                dir.getFileName().toString(),
                dirDesc,
                isLastDir
        ));

        // 4) 列出子项，先目录后文件，过滤
        List<Path> all = Files.list(dir)
                .filter(p -> {
                    String name = p.getFileName().toString();
                    if (Files.isDirectory(p)) {
                        return !EXCLUDED_DIR_NAMES.contains(name.toLowerCase());
                    } else {
                        String ext = getExt(name).toLowerCase();
                        return !EXCLUDED_FILE_EXTENSIONS.contains(ext);
                    }
                })
                .sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase()))
                .collect(Collectors.toList());

        List<Path> dirs  = all.stream().filter(Files::isDirectory).collect(Collectors.toList());
        List<Path> files = all.stream().filter(p->!Files.isDirectory(p)).collect(Collectors.toList());

        // 5) 递归子目录，每层都重新 new 一个 annotatedExtensionsInThisDir
        for (int i = 0; i < dirs.size(); i++) {
            Path child = dirs.get(i);
            boolean isLast = (i == dirs.size()-1) && files.isEmpty();
            List<Boolean> nextFlags = new ArrayList<>(ancestorsLastFlags);
            nextFlags.add(isLast);
            collectNodes(child, root, nextFlags, nodes);
        }

        // 6) 处理文件，文件可以享受“同目录内扩展名首次注释”
        Set<String> seenExt = new HashSet<>();
        for (int i = 0; i < files.size(); i++) {
            Path f = files.get(i);
            boolean isLast = (i == files.size()-1);
            String name = f.getFileName().toString();
            String ext = getExt(name).toLowerCase();

            // (a) 先 glob 匹配
            Path fileRel = root.relativize(f);
            String fileRelStr = fileRel.toString().replace(f.getFileSystem().getSeparator(), "/");
            String fileDesc = "";
            for (PatternDesc pd : PATH_PATTERN_LIST) {
                if (pd.pattern.matcher(fileRelStr).matches()) {
                    fileDesc = pd.desc;
                    break;
                }
            }
            // (b) 若还没找到注释，而且同目录没见过这个 ext，就用扩展名注释
            if (fileDesc.isBlank() && !seenExt.contains(ext)
                    && EXTENSION_DESCRIPTIONS.containsKey(ext)) {
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

    private static String getExt(String filename) {
        int idx = filename.lastIndexOf('.');
        if (idx >= 0 && idx < filename.length() - 1) {
            return filename.substring(idx + 1);
        }
        return "";
    }
}