package com.example.EnterpriseRagCommunity.service.ai;

import com.example.EnterpriseRagCommunity.dto.ai.AiPostComposeStreamRequest;
import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.entity.content.PostComposeAiSnapshotsEntity;
import com.example.EnterpriseRagCommunity.entity.content.enums.PostComposeAiSnapshotStatus;
import com.example.EnterpriseRagCommunity.repository.access.UsersRepository;
import com.example.EnterpriseRagCommunity.repository.ai.LlmModelRepository;
import com.example.EnterpriseRagCommunity.repository.content.PostComposeAiSnapshotsRepository;
import com.example.EnterpriseRagCommunity.repository.monitor.FileAssetsRepository;
import com.example.EnterpriseRagCommunity.service.ai.dto.ChatMessage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AiPostComposeAssistantService {
    private static final String ENV_DEFAULT = "default";

    private final PostComposeAiSnapshotsRepository snapshotsRepository;
    private final UsersRepository usersRepository;
    private final LlmGateway llmGateway;
    private final LlmModelRepository llmModelRepository;
    private final FileAssetsRepository fileAssetsRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.upload.root:uploads}")
    private String uploadRoot;

    @Value("${app.upload.url-prefix:/uploads}")
    private String urlPrefix;

    public void streamComposeEdit(AiPostComposeStreamRequest req, Long currentUserId, HttpServletResponse response) throws IOException {
        PostComposeAiSnapshotsEntity snap = snapshotsRepository.findByIdAndUserId(req.getSnapshotId(), currentUserId)
                .orElseThrow(() -> new IllegalArgumentException("快照不存在或无权访问"));
        if (snap.getStatus() != PostComposeAiSnapshotStatus.PENDING) {
            throw new IllegalStateException("快照已处理");
        }
        if (snap.getExpiresAt() != null && snap.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalStateException("快照已过期");
        }

        response.setCharacterEncoding("UTF-8");
        response.setContentType("text/event-stream;charset=UTF-8");
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Connection", "keep-alive");
        PrintWriter out = response.getWriter();

        out.write("event: meta\n");
        out.write("data: {\"snapshotId\":" + snap.getId() + "}\n\n");
        out.flush();

        boolean deepThink = Boolean.TRUE.equals(req.getDeepThink());
        String baseSystemPrompt = deepThink
                ? "你是一个严谨、专业的中文助手。请在回答前进行更充分的推理与自检，输出更可靠、结构化的结论；不确定时说明不确定并给出验证建议。"
                : "你是一个严谨、专业的中文助手。";

        String userDefaultSystemPrompt = loadUserDefaultSystemPrompt(currentUserId);

        String composeSystemPrompt =
                "你是一名发帖编辑助手。你要帮助用户完成“可发布的 Markdown 正文”，并在必要时与用户沟通确认细节。\n" +
                "你必须严格遵守以下输出协议（非常重要）：\n" +
                "1) 你只允许输出两类内容块，并且所有输出必须被包裹在其中之一：\n" +
                "   - <chat>...</chat>：与用户沟通（提问、确认、解释、澄清）。这部分只会显示在聊天窗口，不会写入正文。\n" +
                "   - <post>...</post>：帖子最终 Markdown 正文。这部分只会写入正文编辑框，不会显示在聊天窗口。\n" +
                "2) 当信息不足、需要用户确认/补充时：只输出 <chat>，不要输出 <post>。\n" +
                "3) 当你输出 <post> 时：内容必须是完整、可发布的最终 Markdown 正文；不要解释你的思考过程；不要使用```包裹正文。\n" +
                "4) 不要杜撰事实；缺少信息时在 <chat> 提问，或在 <post> 中用占位符明确标记缺失信息。\n" +
                "5) 若用户明确要求“直接写入正文/直接改写/不要提问/给出最终稿”，你必须直接输出 <post>，不要继续在 <chat> 中拉扯确认。\n" +
                "6) 标签必须使用半角尖括号：<post>/<chat>，不要转义为 &lt;post&gt;，也不要使用全角括号。\n" +
                "7) 除 <chat> 或 <post> 之外不要输出任何其他文本。\n";

        String instruction = firstNonBlank(req.getInstruction(), snap.getInstruction());
        if (!StringUtils.hasText(instruction)) {
            instruction = "请将正文改写为更适合发布的 Markdown，包含清晰的小标题与要点列表。";
        }

        String effectiveTitle = StringUtils.hasText(req.getCurrentTitle()) ? req.getCurrentTitle() : snap.getBeforeTitle();
        String effectiveContent = req.getCurrentContent() != null ? req.getCurrentContent() : snap.getBeforeContent();
        String userMsg = buildUserMessage(effectiveTitle, effectiveContent, instruction);

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system(baseSystemPrompt));
        if (StringUtils.hasText(userDefaultSystemPrompt)) {
            messages.add(ChatMessage.system(userDefaultSystemPrompt));
        }
        messages.add(ChatMessage.system(composeSystemPrompt));
        if (req.getChatHistory() != null) {
            for (AiPostComposeStreamRequest.ChatHistoryMessage m : req.getChatHistory()) {
                if (m == null) continue;
                String role = m.getRole() == null ? "" : m.getRole().trim().toLowerCase();
                String content = m.getContent();
                if (!StringUtils.hasText(content)) continue;
                if ("assistant".equals(role)) {
                    messages.add(ChatMessage.assistant(content));
                } else {
                    messages.add(ChatMessage.user(content));
                }
            }
        }

        String providerId = firstNonBlank(req.getProviderId(), snap.getProviderId());
        String model = firstNonBlank(req.getModel(), snap.getModel());
        Double temperature = req.getTemperature() != null ? req.getTemperature() : snap.getTemperature();
        if (temperature == null && deepThink) temperature = 0.2;
        Double topP = req.getTopP() != null ? req.getTopP() : snap.getTopP();

        List<AiPostComposeStreamRequest.ImageInput> images = resolveImages(req, snap);
        boolean hasImages = images != null && !images.isEmpty();
        LlmQueueTaskType chatTaskType = hasImages ? LlmQueueTaskType.IMAGE_CHAT : LlmQueueTaskType.TEXT_CHAT;
        try {
            ensureVisionModelForRequest(chatTaskType, providerId, model, hasImages);
        } catch (Exception e) {
            out.write("event: error\n");
            out.write("data: {\"message\":\"" + jsonEscape(String.valueOf(e.getMessage() == null ? "请求失败" : e.getMessage())) + "\"}\n\n");
            out.write("event: done\n");
            out.write("data: {}\n\n");
            out.flush();
            return;
        }
        List<ChatMessage> messagesMultimodal = new ArrayList<>(messages);
        if (hasImages) {
            List<Map<String, Object>> parts = new ArrayList<>();
            parts.add(Map.of("type", "text", "text", userMsg));
            for (AiPostComposeStreamRequest.ImageInput img : images) {
                String url = encodeImageUrlForUpstream(img);
                if (!StringUtils.hasText(url)) continue;
                parts.add(Map.of("type", "image_url", "image_url", Map.of("url", url)));
            }
            messagesMultimodal.add(ChatMessage.userParts(parts));
        } else {
            messagesMultimodal.add(ChatMessage.user(userMsg));
        }

        StringBuilder assistantAccum = new StringBuilder();
        long startedAt = System.currentTimeMillis();
        boolean[] gotDelta = new boolean[] {false};
        try {
            llmGateway.chatStreamRouted(
                    chatTaskType,
                    providerId,
                    model,
                    messagesMultimodal,
                    temperature,
                    topP,
                    deepThink,
                    null,
                    line -> {
                        if (line == null || line.isBlank()) return;
                        if (!line.startsWith("data:")) return;
                        String data = line.substring("data:".length()).trim();
                        if ("[DONE]".equals(data)) return;
                        String content = extractDeltaContent(data);
                        if (!StringUtils.hasText(content)) return;
                        gotDelta[0] = true;
                        assistantAccum.append(content);
                        out.write("event: delta\n");
                        out.write("data: {\"content\":\"" + jsonEscape(content) + "\"}\n\n");
                        out.flush();
                    }
            );
        } catch (Exception ex) {
            out.write("event: error\n");
            out.write("data: {\"message\":\"" + jsonEscape("上游AI调用失败：" + String.valueOf(ex.getMessage())) + "\"}\n\n");
            out.write("event: done\n");
            out.write("data: {}\n\n");
            out.flush();
            return;
        }

        long latency = Math.max(0, System.currentTimeMillis() - startedAt);
        out.write("event: done\n");
        out.write("data: {\"latencyMs\":" + latency + "}\n\n");
        out.flush();
    }

    private String loadUserDefaultSystemPrompt(Long userId) {
        UsersEntity u = usersRepository.findById(userId).orElse(null);
        if (u == null) return null;
        Map<String, Object> metadata = u.getMetadata();
        if (metadata == null) return null;
        Object prefs = metadata.get("preferences");
        if (!(prefs instanceof Map)) return null;
        Object assistant = ((Map<?, ?>) prefs).get("assistant");
        if (!(assistant instanceof Map)) return null;
        Object v = ((Map<?, ?>) assistant).get("defaultSystemPrompt");
        if (v == null) return null;
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? null : s;
    }

    private void ensureVisionModelForRequest(LlmQueueTaskType taskType, String providerId, String modelOverride, boolean hasImages) {
        if (!hasImages) return;
        if (taskType != LlmQueueTaskType.IMAGE_CHAT) return;

        String mo = firstNonBlank(modelOverride, null);
        String pid = firstNonBlank(providerId, null);
        if (mo != null) {
            String effectiveProviderId = pid;
            if (effectiveProviderId == null) {
                try {
                    var p = llmGateway.resolve(null);
                    effectiveProviderId = p == null ? null : firstNonBlank(p.id(), null);
                } catch (Exception ignored) {
                }
            }
            if (effectiveProviderId == null) {
                throw new IllegalArgumentException("未指定模型来源(providerId)，无法发送图片");
            }
            if (!isEnabledImageChatModel(effectiveProviderId, mo)) {
                throw new IllegalArgumentException("当前选择的模型不支持图片，请选择视觉模型（图片聊天）或切换为“自动(均衡负载)”");
            }
            return;
        }

        if (pid != null) {
            try {
                var p = llmGateway.resolve(pid);
                String effectiveProviderId = p == null ? null : firstNonBlank(p.id(), null);
                String effectiveModel = p == null ? null : firstNonBlank(p.defaultChatModel(), null);
                if (effectiveProviderId == null || effectiveModel == null) {
                    throw new IllegalArgumentException("未配置可用的默认模型，无法发送图片");
                }
                if (!isEnabledImageChatModel(effectiveProviderId, effectiveModel)) {
                    throw new IllegalArgumentException("当前选择的模型不支持图片，请选择视觉模型（图片聊天）或切换为“自动(均衡负载)”");
                }
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalArgumentException("模型来源解析失败，无法发送图片");
            }
            return;
        }

        if (llmModelRepository.findByEnvAndPurposeAndEnabledTrueOrderBySortIndexAscPriorityDescWeightDescIsDefaultDescIdAsc(ENV_DEFAULT, "IMAGE_CHAT").isEmpty()) {
            throw new IllegalArgumentException("未配置“图片聊天(IMAGE_CHAT)”模型池，请在管理端为图片聊天配置视觉模型");
        }
    }

    private boolean isEnabledImageChatModel(String providerId, String modelName) {
        String pid = firstNonBlank(providerId, null);
        String mn = firstNonBlank(modelName, null);
        if (pid == null || mn == null) return false;
        return llmModelRepository.findByEnvAndProviderIdAndPurposeAndModelName(ENV_DEFAULT, pid, "IMAGE_CHAT", mn)
                .filter((e) -> !Boolean.FALSE.equals(e.getEnabled()))
                .isPresent();
    }

    private static String buildUserMessage(String title, String beforeContent, String instruction) {
        String t = title == null ? "" : title.trim();
        String c = beforeContent == null ? "" : beforeContent;
        return "标题：" + (t.isEmpty() ? "（无标题）" : t) + "\n\n" +
                "当前正文（Markdown）：\n" +
                "-----\n" +
                c +
                "\n-----\n\n" +
                "用户要求：\n" +
                instruction +
                "\n\n" +
                "输出协议（必须遵守）：只输出 <chat>...</chat> 或 <post>...</post>；不要转义标签。\n" +
                "\n";
    }

    private static List<AiPostComposeStreamRequest.ImageInput> resolveImages(AiPostComposeStreamRequest req, PostComposeAiSnapshotsEntity snap) {
        List<AiPostComposeStreamRequest.ImageInput> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        java.util.function.BiConsumer<Long, AiPostComposeStreamRequest.ImageInput> add = (fileAssetId, in) -> {
            if (out.size() >= 5) return;
            if (in == null) return;
            String url = toNonBlank(in.getUrl());
            if (url == null) return;
            if (seen.contains(url)) return;
            String mimeType = toNonBlank(in.getMimeType());
            boolean isImg = mimeType != null && mimeType.toLowerCase().startsWith("image/");
            if (!isImg && !isLikelyImageUrl(url)) return;
            AiPostComposeStreamRequest.ImageInput x = new AiPostComposeStreamRequest.ImageInput();
            x.setFileAssetId(fileAssetId);
            x.setUrl(url);
            x.setMimeType(mimeType);
            out.add(x);
            seen.add(url);
        };

        if (snap != null) {
            Map<String, Object> meta = snap.getBeforeMetadata();
            if (meta != null) {
                Object atts = meta.get("attachments");
                if (atts instanceof List<?> list) {
                    for (Object o : list) {
                        if (!(o instanceof Map<?, ?> m)) continue;
                        String url = null;
                        Object v = m.get("fileUrl");
                        if (v != null) url = String.valueOf(v);
                        if (url == null || url.isBlank()) {
                            Object u2 = m.get("url");
                            if (u2 != null) url = String.valueOf(u2);
                        }
                        String mt = null;
                        Object mtObj = m.get("mimeType");
                        if (mtObj != null) mt = String.valueOf(mtObj);
                        Long fileAssetId = null;
                        Object idObj = m.get("fileAssetId");
                        if (idObj == null) idObj = m.get("id");
                        if (idObj != null) {
                            try {
                                fileAssetId = Long.valueOf(String.valueOf(idObj));
                            } catch (Exception ignored) {
                                fileAssetId = null;
                            }
                        }
                        AiPostComposeStreamRequest.ImageInput in = new AiPostComposeStreamRequest.ImageInput();
                        in.setUrl(url);
                        in.setMimeType(mt);
                        add.accept(fileAssetId, in);
                        if (out.size() >= 5) break;
                    }
                }
            }
        }

        if (req != null && req.getImages() != null) {
            for (AiPostComposeStreamRequest.ImageInput img : req.getImages()) {
                if (img == null) continue;
                add.accept(img.getFileAssetId(), img);
                if (out.size() >= 5) break;
            }
        }
        return out;
    }

    private static boolean isLikelyImageUrl(String url) {
        String u = toNonBlank(url);
        if (u == null) return false;
        String lower = u.toLowerCase();
        if (lower.startsWith("/uploads/")) return true;
        return lower.endsWith(".png")
                || lower.endsWith(".jpg")
                || lower.endsWith(".jpeg")
                || lower.endsWith(".gif")
                || lower.endsWith(".webp")
                || lower.endsWith(".bmp")
                || lower.endsWith(".svg");
    }

    private String encodeImageUrlForUpstream(AiPostComposeStreamRequest.ImageInput img) {
        if (img == null) return null;
        String url = toNonBlank(img.getUrl());
        if (url == null) return null;

        if (url.startsWith("data:") || url.startsWith("http://") || url.startsWith("https://")) {
            return url;
        }

        byte[] bytes = readLocalUploadBytes(img.getFileAssetId(), url);
        if (bytes == null || bytes.length == 0) return url;
        if (bytes.length > 4_000_000) return url;

        String mimeType = toNonBlank(img.getMimeType());
        if (!StringUtils.hasText(mimeType) && img.getFileAssetId() != null) {
            var fa = fileAssetsRepository.findById(img.getFileAssetId()).orElse(null);
            mimeType = fa == null ? null : toNonBlank(fa.getMimeType());
        }
        if (!StringUtils.hasText(mimeType)) mimeType = "application/octet-stream";

        return "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(bytes);
    }

    private byte[] readLocalUploadBytes(Long fileAssetId, String url) {
        try {
            if (fileAssetId != null) {
                var fa = fileAssetsRepository.findById(fileAssetId).orElse(null);
                if (fa != null && fa.getPath() != null && !fa.getPath().isBlank()) {
                    Path p = Paths.get(fa.getPath()).toAbsolutePath().normalize();
                    if (Files.exists(p) && Files.isRegularFile(p)) {
                        return Files.readAllBytes(p);
                    }
                }
            }

            String prefix = urlPrefix == null ? "/uploads" : urlPrefix.trim();
            String u = toNonBlank(url);
            if (u == null || prefix.isEmpty()) return null;
            if (!u.startsWith(prefix + "/")) return null;

            int q = u.indexOf('?');
            if (q >= 0) u = u.substring(0, q);
            String rel = u.substring(prefix.length());
            while (rel.startsWith("/")) rel = rel.substring(1);

            Path root = Paths.get(uploadRoot == null ? "uploads" : uploadRoot).toAbsolutePath().normalize();
            Path p = root.resolve(rel).normalize();
            if (!p.startsWith(root)) return null;
            if (!Files.exists(p) || !Files.isRegularFile(p)) return null;
            return Files.readAllBytes(p);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String appendImagesAsText(String userMsg, List<AiPostComposeStreamRequest.ImageInput> images) {
        String base = userMsg == null ? "" : userMsg;
        StringBuilder sb = new StringBuilder(base);
        sb.append("\n\n[IMAGES]\n");
        int take = 0;
        for (AiPostComposeStreamRequest.ImageInput img : images) {
            if (img == null) continue;
            String url = toNonBlank(img.getUrl());
            if (url == null) continue;
            sb.append("- ").append(url).append("\n");
            take += 1;
            if (take >= 5) break;
        }
        return sb.toString();
    }

    private String extractDeltaContent(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) return null;
            JsonNode first = choices.get(0);
            JsonNode delta = first.path("delta");
            JsonNode content = delta.path("content");
            if (content.isTextual()) return content.asText();
            JsonNode text = first.path("text");
            if (text.isTextual()) return text.asText();
            return null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String firstNonBlank(String a, String b) {
        String aa = a == null ? "" : a.trim();
        if (!aa.isEmpty()) return aa;
        String bb = b == null ? "" : b.trim();
        return bb.isEmpty() ? null : bb;
    }

    private static String toNonBlank(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static String jsonEscape(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.toString();
    }
}
