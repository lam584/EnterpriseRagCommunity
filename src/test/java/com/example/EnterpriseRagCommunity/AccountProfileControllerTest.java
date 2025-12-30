package com.example.EnterpriseRagCommunity;

import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.entity.access.enums.AccountStatus;
import com.example.EnterpriseRagCommunity.repository.access.UsersRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AccountProfileControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UsersRepository usersRepository;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void updateProfile_should_persist_all_known_profile_fields_into_metadata_profile() throws Exception {
        // Arrange: prepare a user that can be resolved by auth.getName() (email)
        UsersEntity u = new UsersEntity();
        u.setEmail("profile_test@example.com");
        u.setUsername("old-name");
        u.setPasswordHash("x");
        u.setStatus(AccountStatus.ACTIVE);
        u.setIsDeleted(false);
        u.setCreatedAt(LocalDateTime.now());
        u.setUpdatedAt(LocalDateTime.now());

        Map<String, Object> metadata = new LinkedHashMap<>();
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("bio", "keep-this-bio");
        metadata.put("profile", profile);
        u.setMetadata(metadata);

        u = usersRepository.save(u);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", "new-name");
        body.put("avatarUrl", "https://example.com/a.png");
        body.put("bio", ""); // should become null (emptyToNull)
        body.put("location", "成都");
        body.put("website", "https://www.bing.com/");

        // Act
        mockMvc.perform(
                        put("/api/account/profile")
                                .with(SecurityMockMvcRequestPostProcessors.user("profile_test@example.com").roles("ADMIN"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(body))
                )
                .andExpect(status().isOk());

        // Assert: reload and check persisted JSON
        UsersEntity saved = usersRepository.findByEmailAndIsDeletedFalse("profile_test@example.com").orElseThrow();
        assertThat(saved.getUsername()).isEqualTo("new-name");

        assertThat(saved.getMetadata()).isNotNull();
        Object pObj = saved.getMetadata().get("profile");
        assertThat(pObj).isInstanceOf(Map.class);

        @SuppressWarnings("unchecked")
        Map<String, Object> p = (Map<String, Object>) pObj;

        assertThat(p)
                .as("metadata.profile should contain updated fields, actual=%s", p)
                .containsEntry("avatarUrl", "https://example.com/a.png")
                .containsEntry("location", "成都")
                .containsEntry("website", "https://www.bing.com/");
        assertThat(p.get("bio"))
                .as("bio should be null after sending empty string, actual profile=%s", p)
                .isNull();
    }

    @Test
    void updateProfile_should_allow_clearing_avatarUrl_with_explicit_null() throws Exception {
        UsersEntity u = new UsersEntity();
        u.setEmail("profile_clear_avatar@example.com");
        u.setUsername("name");
        u.setPasswordHash("x");
        u.setStatus(AccountStatus.ACTIVE);
        u.setIsDeleted(false);
        u.setCreatedAt(LocalDateTime.now());
        u.setUpdatedAt(LocalDateTime.now());

        Map<String, Object> metadata = new LinkedHashMap<>();
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("avatarUrl", "https://example.com/old.png");
        metadata.put("profile", profile);
        u.setMetadata(metadata);
        usersRepository.save(u);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", "name");
        body.put("avatarUrl", null); // explicit clear

        mockMvc.perform(
                        put("/api/account/profile")
                                .with(SecurityMockMvcRequestPostProcessors.user("profile_clear_avatar@example.com").roles("ADMIN"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(body))
                )
                .andExpect(status().isOk());

        UsersEntity saved = usersRepository.findByEmailAndIsDeletedFalse("profile_clear_avatar@example.com").orElseThrow();
        @SuppressWarnings("unchecked")
        Map<String, Object> p = (Map<String, Object>) saved.getMetadata().get("profile");
        assertThat(p).containsKey("avatarUrl");
        assertThat(p.get("avatarUrl")).isNull();
    }
}
