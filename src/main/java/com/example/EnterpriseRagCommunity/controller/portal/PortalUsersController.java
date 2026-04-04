package com.example.EnterpriseRagCommunity.controller.portal;

import com.example.EnterpriseRagCommunity.dto.portal.PublicUserProfileDTO;
import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.repository.access.UsersRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/portal/users")
@RequiredArgsConstructor
@CrossOrigin(origins = {"http://localhost:5173", "http://127.0.0.1:5173"}, allowCredentials = "true")
public class PortalUsersController {

    private final UsersRepository usersRepository;

    @GetMapping("/{id}/profile")
    public ResponseEntity<?> getPublicProfile(@PathVariable Long id) {
        UsersEntity user = usersRepository.findByIdAndIsDeletedFalse(id).orElse(null);
        if (user == null) {
            return ResponseEntity.status(404).body(Map.of("message", "用户不存在"));
        }

        PublicUserProfileDTO dto = new PublicUserProfileDTO();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setAvatarUrl(readProfileString(user, "avatarUrl"));
        dto.setBio(readProfileString(user, "bio"));
        dto.setLocation(readProfileString(user, "location"));
        dto.setWebsite(readProfileString(user, "website"));
        return ResponseEntity.ok(dto);
    }

    private static String readProfileString(UsersEntity user, String key) {
        if (user == null) return null;
        Map<String, Object> metadata = user.getMetadata();
        if (metadata == null) return null;
        Object profileObj = metadata.get("profile");
        if (!(profileObj instanceof Map)) return null;
        @SuppressWarnings("unchecked")
        Map<String, Object> profile = (Map<String, Object>) profileObj;
        Object v = profile.get(key);
        if (v == null) return null;
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? null : s;
    }
}

