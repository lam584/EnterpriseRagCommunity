package com.example.EnterpriseRagCommunity.service.content;

import com.example.EnterpriseRagCommunity.dto.content.BoardAccessControlDTO;

import java.util.Set;

public interface BoardAccessControlService {
    BoardAccessControlDTO getByBoardId(Long boardId);

    BoardAccessControlDTO replace(Long boardId, BoardAccessControlDTO dto);

    Set<Long> currentUserRoleIds();

    boolean canViewBoard(Long boardId, Set<Long> roleIds);

    boolean canPostBoard(Long boardId, Set<Long> roleIds);
}
