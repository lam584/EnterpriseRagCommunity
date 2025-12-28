package com.example.EnterpriseRagCommunity.controller.admin;

import com.example.EnterpriseRagCommunity.service.content.HotScoresService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/hot-scores")
public class HotScoresAdminController {

    private final HotScoresService hotScoresService;

    public HotScoresAdminController(HotScoresService hotScoresService) {
        this.hotScoresService = hotScoresService;
    }

    /**
     * 手动触发热度分重算（24h 窗口）。
     */
    @PostMapping("/recompute-24h")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> recompute24h() {
        hotScoresService.recompute24hHourly();
        return ResponseEntity.ok(Map.of(
                "ok", true,
                "window", "H24",
                "at", ZonedDateTime.now(ZoneId.of("Asia/Shanghai")).toString()
        ));
    }

    /**
     * 手动触发热度分重算（7d 窗口）。
     */
    @PostMapping("/recompute-7d")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> recompute7d() {
        // 复用 daily 的实现：内部会按 window 分别计算 24h/7d/all
        // 这里单独暴露 7d 是为了便于手动校准。
        hotScoresService.recomputeAllWindowsDaily();
        return ResponseEntity.ok(Map.of(
                "ok", true,
                "window", "D7",
                "at", ZonedDateTime.now(ZoneId.of("Asia/Shanghai")).toString()
        ));
    }

    /**
     * 手动触发热度分重算（24h/7d/all 三个窗口）。
     */
    @PostMapping("/recompute-all")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> recomputeAll() {
        hotScoresService.recomputeAllWindowsDaily();
        return ResponseEntity.ok(Map.of(
                "ok", true,
                "window", "ALL_WINDOWS",
                "at", ZonedDateTime.now(ZoneId.of("Asia/Shanghai")).toString()
        ));
    }
}

