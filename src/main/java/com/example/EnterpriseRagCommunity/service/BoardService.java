package com.example.EnterpriseRagCommunity.service;

import com.example.EnterpriseRagCommunity.dto.content.BoardsCreateDTO;
import com.example.EnterpriseRagCommunity.dto.content.BoardsDTO;
import com.example.EnterpriseRagCommunity.dto.content.BoardsQueryDTO;
import com.example.EnterpriseRagCommunity.dto.content.BoardsUpdateDTO;
import org.springframework.data.domain.Page;

public interface BoardService {
    /**
     * 分页查询板块
     *
     * @param queryDTO 查询条件
     * @return 板块分页列表
     */
    Page<BoardsDTO> queryBoards(BoardsQueryDTO queryDTO);

    /**
     * 创建板块
     * @param createDTO 创建信息
     * @return 创建后的板块
     */
    BoardsDTO createBoard(BoardsCreateDTO createDTO);

    /**
     * 更新板块
     * @param updateDTO 更新信息
     * @return 更新后的板块
     */
    BoardsDTO updateBoard(BoardsUpdateDTO updateDTO);

    /**
     * 删除板块
     * @param id 板块ID
     */
    void deleteBoard(Long id);
}
