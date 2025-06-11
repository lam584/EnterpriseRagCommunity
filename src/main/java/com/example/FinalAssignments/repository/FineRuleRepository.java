package com.example.FinalAssignments.repository;

import com.example.FinalAssignments.entity.FineRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

// 该接口用于定义罚款规则实体的数据库操作
@Repository
public interface FineRuleRepository extends JpaRepository<FineRule, Long> {
    // 根据状态查找罚款规则
    List<FineRule> findByStatus(Boolean status);

    // 根据天数范围和状态查找罚款规则
    Optional<FineRule> findByDayMinLessThanEqualAndDayMaxGreaterThanEqualAndStatus(
            Integer days, Integer days2, Boolean status);
}
