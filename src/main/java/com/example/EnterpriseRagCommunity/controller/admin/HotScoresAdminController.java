package com.example.EnterpriseRagCommunity.controller.admin;

import com.example.EnterpriseRagCommunity.dto.content.HotScoreConfigDTO;
import com.example.EnterpriseRagCommunity.service.content.HotScoreConfigService;
import com.example.EnterpriseRagCommunity.service.content.HotScoresService;
import org.springframework.data.domain.Page;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.util.Assert;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/hot-scores")
public class HotScoresAdminController {

    private final HotScoresService hotScoresService;
    private final HotScoreConfigService hotScoreConfigService;

    public HotScoresAdminController(HotScoresService hotScoresService,
                                    ObjectProvider<HotScoreConfigService> hotScoreConfigServiceProvider) {
        Assert.notNull(hotScoresService, "HotScoresService must not be null!");
        Assert.notNull(hotScoreConfigServiceProvider, "HotScoreConfigService provider must not be null!");
        this.hotScoresService = hotScoresService;
        this.hotScoreConfigService = hotScoreConfigServiceProvider.getIfAvailable();
    }

    private HotScoreConfigService configServiceOrThrow() {
        if (hotScoreConfigService == null) {
            throw new IllegalStateException("热度配置服务不可用");
        }
        return hotScoreConfigService;
    }

    @GetMapping("/config")
    @PreAuthorize("isAuthenticated()")
    public HotScoreConfigDTO getConfig() {
        return configServiceOrThrow().getConfigOrDefault();
    }

    @PutMapping("/config")
    @PreAuthorize("isAuthenticated()")
    public HotScoreConfigDTO updateConfig(@RequestBody HotScoreConfigDTO payload) {
        return configServiceOrThrow().upsertConfig(payload);
    }

    /**
     * 手动触发热度分重算（24h 窗口）。
     */
    @PostMapping("/recompute-24h")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> recompute24h() {
        return ResponseEntity.ok(toApiResult(hotScoresService.recompute24hHourlyWithResult(), "H24"));
    }

    /**
     * 手动触发热度分重算（7d 窗口）。
     */
    @PostMapping("/recompute-7d")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> recompute7d() {
        return ResponseEntity.ok(toApiResult(hotScoresService.recomputeWindowWithResult(HotScoresService.Window.D7), "D7"));
    }

    /**
     * 手动触发热度分重算（30d 窗口）。
     */
    @PostMapping("/recompute-30d")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> recompute30d() {
        return ResponseEntity.ok(toApiResult(hotScoresService.recomputeWindowWithResult(HotScoresService.Window.D30), "D30"));
    }

    /**
     * 手动触发热度分重算（3 个月窗口）。
     */
    @PostMapping("/recompute-3m")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> recompute3m() {
        return ResponseEntity.ok(toApiResult(hotScoresService.recomputeWindowWithResult(HotScoresService.Window.M3), "M3"));
    }

    /**
     * 手动触发热度分重算（半年窗口）。
     */
    @PostMapping("/recompute-6m")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> recompute6m() {
        return ResponseEntity.ok(toApiResult(hotScoresService.recomputeWindowWithResult(HotScoresService.Window.M6), "M6"));
    }

    /**
     * 手动触发热度分重算（1 年窗口）。
     */
    @PostMapping("/recompute-1y")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> recompute1y() {
        return ResponseEntity.ok(toApiResult(hotScoresService.recomputeWindowWithResult(HotScoresService.Window.Y1), "Y1"));
    }

    /**
     * 手动触发热度分重算（全部窗口）。
     */
    @PostMapping("/recompute-all")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> recomputeAll() {
        return ResponseEntity.ok(toApiResult(hotScoresService.recomputeAllWindowsDailyWithResult(), "ALL_WINDOWS"));
    }

    @GetMapping("/recompute-logs")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> recomputeLogs(
            @RequestParam(value = "page", required = false, defaultValue = "0") Integer page,
            @RequestParam(value = "size", required = false, defaultValue = "20") Integer size
    ) {
        int safePage = page == null ? 0 : Math.max(0, page);
        int safeSize = size == null ? 20 : Math.clamp(size, 1, 100);
        Page<HotScoresService.RecomputeLogItem> logs = hotScoresService.listRecomputeLogs(safePage, safeSize);
        return ResponseEntity.ok(Map.of(
            "content", logs.getContent(),
            "number", logs.getNumber(),
            "size", logs.getSize(),
            "totalElements", logs.getTotalElements(),
            "totalPages", logs.getTotalPages()
        ));
    }

    private Map<String, Object> toApiResult(HotScoresService.RecomputeResult result, String window) {
        HotScoresService.RecomputeResult safe = result;
        if (safe == null) {
            java.time.LocalDateTime now = java.time.LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
            safe = new HotScoresService.RecomputeResult(window, now, now, 0L, 0, 0, 0, 0, 0.0, 0.0);
        }
        String at = safe.finishedAt().atZone(ZoneId.of("Asia/Shanghai")).toString();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("window", window);
        out.put("at", at);
        out.put("startedAt", safe.startedAt());
        out.put("finishedAt", safe.finishedAt());
        out.put("durationMs", Math.max(0L, safe.durationMs()));
        out.put("changedCount", Math.max(0, safe.changedCount()));
        out.put("increasedCount", Math.max(0, safe.increasedCount()));
        out.put("decreasedCount", Math.max(0, safe.decreasedCount()));
        out.put("unchangedCount", Math.max(0, safe.unchangedCount()));
        out.put("increasedScoreDelta", Math.max(0.0, safe.increasedScoreDelta()));
        out.put("decreasedScoreDelta", Math.max(0.0, safe.decreasedScoreDelta()));
        return out;
    }
}

