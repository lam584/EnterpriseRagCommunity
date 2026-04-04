package com.example.EnterpriseRagCommunity.controller.content;

import com.example.EnterpriseRagCommunity.dto.content.BoardAccessControlDTO;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.util.HtmlUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/boards")
@RequiredArgsConstructor
@Api(tags = "后台板块管理")
public class AdminBoardsController {

    private final BoardService boardService;
    private final BoardAccessControlService boardAccessControlService;

    private static BoardsDTO sanitizeBoard(BoardsDTO in) {
        if (in == null) return null;
        BoardsDTO out = new BoardsDTO();
        out.setId(in.getId());
        out.setTenantId(in.getTenantId());
        out.setParentId(in.getParentId());
        out.setName(in.getName() == null ? null : HtmlUtils.htmlEscape(in.getName()));
        out.setDescription(in.getDescription() == null ? null : HtmlUtils.htmlEscape(in.getDescription()));
        out.setVisible(in.getVisible());
        out.setSortOrder(in.getSortOrder());
        out.setCreatedAt(in.getCreatedAt());
        out.setUpdatedAt(in.getUpdatedAt());
        return out;
    }

    @GetMapping
    @ApiOperation("后台查询板块列表（不默认过滤 visible）")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_boards','read'))")
    public ResponseEntity<Page<BoardsDTO>> queryBoards(@ModelAttribute BoardsQueryDTO queryDTO) {
        Page<BoardsDTO> result = boardService.queryBoards(queryDTO);
        return ResponseEntity.ok(result.map(AdminBoardsController::sanitizeBoard));
    }

    @PostMapping
    @ApiOperation("后台创建板块")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_boards','write'))")
    public ResponseEntity<BoardsDTO> createBoard(@Valid @RequestBody BoardsCreateDTO createDTO) {
        BoardsDTO result = boardService.createBoard(createDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(sanitizeBoard(result));
    }

    @PutMapping("/{id}")
    @ApiOperation("后台更新板块")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_boards','write'))")
    public ResponseEntity<BoardsDTO> updateBoard(@PathVariable Long id, @RequestBody BoardsUpdateDTO updateDTO) {
        updateDTO.setId(id);
        BoardsDTO result = boardService.updateBoard(updateDTO);
        return ResponseEntity.ok(sanitizeBoard(result));
    }

    @DeleteMapping("/{id}")
    @ApiOperation("后台删除板块")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_boards','write'))")
    public ResponseEntity<Void> deleteBoard(@PathVariable Long id) {
        boardService.deleteBoard(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/access-control")
    @ApiOperation("查询板块访问/发帖角色与版主配置")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_boards','read'))")
    public ResponseEntity<BoardAccessControlDTO> getAccessControl(@PathVariable Long id) {
        return ResponseEntity.ok(boardAccessControlService.getByBoardId(id));
    }

    @PutMapping("/{id}/access-control")
    @ApiOperation("覆盖更新板块访问/发帖角色与版主配置")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_boards','write'))")
    public ResponseEntity<BoardAccessControlDTO> replaceAccessControl(@PathVariable Long id,
                                                                      @RequestBody BoardAccessControlDTO dto) {
        return ResponseEntity.ok(boardAccessControlService.replace(id, dto));
    }
}
