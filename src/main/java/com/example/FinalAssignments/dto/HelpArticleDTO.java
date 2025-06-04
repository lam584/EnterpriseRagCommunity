package com.example.FinalAssignments.dto;

import com.example.FinalAssignments.entity.Administrator;
import com.example.FinalAssignments.entity.HelpArticle;
import lombok.Data;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 帮助文章数据传输对象
 */
@Data
public class HelpArticleDTO {
    private Long id;
    private String title;
    private String contentHtml;
    private String contentText;
    private Long viewCount;
    private Long likeCount;
    private Long administratorId;
    private String administratorAccount; // 管理员账号
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * 帮助文章实体与DTO之间的转换器
     */
    @Component
    public static class Mapper {
        /**
         * 将实体对象转换为DTO
         *
         * @param helpArticle 帮助文章实体
         * @return 帮助文章DTO
         */
        public HelpArticleDTO toDTO(HelpArticle helpArticle) {
            if (helpArticle == null) {
                return null;
            }

            HelpArticleDTO dto = new HelpArticleDTO();
            dto.setId(helpArticle.getId());
            dto.setTitle(helpArticle.getTitle());
            dto.setContentHtml(helpArticle.getContentHtml());
            dto.setContentText(helpArticle.getContentText());
            dto.setViewCount(helpArticle.getViewCount());
            dto.setLikeCount(helpArticle.getLikeCount());
            dto.setCreatedAt(helpArticle.getCreatedAt());
            dto.setUpdatedAt(helpArticle.getUpdatedAt());

            // 处理关联的管理员信息
            Administrator administrator = helpArticle.getAdministrator();
            if (administrator != null) {
                dto.setAdministratorId(administrator.getId());
                dto.setAdministratorAccount(administrator.getAccount());
            }

            return dto;
        }

        /**
         * 将DTO对象转换为实体
         * 注意：此方法不会设置管理员对象，需要在Service层中处理
         *
         * @param dto 帮助文章DTO
         * @return 帮助文章实体
         */
        public HelpArticle toEntity(HelpArticleDTO dto) {
            if (dto == null) {
                return null;
            }

            HelpArticle entity = new HelpArticle();
            updateEntityFromDTO(entity, dto);
            return entity;
        }

        /**
         * 使用DTO更新实体对象
         * 注意：此方法不会更新管理员对象，需要在Service层中处理
         *
         * @param entity 待更新的实体对象
         * @param dto 包含更新数据的DTO对象
         */
        public void updateEntityFromDTO(HelpArticle entity, HelpArticleDTO dto) {
            if (entity == null || dto == null) {
                return;
            }

            // 只有在创建新对象时才设置ID
            if (entity.getId() == null && dto.getId() != null) {
                entity.setId(dto.getId());
            }

            entity.setTitle(dto.getTitle());
            entity.setContentHtml(dto.getContentHtml());
            entity.setContentText(dto.getContentText());
            entity.setViewCount(dto.getViewCount());
            entity.setLikeCount(dto.getLikeCount());

            // 创建/更新时间处理
            LocalDateTime now = LocalDateTime.now();
            if (entity.getCreatedAt() == null) {
                entity.setCreatedAt(dto.getCreatedAt() != null ? dto.getCreatedAt() : now);
            }
            entity.setUpdatedAt(dto.getUpdatedAt() != null ? dto.getUpdatedAt() : now);

            // 注意：不在这里设置关联的管理员对象，应该在Service层处理
        }

        /**
         * 将实体对象列表转换为DTO列表
         *
         * @param helpArticles 帮助文章实体列表
         * @return 帮助文章DTO列表
         */
        public List<HelpArticleDTO> toDTOList(List<HelpArticle> helpArticles) {
            if (helpArticles == null) {
                return null;
            }

            return helpArticles.stream()
                    .map(this::toDTO)
                    .collect(Collectors.toList());
        }
    }
}
