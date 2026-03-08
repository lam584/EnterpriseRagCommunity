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
import java.util.stream.Collectors;

public class SimpleDirectoryTreeMarkdown {

    private static final Set<String> EXCLUDED_DIR_NAMES = Set.of(
            "node_modules"
    );

    private static final Set<String> EXCLUDED_FILE_EXTENSIONS = Set.of();

    private static final Map<String, String> EXTENSION_DESCRIPTIONS = Map.ofEntries();

    private static class PatternDesc {
        final Pattern pattern;
        final String desc;

        PatternDesc(Pattern pattern, String desc) {
            this.pattern = pattern;
            this.desc = desc;
        }
    }

    private static final List<PatternDesc> PATH_PATTERN_LIST = List.of();

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
        Path projectRoot = args.length > 0 ? Paths.get(args[0]) : Paths.get(".");
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
                    line.append(" ".repeat(Math.max(0, pad)));
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

        List<Path> dirs = all.stream().filter(Files::isDirectory).collect(Collectors.toList());
        List<Path> files = all.stream().filter(p -> !Files.isDirectory(p)).collect(Collectors.toList());

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

    private static String getExt(String filename) {
        int idx = filename.lastIndexOf('.');
        if (idx >= 0 && idx < filename.length() - 1) {
            return filename.substring(idx + 1);
        }
        return "";
    }
}
