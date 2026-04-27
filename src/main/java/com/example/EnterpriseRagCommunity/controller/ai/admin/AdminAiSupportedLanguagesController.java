package com.example.EnterpriseRagCommunity.controller.ai.admin;

import com.example.EnterpriseRagCommunity.dto.ai.SupportedLanguageDTO;
import com.example.EnterpriseRagCommunity.exception.ResourceNotFoundException;
import com.example.EnterpriseRagCommunity.service.ai.SupportedLanguageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/ai/supported-languages")
@CrossOrigin(origins = {"http://localhost:5173", "http://127.0.0.1:5173"}, allowCredentials = "true")
@RequiredArgsConstructor
public class AdminAiSupportedLanguagesController {

    private final SupportedLanguageService supportedLanguageService;

    @PostMapping
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_semantic_translate','action'))")
    public SupportedLanguageDTO upsert(@RequestBody SupportedLanguageDTO payload) {
        return supportedLanguageService.adminUpsert(payload);
    }

    @PutMapping("/{languageCode}")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_semantic_translate','action'))")
    public SupportedLanguageDTO update(@PathVariable String languageCode, @RequestBody SupportedLanguageDTO payload) {
        String oldCode = languageCode == null ? "" : languageCode.trim();
        String nextCode = payload == null || payload.getLanguageCode() == null ? "" : payload.getLanguageCode().trim();
        if (SupportedLanguageService.DEFAULT_LANGUAGE_CODE.equals(oldCode) && !oldCode.equals(nextCode)) {
            throw new IllegalArgumentException("不允许修改默认语言代码");
        }
        try {
            return supportedLanguageService.adminUpdate(oldCode, payload);
        } catch (IllegalArgumentException e) {
            String msg = e.getMessage();
            if (msg != null && msg.startsWith("语言不存在")) throw new ResourceNotFoundException(msg);
            throw e;
        }
    }

    @DeleteMapping("/{languageCode}")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_semantic_translate','action'))")
    public ResponseEntity<Void> delete(@PathVariable String languageCode) {
        String code = languageCode == null ? "" : languageCode.trim();
        if (SupportedLanguageService.DEFAULT_LANGUAGE_CODE.equals(code)) {
            throw new IllegalArgumentException("不允许删除默认语言");
        }
        try {
            supportedLanguageService.adminDeactivate(code);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            String msg = e.getMessage();
            if (msg != null && msg.startsWith("语言不存在")) throw new ResourceNotFoundException(msg);
            throw e;
        }
    }
}
