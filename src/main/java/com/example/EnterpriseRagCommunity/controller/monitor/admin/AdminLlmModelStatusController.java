package com.example.EnterpriseRagCommunity.controller.monitor.admin;

import com.example.EnterpriseRagCommunity.dto.monitor.AdminLlmModelCallRecordDTO;
import com.example.EnterpriseRagCommunity.dto.monitor.AdminLlmModelStatusItemDTO;
import com.example.EnterpriseRagCommunity.dto.monitor.AdminLlmModelStatusResponseDTO;
import com.example.EnterpriseRagCommunity.service.ai.LlmCallQueueService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/llm/status")
@CrossOrigin(origins = {"http://localhost:5173", "http://127.0.0.1:5173"}, allowCredentials = "true")
@RequiredArgsConstructor
public class AdminLlmModelStatusController {

    private final LlmCallQueueService llmCallQueueService;
    private static final Pattern HTTP_CODE = Pattern.compile("\\bHTTP\\s+(\\d{3})\\b", Pattern.CASE_INSENSITIVE);

    private static String enumName(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private static String trim(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    @GetMapping
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_users','read'))")
    public AdminLlmModelStatusResponseDTO status(
            @RequestParam(value = "windowSec", required = false) Integer windowSec,
            @RequestParam(value = "perModel", required = false) Integer perModel
    ) {
        int win = windowSec == null ? 300 : Math.clamp(windowSec, 10, 3600);
        int per = perModel == null ? 10 : Math.clamp(perModel, 1, 100);

        LlmCallQueueService.QueueSnapshot snap = llmCallQueueService.snapshot(500, 0, 5000);
        Map<String, AdminLlmModelStatusItemDTO> byKey = new HashMap<>();

        for (LlmCallQueueService.TaskSnapshot t : snap.running()) {
            if (t == null) continue;
            String providerId = trim(t.getProviderId());
            String model = trim(t.getModel());
            if (providerId == null || model == null) continue;
            AdminLlmModelStatusItemDTO item = ensureModelItem(byKey, providerId, model);
            Integer rc = item.getRunningCount();
            item.setRunningCount((rc == null ? 0 : rc) + 1);
        }

        long cutoff = System.currentTimeMillis() - win * 1000L;
        for (LlmCallQueueService.TaskSnapshot t : snap.recentCompleted()) {
            if (t == null) continue;
            Long finishedAt = t.getFinishedAtMs();
            if (finishedAt == null || finishedAt < cutoff) continue;
            String providerId = trim(t.getProviderId());
            String model = trim(t.getModel());
            if (providerId == null || model == null) continue;
            AdminLlmModelStatusItemDTO item = ensureModelItem(byKey, providerId, model);
            List<AdminLlmModelCallRecordDTO> list = item.getRecords();
            if (list.size() >= per) continue;
            AdminLlmModelCallRecordDTO r = new AdminLlmModelCallRecordDTO();
            r.setTaskId(t.getId());
            r.setTaskType(enumName(t.getType()));
            r.setStatus(enumName(t.getStatus()));
            boolean ok = t.getStatus() != null && t.getStatus() == com.example.EnterpriseRagCommunity.service.ai.LlmQueueTaskStatus.DONE;
            r.setOk(ok);
            r.setTsMs(t.getFinishedAtMs());
            r.setDurationMs(t.getDurationMs());
            r.setTokensIn(t.getTokensIn());
            r.setTokensOut(t.getTokensOut());
            r.setTotalTokens(t.getTotalTokens());
            String err = t.getError();
            if (err != null) {
                r.setErrorCode(extractErrorCode(err));
                r.setErrorMessage(err);
            } else {
                r.setErrorCode("");
                r.setErrorMessage("");
            }
            list.add(r);
        }

        List<AdminLlmModelStatusItemDTO> items = new ArrayList<>(byKey.values());
        AdminLlmModelStatusResponseDTO out = new AdminLlmModelStatusResponseDTO();
        out.setCheckedAtMs(System.currentTimeMillis());
        out.setWindowSec(win);
        out.setPerModel(per);
        out.setModels(items);
        return out;
    }

    private static AdminLlmModelStatusItemDTO ensureModelItem(Map<String, AdminLlmModelStatusItemDTO> byKey,
                                                             String providerId,
                                                             String model) {
        String key = providerId + "|" + model;
        return byKey.computeIfAbsent(key, _k -> {
            AdminLlmModelStatusItemDTO x = new AdminLlmModelStatusItemDTO();
            x.setProviderId(providerId);
            x.setModelName(model);
            x.setRunningCount(0);
            x.setRecords(new ArrayList<>());
            return x;
        });
    }

    private static String extractErrorCode(String message) {
        if (message == null || message.isBlank()) return "";
        Matcher m = HTTP_CODE.matcher(message);
        if (m.find()) return m.group(1);
        String low = message.toLowerCase();
        if (low.contains("429") || low.contains("too many requests") || low.contains("rate limit")) return "429";
        if (low.contains("401") || low.contains("unauthorized")) return "401";
        if (low.contains("403") || low.contains("forbidden")) return "403";
        if (low.contains("timeout") || low.contains("timed out")) return "timeout";
        if (low.contains("unknownhost")) return "dns";
        if (low.contains("connection reset")) return "reset";
        if (low.contains("connect")) return "connect";
        return "";
    }
}
