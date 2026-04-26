package com.example.EnterpriseRagCommunity.controller.retrieval.admin;

import java.time.LocalDateTime;

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

import com.example.EnterpriseRagCommunity.dto.retrieval.ContextClipConfigDTO;
import com.example.EnterpriseRagCommunity.dto.retrieval.ContextClipTestRequest;
import com.example.EnterpriseRagCommunity.dto.retrieval.ContextClipTestResponse;
import com.example.EnterpriseRagCommunity.dto.retrieval.ContextWindowDetailDTO;
import com.example.EnterpriseRagCommunity.dto.retrieval.ContextWindowLogDTO;
import com.example.EnterpriseRagCommunity.service.access.DateTimeParamSupport;
import com.example.EnterpriseRagCommunity.service.retrieval.admin.ContextClipConfigService;
import com.example.EnterpriseRagCommunity.service.retrieval.admin.ContextClipTestService;
import com.example.EnterpriseRagCommunity.service.retrieval.admin.ContextWindowLogsService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/retrieval/context")
@CrossOrigin(origins = {"http://localhost:5173", "http://127.0.0.1:5173"}, allowCredentials = "true")
@RequiredArgsConstructor
public class AdminRetrievalContextController {

    private final ContextClipConfigService contextClipConfigService;
    private final ContextClipTestService contextClipTestService;
    private final ContextWindowLogsService contextWindowLogsService;

    @GetMapping("/config")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_retrieval_context','access'))")
    public ContextClipConfigDTO getConfig() {
        return contextClipConfigService.getConfig();
    }

    @PutMapping("/config")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_retrieval_context','write'))")
    public ContextClipConfigDTO updateConfig(@RequestBody @Valid ContextClipConfigDTO payload) {
        return contextClipConfigService.updateConfig(payload);
    }

    @PostMapping("/test")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_retrieval_context','write'))")
    public ContextClipTestResponse test(@RequestBody ContextClipTestRequest req) {
        return contextClipTestService.test(req);
    }

    @GetMapping("/logs/windows")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_retrieval_context','access'))")
    public Page<ContextWindowLogDTO> listWindows(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(name = "from", required = false) String from,
            @RequestParam(name = "to", required = false) String to
    ) {
        return contextWindowLogsService.listWindows(parseTimeOrNull(from), parseTimeOrNull(to), page, size);
    }

    @GetMapping("/logs/windows/{id}")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_retrieval_context','access'))")
    public ContextWindowDetailDTO getWindow(@PathVariable("id") long id) {
        return contextWindowLogsService.getWindow(id);
    }

    private static LocalDateTime parseTimeOrNull(String s) {
        return DateTimeParamSupport.parseOrNull(s);
    }
}
