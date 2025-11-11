package com.example.EnterpriseRagCommunity.entity.monitor;

import com.example.EnterpriseRagCommunity.entity.converter.JsonConverter;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@Entity
@Table(name = "user_settings",
       uniqueConstraints = {
           @UniqueConstraint(name = "uk_us", columnNames = {"user_id", "k"})
       })
public class UserSettingsEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    // 外键统一使用 xxxId(Long)
    @Column(name = "user_id", nullable = false)
    private Long userId;

    // 列名 k，长度 64，不可为空
    @Column(name = "k", length = 64, nullable = false)
    private String k;

    // JSON 列 v，使用统一的 JsonConverter
    @Convert(converter = JsonConverter.class)
    @Column(name = "v", columnDefinition = "json")
    private Map<String, Object> v;
}
