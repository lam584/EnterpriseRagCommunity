package com.example.FinalAssignments.repository;

import com.example.FinalAssignments.entity.FineRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FineRuleRepository extends JpaRepository<FineRule, Long> {
    List<FineRule> findByStatus(Boolean status);
    Optional<FineRule> findByDayMinLessThanEqualAndDayMaxGreaterThanEqualAndStatus(
            Integer days, Integer days2, Boolean status);
}
