package com.example.EnterpriseRagCommunity.service;


import com.example.EnterpriseRagCommunity.dto.access.UserRolesCreateDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;


public interface UserRoleService {

    UserRolesCreateDTO create(UserRolesCreateDTO dto); // 对齐: DTO类型改为UserRolesCreateDTO

    UserRolesCreateDTO update(Long id, UserRolesCreateDTO dto); // 对齐: DTO类型改为UserRolesCreateDTO

    void delete(Long id);

    UserRolesCreateDTO getById(Long id); // 对齐: 返回类型改为UserRolesCreateDTO

    Page<UserRolesCreateDTO> list(Pageable pageable); // 对齐: 返回类型改为UserRolesCreateDTO

    /**
     * 查询全部角色（不分页）
     */
    List<UserRolesCreateDTO> listAll();
}
