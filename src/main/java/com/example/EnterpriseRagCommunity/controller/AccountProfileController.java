package com.example.EnterpriseRagCommunity.controller;

import com.example.EnterpriseRagCommunity.dto.access.UpdateMyProfileRequest;
import com.example.EnterpriseRagCommunity.dto.access.UsersDTO;
import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.repository.access.UsersRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/account")
@RequiredArgsConstructor
@CrossOrigin(origins = {"http://localhost:5173", "http://127.0.0.1:5173"}, allowCredentials = "true")
public class AccountProfileController {

    private final UsersRepository usersRepository;

    @PutMapping("/profile")
    public ResponseEntity<?> updateMyProfile(@RequestBody @Valid UpdateMyProfileRequest req) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            Map<String, String> response = new HashMap<>();
            response.put("message", "未登录或会话已过期");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        // IMPORTANT: authentication name is email (see AuthController.login + SecurityConfig)
        String email = auth.getName();
        UsersEntity user = usersRepository.findByEmailAndIsDeletedFalse(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (StringUtils.hasText(req.getUsername())) {
            user.setUsername(req.getUsername().trim());
        }

        Map<String, Object> metadata0 = user.getMetadata();
        // IMPORTANT: For @Convert JSON Map fields, in-place mutations may NOT trigger dirty checking.
        // Use copy-on-write to ensure Hibernate sees the field as modified.
        Map<String, Object> metadata = (metadata0 == null) ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata0);

        Object profileObj = metadata.get("profile");
        Map<String, Object> profile0;
        if (profileObj instanceof Map) {
            //noinspection unchecked
            profile0 = (Map<String, Object>) profileObj;
        } else {
            profile0 = null;
        }
        Map<String, Object> profile = (profile0 == null) ? new LinkedHashMap<>() : new LinkedHashMap<>(profile0);

        // Only patch known keys. Don't overwrite whole metadata.
        // Semantics:
        // - missing field in JSON => leave as-is
        // - explicit null => clear (set to null)
        // - string => set (empty string treated as null)
        if (req.isAvatarUrlPresent()) profile.put("avatarUrl", emptyToNull(req.getAvatarUrl()));
        if (req.isBioPresent()) profile.put("bio", emptyToNull(req.getBio()));
        if (req.isLocationPresent()) profile.put("location", emptyToNull(req.getLocation()));
        if (req.isWebsitePresent()) profile.put("website", emptyToNull(req.getWebsite()));

        metadata.put("profile", profile);
        user.setMetadata(metadata);

        UsersEntity saved = usersRepository.save(user);
        return ResponseEntity.ok(toSafeDTO(saved));
    }

    private static String emptyToNull(String v) {
        String t = v == null ? null : v.trim();
        return (t == null || t.isEmpty()) ? null : t;
    }

    private static UsersDTO toSafeDTO(UsersEntity user) {
        UsersDTO dto = new UsersDTO();
        dto.setId(user.getId());
        if (user.getTenantId() != null) {
            dto.setTenantId(user.getTenantId().getId());
        }
        dto.setEmail(user.getEmail());
        dto.setUsername(user.getUsername());
        dto.setStatus(user.getStatus());
        dto.setIsDeleted(user.getIsDeleted());
        dto.setMetadata(user.getMetadata());
        return dto;
    }
}
