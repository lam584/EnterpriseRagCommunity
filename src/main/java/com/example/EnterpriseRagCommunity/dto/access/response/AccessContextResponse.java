package com.example.EnterpriseRagCommunity.dto.access.response;

import java.util.List;

/**
 * Returned to frontend so it can decide what menus/buttons to show.
 */
public record AccessContextResponse(
        String email,
        List<String> roles,
        List<String> permissions
) {
}

