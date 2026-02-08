package com.example.EnterpriseRagCommunity.controller;

import com.example.EnterpriseRagCommunity.dto.content.BoardsCreateDTO;
import com.example.EnterpriseRagCommunity.dto.content.BoardsDTO;
import com.example.EnterpriseRagCommunity.dto.content.BoardsQueryDTO;
import com.example.EnterpriseRagCommunity.dto.content.BoardsUpdateDTO;
import com.example.EnterpriseRagCommunity.service.BoardService;
import com.example.EnterpriseRagCommunity.service.content.BoardAccessControlService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

@RestController
@RequestMapping("/api/boards")
@RequiredArgsConstructor
@Api(tags = "板块管理")
public class BoardController {

    private final BoardService boardService;
    private final BoardAccessControlService boardAccessControlService;

    @GetMapping
    @ApiOperation("查询板块列表")
    public ResponseEntity<Page<BoardsDTO>> queryBoards(@ModelAttribute BoardsQueryDTO queryDTO) {
        // 前台默认只看可见板块
        if (queryDTO.getVisible() == null) {
            queryDTO.setVisible(true);
        }
        Page<BoardsDTO> result = boardService.queryBoards(queryDTO);
        Set<Long> roleIds = boardAccessControlService.currentUserRoleIds();
        var filtered = result.getContent().stream()
                .filter(b -> b != null && b.getId() != null && boardAccessControlService.canViewBoard(b.getId(), roleIds))
                .toList();
        return ResponseEntity.ok(new PageImpl<>(filtered, result.getPageable(), filtered.size()));
    }

    @PostMapping
    @ApiOperation("创建板块")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_boards','write'))")
    public ResponseEntity<BoardsDTO> createBoard(@Valid @RequestBody BoardsCreateDTO createDTO) {
        BoardsDTO result = boardService.createBoard(createDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @PutMapping("/{id}")
    @ApiOperation("更新板块")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_boards','write'))")
    public ResponseEntity<BoardsDTO> updateBoard(@PathVariable("id") Long id, @RequestBody BoardsUpdateDTO updateDTO) {
        // Ensure ID matches path variable
        updateDTO.setId(id);
        BoardsDTO result = boardService.updateBoard(updateDTO);
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/{id}")
    @ApiOperation("删除板块")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_boards','write'))")
    public ResponseEntity<Void> deleteBoard(@PathVariable("id") Long id) {
        boardService.deleteBoard(id);
        return ResponseEntity.noContent().build();
    }
}
