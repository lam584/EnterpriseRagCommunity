package com.example.EnterpriseRagCommunity.controller;

import com.example.EnterpriseRagCommunity.dto.content.BoardsDTO;
import org.springframework.web.util.HtmlUtils;

public final class BoardDtoSupport {

    private BoardDtoSupport() {
    }

    public static BoardsDTO sanitizeBoard(BoardsDTO in) {
        if (in == null) return null;
        BoardsDTO out = new BoardsDTO();
        out.setId(in.getId());
        out.setTenantId(in.getTenantId());
        out.setParentId(in.getParentId());
        out.setName(in.getName() == null ? null : HtmlUtils.htmlEscape(in.getName()));
        out.setDescription(in.getDescription() == null ? null : HtmlUtils.htmlEscape(in.getDescription()));
        out.setVisible(in.getVisible());
        out.setSortOrder(in.getSortOrder());
        out.setCreatedAt(in.getCreatedAt());
        out.setUpdatedAt(in.getUpdatedAt());
        return out;
    }
}
