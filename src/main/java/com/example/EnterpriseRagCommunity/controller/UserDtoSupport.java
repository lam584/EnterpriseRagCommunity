package com.example.EnterpriseRagCommunity.controller;

import com.example.EnterpriseRagCommunity.dto.access.UsersDTO;
import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;

final class UserDtoSupport {

    private UserDtoSupport() {
    }

    static UsersDTO toBasicDto(UsersEntity user) {
        UsersDTO dto = new UsersDTO();
        dto.setId(user.getId());
        if (user.getTenantId() != null) {
            dto.setTenantId(user.getTenantId().getId());
        }
        dto.setEmail(user.getEmail());
        dto.setUsername(user.getUsername());
        dto.setStatus(user.getStatus());
        dto.setIsDeleted(user.getIsDeleted());
        return dto;
    }
}
