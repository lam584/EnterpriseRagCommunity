package com.example.EnterpriseRagCommunity.service.moderation.admin;

import com.example.EnterpriseRagCommunity.dto.moderation.AdminModerationChunkLogDetailDTO;
import com.example.EnterpriseRagCommunity.dto.moderation.AdminModerationChunkLogItemDTO;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationChunkEntity;

final class AdminModerationLogDtoSupport {

    private AdminModerationLogDtoSupport() {
    }

    static void applyChunkSourceFields(AdminModerationChunkLogItemDTO dto, ModerationChunkEntity chunk) {
        dto.setSourceType(enumName(chunk.getSourceType()));
        dto.setSourceKey(chunk.getSourceKey());
        dto.setFileAssetId(chunk.getFileAssetId());
        dto.setFileName(chunk.getFileName());
        dto.setChunkIndex(chunk.getChunkIndex());
        dto.setStartOffset(chunk.getStartOffset());
        dto.setEndOffset(chunk.getEndOffset());
        dto.setStatus(enumName(chunk.getStatus()));
    }

    static void applyChunkSourceFields(AdminModerationChunkLogDetailDTO.Chunk dto, ModerationChunkEntity chunk) {
        dto.setSourceType(enumName(chunk.getSourceType()));
        dto.setSourceKey(chunk.getSourceKey());
        dto.setFileAssetId(chunk.getFileAssetId());
        dto.setFileName(chunk.getFileName());
        dto.setChunkIndex(chunk.getChunkIndex());
        dto.setStartOffset(chunk.getStartOffset());
        dto.setEndOffset(chunk.getEndOffset());
        dto.setStatus(enumName(chunk.getStatus()));
    }

    private static String enumName(Enum<?> value) {
        return value == null ? null : value.name();
    }
}
