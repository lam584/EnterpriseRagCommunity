package com.example.EnterpriseRagCommunity.controller.content;

import com.example.EnterpriseRagCommunity.dto.content.HotPostDTO;
import com.example.EnterpriseRagCommunity.service.content.HotScoresService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class HotScoresController {

    @Autowired
    private HotScoresService hotScoresService;

    /**
     * 热榜：
     * GET /api/hot?window=24h|7d|all&page=1&pageSize=20
     */
    @GetMapping("/hot")
    public Page<HotPostDTO> listHot(
            @RequestParam(name = "window", defaultValue = "24h") String window,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "pageSize", defaultValue = "20") int pageSize
    ) {
        HotScoresService.Window w = parseWindow(window);
        return hotScoresService.listHot(w, page, pageSize);
    }

    private static HotScoresService.Window parseWindow(String window) {
        if (window == null) return HotScoresService.Window.H24;
        return switch (window.trim().toLowerCase()) {
            case "24h" -> HotScoresService.Window.H24;
            case "7d" -> HotScoresService.Window.D7;
            case "all" -> HotScoresService.Window.ALL;
            default -> throw new IllegalArgumentException("window 参数不合法，应为 24h|7d|all");
        };
    }
}

