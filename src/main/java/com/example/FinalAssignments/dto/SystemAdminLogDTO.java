package com.example.FinalAssignments.dto;

import com.example.FinalAssignments.entity.SystemAdminLog;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.BeanUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 系统管理员日志数据传输对象
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SystemAdminLogDTO {
    private Long id;
    private String level;
    private String message;
    private String context;
    private String ip;
    private String adminUserAgent;
    private AdministratorDTO administrator; // 使用管理员DTO
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * 系统管理员日志DTO转换器
     */
    public static class Converter {
        /**
         * 实体转DTO
         */
        public static SystemAdminLogDTO fromEntity(SystemAdminLog entity) {
            if (entity == null) {
                return null;
            }

            SystemAdminLogDTO dto = new SystemAdminLogDTO();
            BeanUtils.copyProperties(entity, dto);

            // 手动转换管理员
            if (entity.getAdministrator() != null) {
                dto.setAdministrator(AdministratorDTO.Converter.fromEntity(entity.getAdministrator()));
            }

            return dto;
        }

        /**
         * DTO转实体
         */
        public static SystemAdminLog toEntity(SystemAdminLogDTO dto) {
            if (dto == null) {
                return null;
            }

            SystemAdminLog entity = new SystemAdminLog();
            entity.setLevel(dto.getLevel());
            entity.setMessage(dto.getMessage());
            entity.setContext(dto.getContext());
            entity.setIp(dto.getIp());
            entity.setAdminUserAgent(dto.getAdminUserAgent());
            entity.setCreatedAt(dto.getCreatedAt() != null ? dto.getCreatedAt() : LocalDateTime.now());
            entity.setUpdatedAt(dto.getUpdatedAt() != null ? dto.getUpdatedAt() : LocalDateTime.now());

            // 手动转换管理员
            if (dto.getAdministrator() != null && dto.getAdministrator().getId() != null) {
                var administrator = new com.example.FinalAssignments.entity.Administrator();
                administrator.setId(dto.getAdministrator().getId());
                entity.setAdministrator(administrator);
            }

            return entity;
        }

        /**
         * 更新实体
         */
        public static void updateEntity(SystemAdminLogDTO dto, SystemAdminLog entity) {
            if (dto == null || entity == null) {
                return;
            }

            // 只更新非空字段
            if (dto.getLevel() != null) entity.setLevel(dto.getLevel());
            if (dto.getMessage() != null) entity.setMessage(dto.getMessage());
            if (dto.getContext() != null) entity.setContext(dto.getContext());
            if (dto.getIp() != null) entity.setIp(dto.getIp());
            if (dto.getAdminUserAgent() != null) entity.setAdminUserAgent(dto.getAdminUserAgent());

            // 通常日志不会被更新，但为了完整性也添加更新时间
            entity.setUpdatedAt(LocalDateTime.now());
        }

        /**
         * 实体列表转DTO列表
         */
        public static List<SystemAdminLogDTO> fromEntityList(List<SystemAdminLog> entityList) {
            if (entityList == null) {
                return null;
            }
            return entityList.stream()
                    .map(Converter::fromEntity)
                    .collect(Collectors.toList());
        }
    }
}
