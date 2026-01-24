package com.example.EnterpriseRagCommunity.dto.access.response;

import com.example.EnterpriseRagCommunity.dto.access.UsersDTO;
import lombok.Data;

@Data
public class AuthResponse {
    private String token;
    private String tokenType = "Bearer";
    private Long expiresIn;
    private UsersDTO user;

    public AuthResponse(String token, Long expiresIn, UsersDTO user) {
        this.token = token;
        this.expiresIn = expiresIn;
        this.user = user;
    }
}