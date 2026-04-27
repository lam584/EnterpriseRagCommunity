package com.example.EnterpriseRagCommunity.controller.moderation.admin;

import com.example.EnterpriseRagCommunity.dto.moderation.ModerationRulesCreateDTO;
import com.example.EnterpriseRagCommunity.dto.moderation.ModerationRulesUpdateDTO;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationRulesEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.RuleType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.Severity;
import com.example.EnterpriseRagCommunity.service.moderation.admin.AdminModerationRulesService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/moderation/rules")
@CrossOrigin(origins = {"http://localhost:5173", "http://127.0.0.1:5173"}, allowCredentials = "true")
public class AdminModerationRulesController {

    private final AdminModerationRulesService service;

    public AdminModerationRulesController(AdminModerationRulesService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_moderation_logs','read'))")
    public Page<ModerationRulesEntity> list(@RequestParam(value = "page", defaultValue = "1") int page,
                                           @RequestParam(value = "pageSize", defaultValue = "50") int pageSize,
                                           @RequestParam(value = "q", required = false) String q,
                                           @RequestParam(value = "type", required = false) RuleType type,
                                           @RequestParam(value = "severity", required = false) Severity severity,
                                           @RequestParam(value = "enabled", required = false) Boolean enabled,
                                           @RequestParam(value = "category", required = false) String category) {
        return service.list(page, pageSize, q, type, severity, enabled, category);
    }

    @PostMapping
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_moderation_rules','write'))")
    public ModerationRulesEntity create(@Valid @RequestBody ModerationRulesCreateDTO dto) {
        return service.create(dto);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_moderation_rules','write'))")
    public ModerationRulesEntity update(@PathVariable Long id,
                                        @RequestBody ModerationRulesUpdateDTO dto) {
        // allow frontend to omit dto.id and only use path id
        if (dto.getId() == null) dto.setId(id);
        return service.update(id, dto);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_moderation_rules','write'))")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
