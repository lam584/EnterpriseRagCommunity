package com.example.FinalAssignments.dto;

import com.example.FinalAssignments.entity.SystemReaderLog;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.BeanUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 系统读者日志数据传输对象
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SystemReaderLogDTO {
    private Long id;
    private String level;
    private String message;
    private String context;
    private String ip;
    private String readersAgent;
    private ReaderDTO reader; // 使用读者DTO
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * 系统读者日志DTO转换器
     */
    public static class Converter {
        /**
         * 实体转DTO
         */
        public static SystemReaderLogDTO fromEntity(SystemReaderLog entity) {
            if (entity == null) {
                return null;
            }

            SystemReaderLogDTO dto = new SystemReaderLogDTO();
            BeanUtils.copyProperties(entity, dto);

            // 手动转换读者
            if (entity.getReader() != null) {
                ReaderDTO.Converter readerConverter = new ReaderDTO.Converter();
                dto.setReader(readerConverter.convertToDTO(entity.getReader()));
            }

            return dto;
        }

        /**
         * DTO转实体
         */
        public static SystemReaderLog toEntity(SystemReaderLogDTO dto) {
            if (dto == null) {
                return null;
            }

            SystemReaderLog entity = new SystemReaderLog();
            entity.setLevel(dto.getLevel());
            entity.setMessage(dto.getMessage());
            entity.setContext(dto.getContext());
            entity.setIp(dto.getIp());
            entity.setReadersAgent(dto.getReadersAgent());
            entity.setCreatedAt(dto.getCreatedAt() != null ? dto.getCreatedAt() : LocalDateTime.now());
            entity.setUpdatedAt(dto.getUpdatedAt() != null ? dto.getUpdatedAt() : LocalDateTime.now());

            // 手动转换读者
            if (dto.getReader() != null && dto.getReader().getId() != null) {
                var reader = new com.example.FinalAssignments.entity.Reader();
                reader.setId(dto.getReader().getId());
                entity.setReader(reader);
            }

            return entity;
        }

        /**
         * 更新实体
         */
        public static void updateEntity(SystemReaderLogDTO dto, SystemReaderLog entity) {
            if (dto == null || entity == null) {
                return;
            }

            // 只更新非空字段
            if (dto.getLevel() != null) entity.setLevel(dto.getLevel());
            if (dto.getMessage() != null) entity.setMessage(dto.getMessage());
            if (dto.getContext() != null) entity.setContext(dto.getContext());
            if (dto.getIp() != null) entity.setIp(dto.getIp());
            if (dto.getReadersAgent() != null) entity.setReadersAgent(dto.getReadersAgent());

            // 通常日志不会被更新，但为了完整性也添加更新时间
            entity.setUpdatedAt(LocalDateTime.now());
        }

        /**
         * 实体列表转DTO列表
         */
        public static List<SystemReaderLogDTO> fromEntityList(List<SystemReaderLog> entityList) {
            if (entityList == null) {
                return null;
            }
            return entityList.stream()
                    .map(Converter::fromEntity)
                    .collect(Collectors.toList());
        }
    }
}
