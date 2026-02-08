package com.example.EnterpriseRagCommunity.dto.portal;

import lombok.Data;

@Data
public class PublicUserProfileDTO {
    private Long id;
    private String username;
    private String avatarUrl;
    private String bio;
    private String location;
    private String website;
}

