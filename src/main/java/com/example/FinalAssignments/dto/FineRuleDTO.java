package com.example.FinalAssignments.dto;

import com.example.FinalAssignments.entity.FineRule;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.BeanUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 罚款规则数据传输对象
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FineRuleDTO {
    private Long id;
    private String name;
    private Integer dayMin;
    private Integer dayMax;
    private BigDecimal finePerDay;
    private Boolean status;
    private AdministratorDTO admin; // 使用管理员DTO
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * 罚款规则DTO转换器
     */
    public static class Converter {
        /**
         * 实体转DTO
         */
        public static FineRuleDTO fromEntity(FineRule entity) {
            if (entity == null) {
                return null;
            }

            FineRuleDTO dto = new FineRuleDTO();
            BeanUtils.copyProperties(entity, dto);

            // 手动转换管理员
            if (entity.getAdmin() != null) {
                dto.setAdmin(AdministratorDTO.Converter.fromEntity(entity.getAdmin()));
            }

            return dto;
        }

        /**
         * DTO转实体
         */
        public static FineRule toEntity(FineRuleDTO dto) {
            if (dto == null) {
                return null;
            }

            FineRule entity = new FineRule();
            entity.setName(dto.getName());
            entity.setDayMin(dto.getDayMin());
            entity.setDayMax(dto.getDayMax());
            entity.setFinePerDay(dto.getFinePerDay());
            entity.setStatus(dto.getStatus());
            entity.setCreatedAt(dto.getCreatedAt() != null ? dto.getCreatedAt() : LocalDateTime.now());
            entity.setUpdatedAt(dto.getUpdatedAt() != null ? dto.getUpdatedAt() : LocalDateTime.now());

            // 手动转换管理员
            if (dto.getAdmin() != null && dto.getAdmin().getId() != null) {
                var admin = new com.example.FinalAssignments.entity.Administrator();
                admin.setId(dto.getAdmin().getId());
                entity.setAdmin(admin);
            }

            return entity;
        }

        /**
         * 更新实体
         */
        public static void updateEntity(FineRuleDTO dto, FineRule entity) {
            if (dto == null || entity == null) {
                return;
            }

            // 只更新非空字段
            if (dto.getName() != null) entity.setName(dto.getName());
            if (dto.getDayMin() != null) entity.setDayMin(dto.getDayMin());
            if (dto.getDayMax() != null) entity.setDayMax(dto.getDayMax());
            if (dto.getFinePerDay() != null) entity.setFinePerDay(dto.getFinePerDay());
            if (dto.getStatus() != null) entity.setStatus(dto.getStatus());

            // 总是更新updatedAt字段
            entity.setUpdatedAt(LocalDateTime.now());
        }

        /**
         * 实体列表转DTO列表
         */
        public static List<FineRuleDTO> fromEntityList(List<FineRule> entityList) {
            if (entityList == null) {
                return null;
            }
            return entityList.stream()
                    .map(Converter::fromEntity)
                    .collect(Collectors.toList());
        }
    }
}
