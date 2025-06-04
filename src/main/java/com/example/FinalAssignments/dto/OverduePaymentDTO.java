package com.example.FinalAssignments.dto;

import com.example.FinalAssignments.entity.OverduePayment;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.BeanUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 逾期付款数据传输对象
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OverduePaymentDTO {
    private Long id;
    private ReaderDTO reader; // 使用读者DTO
    private BookLoanDTO loanOrder; // 使用借阅订单DTO
    private Integer overdueDays;
    private BigDecimal amount;
    private LocalDateTime dueDate;
    private Boolean isCleared;
    private PaymentBillDTO paymentBill; // 使用支付账单DTO
    private BigDecimal paidAmount;
    private LocalDateTime repaidDate;
    private String remarks;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * 逾期付款DTO转换器
     */
    public static class Converter {
        /**
         * 实体转DTO
         */
        public static OverduePaymentDTO fromEntity(OverduePayment entity) {
            if (entity == null) {
                return null;
            }

            OverduePaymentDTO dto = new OverduePaymentDTO();
            BeanUtils.copyProperties(entity, dto);

            // 手动转换关联实体
            if (entity.getReader() != null) {
                ReaderDTO.Converter readerConverter = new ReaderDTO.Converter();
                dto.setReader(readerConverter.convertToDTO(entity.getReader()));
            }
            if (entity.getLoanOrder() != null) {
                dto.setLoanOrder(BookLoanDTO.Converter.fromEntity(entity.getLoanOrder()));
            }
            if (entity.getPaymentBill() != null) {
                dto.setPaymentBill(PaymentBillDTO.Converter.fromEntity(entity.getPaymentBill()));
            }

            return dto;
        }

        /**
         * DTO转实体
         */
        public static OverduePayment toEntity(OverduePaymentDTO dto) {
            if (dto == null) {
                return null;
            }

            OverduePayment entity = new OverduePayment();
            entity.setOverdueDays(dto.getOverdueDays());
            entity.setAmount(dto.getAmount());
            entity.setDueDate(dto.getDueDate());
            entity.setIsCleared(dto.getIsCleared());
            entity.setPaidAmount(dto.getPaidAmount());
            entity.setRepaidDate(dto.getRepaidDate());
            entity.setRemarks(dto.getRemarks());
            entity.setCreatedAt(dto.getCreatedAt() != null ? dto.getCreatedAt() : LocalDateTime.now());
            entity.setUpdatedAt(dto.getUpdatedAt() != null ? dto.getUpdatedAt() : LocalDateTime.now());

            // 手动转换关联实体（这里只设置ID，实际操作中应该从数据库获取完整实体）
            if (dto.getReader() != null && dto.getReader().getId() != null) {
                var reader = new com.example.FinalAssignments.entity.Reader();
                reader.setId(dto.getReader().getId());
                entity.setReader(reader);
            }

            if (dto.getLoanOrder() != null && dto.getLoanOrder().getId() != null) {
                var loanOrder = new com.example.FinalAssignments.entity.BookLoan();
                loanOrder.setId(dto.getLoanOrder().getId());
                entity.setLoanOrder(loanOrder);
            }

            if (dto.getPaymentBill() != null && dto.getPaymentBill().getId() != null) {
                var paymentBill = new com.example.FinalAssignments.entity.PaymentBill();
                paymentBill.setId(dto.getPaymentBill().getId());
                entity.setPaymentBill(paymentBill);
            }

            return entity;
        }

        /**
         * 更新实体
         */
        public static void updateEntity(OverduePaymentDTO dto, OverduePayment entity) {
            if (dto == null || entity == null) {
                return;
            }

            // 只更新非空字段
            if (dto.getOverdueDays() != null) entity.setOverdueDays(dto.getOverdueDays());
            if (dto.getAmount() != null) entity.setAmount(dto.getAmount());
            if (dto.getDueDate() != null) entity.setDueDate(dto.getDueDate());
            if (dto.getIsCleared() != null) entity.setIsCleared(dto.getIsCleared());
            if (dto.getPaidAmount() != null) entity.setPaidAmount(dto.getPaidAmount());
            if (dto.getRepaidDate() != null) entity.setRepaidDate(dto.getRepaidDate());
            if (dto.getRemarks() != null) entity.setRemarks(dto.getRemarks());

            // 更新关联实体
            if (dto.getPaymentBill() != null && dto.getPaymentBill().getId() != null) {
                var paymentBill = new com.example.FinalAssignments.entity.PaymentBill();
                paymentBill.setId(dto.getPaymentBill().getId());
                entity.setPaymentBill(paymentBill);
            }

            // 总是更新updatedAt字段
            entity.setUpdatedAt(LocalDateTime.now());
        }

        /**
         * 实体列表转DTO列表
         */
        public static List<OverduePaymentDTO> fromEntityList(List<OverduePayment> entityList) {
            if (entityList == null) {
                return null;
            }
            return entityList.stream()
                    .map(Converter::fromEntity)
                    .collect(Collectors.toList());
        }
    }
}
