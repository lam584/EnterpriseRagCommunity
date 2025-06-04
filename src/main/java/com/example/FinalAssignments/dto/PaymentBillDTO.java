package com.example.FinalAssignments.dto;

import com.example.FinalAssignments.entity.PaymentBill;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.BeanUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 支付账单数据传输对象
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentBillDTO {
    private Long id;
    private BigDecimal amountPaid;
    private BigDecimal totalPaid;
    private BigDecimal changeGiven;
    private LocalDateTime paymentDate;
    private AdministratorDTO admin; // 使用管理员DTO
    private ReaderDTO reader; // 使用读者DTO
    private String remarks;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * 支付账单DTO转换器
     */
    public static class Converter {
        /**
         * 实体转DTO
         */
        public static PaymentBillDTO fromEntity(PaymentBill entity) {
            if (entity == null) {
                return null;
            }

            PaymentBillDTO dto = new PaymentBillDTO();
            BeanUtils.copyProperties(entity, dto);

            // 手动转换关联实体
            if (entity.getAdmin() != null) {
                dto.setAdmin(AdministratorDTO.Converter.fromEntity(entity.getAdmin()));
            }
            if (entity.getReader() != null) {
                ReaderDTO.Converter readerConverter = new ReaderDTO.Converter();
                dto.setReader(readerConverter.convertToDTO(entity.getReader()));
            }

            return dto;
        }

        /**
         * DTO转实体
         */
        public static PaymentBill toEntity(PaymentBillDTO dto) {
            if (dto == null) {
                return null;
            }

            PaymentBill entity = new PaymentBill();
            entity.setAmountPaid(dto.getAmountPaid());
            entity.setTotalPaid(dto.getTotalPaid());
            entity.setChangeGiven(dto.getChangeGiven());
            entity.setPaymentDate(dto.getPaymentDate());
            entity.setRemarks(dto.getRemarks());
            entity.setCreatedAt(dto.getCreatedAt() != null ? dto.getCreatedAt() : LocalDateTime.now());
            entity.setUpdatedAt(dto.getUpdatedAt() != null ? dto.getUpdatedAt() : LocalDateTime.now());

            // 手动转换关联实体（这里只设置ID，实际操作中应该从数据库获取完整实体）
            if (dto.getAdmin() != null && dto.getAdmin().getId() != null) {
                var admin = new com.example.FinalAssignments.entity.Administrator();
                admin.setId(dto.getAdmin().getId());
                entity.setAdmin(admin);
            }

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
        public static void updateEntity(PaymentBillDTO dto, PaymentBill entity) {
            if (dto == null || entity == null) {
                return;
            }

            // 只更新非空字段
            if (dto.getAmountPaid() != null) entity.setAmountPaid(dto.getAmountPaid());
            if (dto.getTotalPaid() != null) entity.setTotalPaid(dto.getTotalPaid());
            if (dto.getChangeGiven() != null) entity.setChangeGiven(dto.getChangeGiven());
            if (dto.getPaymentDate() != null) entity.setPaymentDate(dto.getPaymentDate());
            if (dto.getRemarks() != null) entity.setRemarks(dto.getRemarks());

            // 总是更新updatedAt字段
            entity.setUpdatedAt(LocalDateTime.now());
        }

        /**
         * 实体列表转DTO列表
         */
        public static List<PaymentBillDTO> fromEntityList(List<PaymentBill> entityList) {
            if (entityList == null) {
                return null;
            }
            return entityList.stream()
                    .map(Converter::fromEntity)
                    .collect(Collectors.toList());
        }
    }
}
