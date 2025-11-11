package com.example.EnterpriseRagCommunity.entity.semantic;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@NoArgsConstructor
@Entity
@Table(name = "prompts")
public class PromptsEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "name", nullable = false, length = 96)
    private String name;

    @Lob
    @Column(name = "template", nullable = false)
    private String template;

    @Convert(converter = com.example.EnterpriseRagCommunity.entity.converter.JsonConverter.class)
    @Column(name = "variables", columnDefinition = "json")
    private Map<String, Object> variables;

    @Column(name = "version", nullable = false)
    private Integer version;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
