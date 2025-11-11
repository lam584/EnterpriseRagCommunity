package com.example.EnterpriseRagCommunity.entity.monitor;

import com.example.EnterpriseRagCommunity.entity.converter.MapJsonConverter;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@NoArgsConstructor
@Entity
@Table(name = "metrics_events")
public class MetricsEventsEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "name", length = 96, nullable = false)
    private String name;

    @Convert(converter = MapJsonConverter.class)
    @Column(name = "tags", columnDefinition = "json")
    private Map<String, Object> tags;

    @Column(name = "value", nullable = false)
    private Double value;

    @Column(name = "ts", nullable = false)
    private LocalDateTime ts;
}

// Duplicate placeholder. Use classes from package com.example.EnterpriseRagCommunity.entity.monitor.
