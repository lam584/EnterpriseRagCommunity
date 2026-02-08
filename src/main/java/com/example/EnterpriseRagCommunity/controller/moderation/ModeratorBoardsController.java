package com.example.EnterpriseRagCommunity.controller.moderation;

import com.example.EnterpriseRagCommunity.dto.content.BoardsDTO;
import com.example.EnterpriseRagCommunity.entity.content.BoardsEntity;
import com.example.EnterpriseRagCommunity.repository.content.BoardModeratorsRepository;
import com.example.EnterpriseRagCommunity.repository.content.BoardsRepository;
import com.example.EnterpriseRagCommunity.service.AdministratorService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/moderator/boards")
@RequiredArgsConstructor
public class ModeratorBoardsController {

    private final BoardModeratorsRepository boardModeratorsRepository;
    private final BoardsRepository boardsRepository;
    private final AdministratorService administratorService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<BoardsDTO> listMyBoards() {
        Long me = currentUserIdOrThrow();
        List<Long> boardIds = boardModeratorsRepository.findBoardIdsByUserId(me);
        if (boardIds == null || boardIds.isEmpty()) return List.of();

        List<Long> uniq = new ArrayList<>(new LinkedHashSet<>(boardIds));
        Map<Long, BoardsEntity> byId = boardsRepository.findAllById(uniq).stream()
                .filter(e -> e != null && e.getId() != null)
                .collect(Collectors.toMap(BoardsEntity::getId, Function.identity(), (a, b) -> a));

        List<BoardsDTO> result = new ArrayList<>();
        for (Long id : uniq) {
            BoardsEntity e = byId.get(id);
            if (e == null) continue;
            BoardsDTO dto = new BoardsDTO();
            BeanUtils.copyProperties(e, dto);
            result.add(dto);
        }
        return result;
    }

    private Long currentUserIdOrThrow() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new org.springframework.security.access.AccessDeniedException("未登录或会话已过期");
        }
        String email = auth.getName();
        return administratorService.findByUsername(email)
                .orElseThrow(() -> new org.springframework.security.access.AccessDeniedException("当前用户不存在"))
                .getId();
    }
}

