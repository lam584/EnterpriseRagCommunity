package com.example.EnterpriseRagCommunity.entity.semantic;

import com.example.EnterpriseRagCommunity.entity.converter.JsonConverter;
import com.example.EnterpriseRagCommunity.entity.semantic.enums.ContextWindowPolicy;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@NoArgsConstructor
@Entity
@Table(name = "context_windows")
public class ContextWindowsEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Enumerated(EnumType.STRING)
    @Column(name = "policy", nullable = false, length = 32)
    private ContextWindowPolicy policy;

    @Column(name = "total_tokens", nullable = false)
    private Integer totalTokens;

    @Column(name = "budget_tokens")
    private Integer budgetTokens;

    @Column(name = "selected_items")
    private Integer selectedItems;

    @Column(name = "dropped_items")
    private Integer droppedItems;

    @Convert(converter = JsonConverter.class)
    @Column(name = "chunk_ids", nullable = false, columnDefinition = "json")
    private Map<String, Object> chunkIds;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
