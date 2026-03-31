package com.example.EnterpriseRagCommunity.entity.monitor;

import com.example.EnterpriseRagCommunity.entity.converter.JsonConverter;
import com.example.EnterpriseRagCommunity.entity.monitor.enums.SystemEventLevel;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@NoArgsConstructor
@Entity
@Table(name = "system_events")
public class SystemEventsEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "level", nullable = false)
    private SystemEventLevel level;

    @Column(name = "category", length = 64, nullable = false)
    private String category;

    @Column(name = "message", nullable = false)
    private String message;

    @Convert(converter = JsonConverter.class)
    @Column(name = "extra", columnDefinition = "json")
    private Map<String, Object> extra;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}

// Duplicate placeholder. Use classes from package com.example.EnterpriseRagCommunity.entity.monitor.
