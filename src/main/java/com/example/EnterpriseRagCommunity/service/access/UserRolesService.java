package com.example.EnterpriseRagCommunity.service.access;

import com.example.EnterpriseRagCommunity.dto.access.UserRolesCreateDTO;
import com.example.EnterpriseRagCommunity.dto.access.UserRolesQueryDTO;
import com.example.EnterpriseRagCommunity.dto.access.UserRolesUpdateDTO;
import com.example.EnterpriseRagCommunity.entity.access.UserRolesEntity;
import com.example.EnterpriseRagCommunity.repository.access.UserRolesRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserRolesService {

    private final UserRolesRepository userRolesRepository;

    @Transactional
    public UserRolesEntity create(UserRolesCreateDTO dto) {
        UserRolesEntity entity = new UserRolesEntity();
        entity.setTenantId(dto.getTenantId());
        entity.setRoles(dto.getRoles());
        entity.setCanLogin(dto.getCanLogin());
        entity.setCanViewAnnouncement(dto.getCanViewAnnouncement());
        entity.setCanViewHelpArticles(dto.getCanViewHelpArticles());
        entity.setCanResetOwnPassword(dto.getCanResetOwnPassword());
        entity.setCanComment(dto.getCanComment());
        entity.setNotes(dto.getNotes());
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        return userRolesRepository.save(entity);
    }

    @Transactional
    public UserRolesEntity update(UserRolesUpdateDTO dto) {
        UserRolesEntity entity = userRolesRepository.findById(dto.getId())
                .orElseThrow(() -> new RuntimeException("Role not found"));

        if (StringUtils.hasText(dto.getRoles())) {
            entity.setRoles(dto.getRoles());
        }
        if (dto.getCanLogin() != null) entity.setCanLogin(dto.getCanLogin());
        if (dto.getCanViewAnnouncement() != null) entity.setCanViewAnnouncement(dto.getCanViewAnnouncement());
        if (dto.getCanViewHelpArticles() != null) entity.setCanViewHelpArticles(dto.getCanViewHelpArticles());
        if (dto.getCanResetOwnPassword() != null) entity.setCanResetOwnPassword(dto.getCanResetOwnPassword());
        if (dto.getCanComment() != null) entity.setCanComment(dto.getCanComment());
        if (dto.getNotes() != null) entity.setNotes(dto.getNotes());
        
        entity.setUpdatedAt(LocalDateTime.now());
        return userRolesRepository.save(entity);
    }

    @Transactional
    public void delete(Long id) {
        userRolesRepository.deleteById(id);
    }

    public Page<UserRolesEntity> query(UserRolesQueryDTO queryDTO) {
        Pageable pageable = PageRequest.of(
                queryDTO.getPageNum() - 1,
                queryDTO.getPageSize(),
                Sort.by(Sort.Direction.DESC, "createdAt")
        );

        Specification<UserRolesEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (queryDTO.getTenantId() != null) {
                predicates.add(cb.equal(root.get("tenantId"), queryDTO.getTenantId()));
            }
            if (StringUtils.hasText(queryDTO.getRoles())) {
                predicates.add(cb.like(root.get("roles"), "%" + queryDTO.getRoles() + "%"));
            }
            if (queryDTO.getCanLogin() != null) {
                predicates.add(cb.equal(root.get("canLogin"), queryDTO.getCanLogin()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return userRolesRepository.findAll(spec, pageable);
    }
    
    public UserRolesEntity getById(Long id) {
        return userRolesRepository.findById(id).orElseThrow(() -> new RuntimeException("Role not found"));
    }
    
    public List<UserRolesEntity> getAll() {
        return userRolesRepository.findAll();
    }
}
