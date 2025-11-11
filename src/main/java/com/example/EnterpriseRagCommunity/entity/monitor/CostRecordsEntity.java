package com.example.EnterpriseRagCommunity.entity.monitor;

import com.example.EnterpriseRagCommunity.entity.monitor.enums.CostScope;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@Entity
@Table(name = "cost_records")
public class CostRecordsEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    // ENUM('GEN','RERANK','MODERATION','OTHER') NOT NULL
    @Enumerated(EnumType.STRING)
    @Column(name = "scope", nullable = false, length = 16)
    private CostScope scope;

    // VARCHAR(64) NULL
    @Column(name = "model", length = 64)
    private String model;

    // tokens_in INT NULL
    @Column(name = "tokens_in")
    private Integer tokensIn;

    // tokens_out INT NULL
    @Column(name = "tokens_out")
    private Integer tokensOut;

    // currency VARCHAR(8) NULL
    @Column(name = "currency", length = 8)
    private String currency;

    // unit_price_in DECIMAL(10,6) NULL
    @Column(name = "unit_price_in", precision = 10, scale = 6)
    private BigDecimal unitPriceIn;

    // unit_price_out DECIMAL(10,6) NULL
    @Column(name = "unit_price_out", precision = 10, scale = 6)
    private BigDecimal unitPriceOut;

    // total_cost DECIMAL(10,4) NULL
    @Column(name = "total_cost", precision = 10, scale = 4)
    private BigDecimal totalCost;

    // ref_type VARCHAR(64) NULL
    @Column(name = "ref_type", length = 64)
    private String refType;

    // ref_id BIGINT NULL
    @Column(name = "ref_id")
    private Long refId;

    // ts DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
    @Column(name = "ts", nullable = false)
    private LocalDateTime ts;
}
