package tools;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 把 tree.md 作为"项目目录结构速查"附录合并进答辩清单 Markdown。
 * <p>
 * 用法：{@code java tools.MergeTreeIntoQaList <targetMdPath> <treeMdPath>}
 * <ul>
 *   <li>自动把 {@code ::: treeview} 围栏替换为 Markdown 代码块 {@code ```text}</li>
 *   <li>幂等：若目标文件已存在同名附录，会先移除再重新追加</li>
 *   <li>UTF-8 无 BOM 输出</li>
 * </ul>
 */
public class MergeTreeIntoQaList {

    private static final String APPENDIX_HEADING = "## 附录：项目目录结构速查（含中文注释）";

    private static final String APPENDIX_INTRO =
            "\r\n\r\n---\r\n\r\n" + APPENDIX_HEADING + "\r\n\r\n" +
            "> 由 `src/test/java/tools/DirectoryTreeMarkdownGenerator.java` 自动生成，" +
            "已排除 `build` / `.qoder` / `.trae` / `.workbuddy` / `bin` / `logs` / `uploads` 等缓存与镜像目录；" +
            "★ 标记为答辩高频定位点。\r\n\r\n";

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: java tools.MergeTreeIntoQaList <targetMdPath> <treeMdPath>");
            System.exit(1);
        }
        Path target = Paths.get(args[0]).toAbsolutePath().normalize();
        Path tree = Paths.get(args[1]).toAbsolutePath().normalize();

        if (!Files.isRegularFile(target)) {
            throw new IllegalArgumentException("目标 Markdown 不存在：" + target);
        }
        if (!Files.isRegularFile(tree)) {
            throw new IllegalArgumentException("tree.md 不存在：" + tree);
        }

        String orig = Files.readString(target, StandardCharsets.UTF_8);
        String treeRaw = Files.readString(tree, StandardCharsets.UTF_8);

        // 移除旧附录（幂等）：从 "---" 行到 "## 附录..." 之后的全部内容都算作旧附录
        int idx = orig.indexOf(APPENDIX_HEADING);
        if (idx >= 0) {
            String pre = orig.substring(0, idx);
            int sepIdx = pre.lastIndexOf("\n---");
            if (sepIdx >= 0) {
                // 退回到 --- 前面最近的换行
                int lineStart = pre.lastIndexOf('\n', sepIdx - 1);
                if (lineStart < 0) lineStart = 0;
                orig = pre.substring(0, lineStart);
            } else {
                orig = pre;
            }
        }

        // 把 ::: treeview / ::: 围栏替换为 Markdown 代码块，保证任何渲染器都能等宽显示
        String treeBody = treeRaw
                .replaceAll("(?m)^:::\\s*treeview\\s*$", "```text")
                .replaceAll("(?m)^:::\\s*$", "```");

        String merged = stripTrailingWhitespace(orig) + APPENDIX_INTRO + treeBody;
        if (!merged.endsWith("\r\n") && !merged.endsWith("\n")) {
            merged += "\r\n";
        }

        Files.writeString(target, merged, StandardCharsets.UTF_8);

        long bytes = Files.size(target);
        long lines = merged.lines().count();
        System.out.println("OK -> " + target);
        System.out.println("Size: " + bytes + " bytes, Lines: " + lines);
    }

    private static String stripTrailingWhitespace(String s) {
        int i = s.length();
        while (i > 0) {
            char c = s.charAt(i - 1);
            if (c == ' ' || c == '\t' || c == '\r' || c == '\n') {
                i--;
            } else {
                break;
            }
        }
        return s.substring(0, i);
    }
}
