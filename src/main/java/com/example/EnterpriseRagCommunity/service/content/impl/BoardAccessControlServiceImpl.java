package com.example.EnterpriseRagCommunity.service.content.impl;

import com.example.EnterpriseRagCommunity.dto.content.BoardAccessControlDTO;
import com.example.EnterpriseRagCommunity.entity.access.UserRoleLinksEntity;
import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.entity.content.BoardModeratorsEntity;
import com.example.EnterpriseRagCommunity.entity.content.BoardRolePermissionsEntity;
import com.example.EnterpriseRagCommunity.entity.content.enums.BoardRolePermissionType;
import com.example.EnterpriseRagCommunity.repository.access.UserRoleLinksRepository;
import com.example.EnterpriseRagCommunity.repository.access.UsersRepository;
import com.example.EnterpriseRagCommunity.repository.content.BoardModeratorsRepository;
import com.example.EnterpriseRagCommunity.repository.content.BoardRolePermissionsRepository;
import com.example.EnterpriseRagCommunity.repository.content.BoardsRepository;
import com.example.EnterpriseRagCommunity.service.AdministratorService;
import com.example.EnterpriseRagCommunity.service.access.AuditLogWriter;
import com.example.EnterpriseRagCommunity.entity.access.enums.AuditResult;
import com.example.EnterpriseRagCommunity.service.content.BoardAccessControlService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BoardAccessControlServiceImpl implements BoardAccessControlService {

    private final BoardsRepository boardsRepository;
    private final BoardRolePermissionsRepository boardRolePermissionsRepository;
    private final BoardModeratorsRepository boardModeratorsRepository;
    private final UserRoleLinksRepository userRoleLinksRepository;
    private final UsersRepository usersRepository;
    private final AdministratorService administratorService;
    private final AuditLogWriter auditLogWriter;

    @Override
    @Transactional(readOnly = true)
    public BoardAccessControlDTO getByBoardId(Long boardId) {
        if (boardId == null) throw new IllegalArgumentException("boardId 不能为空");
        if (!boardsRepository.existsById(boardId)) {
            throw new IllegalArgumentException("版块不存在: " + boardId);
        }

        BoardAccessControlDTO dto = new BoardAccessControlDTO();
        dto.setBoardId(boardId);
        dto.setViewRoleIds(safeList(boardRolePermissionsRepository.findRoleIdsByBoardIdAndPerm(boardId, BoardRolePermissionType.VIEW)));
        dto.setPostRoleIds(safeList(boardRolePermissionsRepository.findRoleIdsByBoardIdAndPerm(boardId, BoardRolePermissionType.POST)));
        dto.setModeratorUserIds(safeList(boardModeratorsRepository.findUserIdsByBoardId(boardId)));
        return dto;
    }

    @Override
    @Transactional
    public BoardAccessControlDTO replace(Long boardId, BoardAccessControlDTO dto) {
        if (boardId == null) throw new IllegalArgumentException("boardId 不能为空");
        if (!boardsRepository.existsById(boardId)) {
            throw new IllegalArgumentException("版块不存在: " + boardId);
        }

        List<Long> oldViewRoleIds = safeList(boardRolePermissionsRepository.findRoleIdsByBoardIdAndPerm(boardId, BoardRolePermissionType.VIEW));
        List<Long> oldPostRoleIds = safeList(boardRolePermissionsRepository.findRoleIdsByBoardIdAndPerm(boardId, BoardRolePermissionType.POST));
        List<Long> oldModeratorUserIds = safeList(boardModeratorsRepository.findUserIdsByBoardId(boardId));

        List<Long> viewRoleIds = normalizeIds(dto == null ? null : dto.getViewRoleIds());
        List<Long> postRoleIds = normalizeIds(dto == null ? null : dto.getPostRoleIds());
        List<Long> moderatorUserIds = normalizeIds(dto == null ? null : dto.getModeratorUserIds());

        if (!moderatorUserIds.isEmpty()) {
            List<UsersEntity> exists = usersRepository.findByIdInAndIsDeletedFalse(new LinkedHashSet<>(moderatorUserIds));
            Set<Long> found = exists.stream().map(UsersEntity::getId).collect(Collectors.toSet());
            List<Long> missing = moderatorUserIds.stream().filter(id -> !found.contains(id)).toList();
            if (!missing.isEmpty()) {
                throw new IllegalArgumentException("版主用户不存在或已删除: " + missing);
            }
        }

        boardRolePermissionsRepository.deleteByBoardId(boardId);
        boardModeratorsRepository.deleteByBoardId(boardId);

        for (Long roleId : viewRoleIds) {
            BoardRolePermissionsEntity e = new BoardRolePermissionsEntity();
            e.setBoardId(boardId);
            e.setRoleId(roleId);
            e.setPerm(BoardRolePermissionType.VIEW);
            boardRolePermissionsRepository.save(e);
        }

        for (Long roleId : postRoleIds) {
            BoardRolePermissionsEntity e = new BoardRolePermissionsEntity();
            e.setBoardId(boardId);
            e.setRoleId(roleId);
            e.setPerm(BoardRolePermissionType.POST);
            boardRolePermissionsRepository.save(e);
        }

        for (Long userId : moderatorUserIds) {
            BoardModeratorsEntity e = new BoardModeratorsEntity();
            e.setBoardId(boardId);
            e.setUserId(userId);
            boardModeratorsRepository.save(e);
        }

        BoardAccessControlDTO saved = getByBoardId(boardId);
        try {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("oldViewRoleIds", oldViewRoleIds);
            details.put("oldPostRoleIds", oldPostRoleIds);
            details.put("oldModeratorUserIds", oldModeratorUserIds);
            details.put("viewRoleIds", viewRoleIds);
            details.put("postRoleIds", postRoleIds);
            details.put("moderatorUserIds", moderatorUserIds);
            auditLogWriter.write(
                    currentUserIdOrNull(),
                    currentActorNameOrNull(),
                    "BOARD_ACCESS_CONTROL_REPLACE",
                    "BOARD",
                    boardId,
                    AuditResult.SUCCESS,
                    null,
                    null,
                    details
            );
        } catch (Exception ignore) {
        }
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public Set<Long> currentUserRoleIds() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return Set.of();
        }
        String email = auth.getName();
        Long userId = administratorService.findByUsername(email).map(UsersEntity::getId).orElse(null);
        if (userId == null) return Set.of();

        List<UserRoleLinksEntity> links = userRoleLinksRepository.findByUserId(userId);
        if (links == null || links.isEmpty()) return Set.of();

        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        for (UserRoleLinksEntity l : links) {
            if (l == null || l.getRoleId() == null || l.getRoleId() <= 0) continue;
            ids.add(l.getRoleId());
        }
        return ids;
    }

    private Long currentUserIdOrNull() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) return null;
        String email = auth.getName();
        return administratorService.findByUsername(email).map(UsersEntity::getId).orElse(null);
    }

    private String currentActorNameOrNull() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) return null;
        return auth.getName();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean canViewBoard(Long boardId, Set<Long> roleIds) {
        if (boardId == null) return false;
        List<Long> required = boardRolePermissionsRepository.findRoleIdsByBoardIdAndPerm(boardId, BoardRolePermissionType.VIEW);
        return matchesAnyRole(required, roleIds);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean canPostBoard(Long boardId, Set<Long> roleIds) {
        if (boardId == null) return false;
        List<Long> required = boardRolePermissionsRepository.findRoleIdsByBoardIdAndPerm(boardId, BoardRolePermissionType.POST);
        return matchesAnyRole(required, roleIds);
    }

    private static boolean matchesAnyRole(List<Long> required, Set<Long> roleIds) {
        if (required == null || required.isEmpty()) return true;
        if (roleIds == null || roleIds.isEmpty()) return false;
        for (Long rid : required) {
            if (rid != null && rid > 0 && roleIds.contains(rid)) return true;
        }
        return false;
    }

    private static List<Long> safeList(List<Long> ids) {
        if (ids == null) return new ArrayList<>();
        return ids.stream().filter(x -> x != null && x > 0).distinct().toList();
    }

    private static List<Long> normalizeIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        LinkedHashSet<Long> uniq = new LinkedHashSet<>();
        for (Long id : ids) {
            if (id == null) continue;
            long v = id;
            if (v <= 0) continue;
            uniq.add(v);
        }
        return new ArrayList<>(uniq);
    }
}
