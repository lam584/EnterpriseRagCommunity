package com.example.EnterpriseRagCommunity.controller.retrieval.admin;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.EnterpriseRagCommunity.dto.retrieval.HybridRetrievalConfigDTO;
import com.example.EnterpriseRagCommunity.dto.retrieval.HybridRetrievalTestRequest;
import com.example.EnterpriseRagCommunity.dto.retrieval.RetrievalEventLogDTO;
import com.example.EnterpriseRagCommunity.dto.retrieval.RetrievalHitLogDTO;
import com.example.EnterpriseRagCommunity.service.retrieval.HybridRagRetrievalService;
import com.example.EnterpriseRagCommunity.service.retrieval.admin.HybridRetrievalConfigService;
import com.example.EnterpriseRagCommunity.service.retrieval.admin.HybridRetrievalLogsService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/retrieval/hybrid")
@CrossOrigin(origins = {"http://localhost:5173", "http://127.0.0.1:5173"}, allowCredentials = "true")
@RequiredArgsConstructor
public class AdminRetrievalHybridController {

    private final HybridRetrievalConfigService hybridRetrievalConfigService;
    private final HybridRagRetrievalService hybridRagRetrievalService;
    private final HybridRetrievalLogsService hybridRetrievalLogsService;

    @GetMapping("/config")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_retrieval_hybrid','access'))")
    public HybridRetrievalConfigDTO getConfig() {
        return hybridRetrievalConfigService.getConfig();
    }

    @PutMapping("/config")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_retrieval_hybrid','write'))")
    public HybridRetrievalConfigDTO updateConfig(@RequestBody HybridRetrievalConfigDTO payload) {
        return hybridRetrievalConfigService.updateConfig(payload);
    }

    @PostMapping("/test")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_retrieval_hybrid','write'))")
    public HybridRagRetrievalService.RetrieveResult test(@RequestBody HybridRetrievalTestRequest req) {
        String query = req == null ? null : req.getQueryText();
        Long boardId = req == null ? null : req.getBoardId();
        boolean debug = req != null && Boolean.TRUE.equals(req.getDebug());

        HybridRetrievalConfigDTO cfg;
        if (req == null || Boolean.TRUE.equals(req.getUseSavedConfig()) || req.getConfig() == null) {
            cfg = hybridRetrievalConfigService.getConfigOrDefault();
        } else {
            cfg = hybridRetrievalConfigService.normalizeConfig(req.getConfig());
        }
        return hybridRagRetrievalService.retrieve(query, boardId, cfg, debug);
    }

    @GetMapping("/logs/events")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_retrieval_hybrid','access'))")
    public Page<RetrievalEventLogDTO> listEvents(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(name = "from", required = false) String from,
            @RequestParam(name = "to", required = false) String to
    ) {
        return hybridRetrievalLogsService.listEvents(parseTimeOrNull(from), parseTimeOrNull(to), page, size);
    }

    @GetMapping("/logs/events/{eventId}/hits")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_retrieval_hybrid','access'))")
    public List<RetrievalHitLogDTO> listHits(@PathVariable("eventId") long eventId) {
        return hybridRetrievalLogsService.listHits(eventId);
    }

    private static LocalDateTime parseTimeOrNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isBlank()) return null;
        try {
            return LocalDateTime.parse(t);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }
}
