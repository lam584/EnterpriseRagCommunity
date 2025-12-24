package com.example.EnterpriseRagCommunity.service.content;

import com.example.EnterpriseRagCommunity.dto.content.PostDraftsCreateDTO;
import com.example.EnterpriseRagCommunity.dto.content.PostDraftsDTO;
import com.example.EnterpriseRagCommunity.dto.content.PostDraftsUpdateDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PostDraftsService {
    Page<PostDraftsDTO> listMine(Pageable pageable);

    PostDraftsDTO getMine(Long id);

    PostDraftsDTO create(PostDraftsCreateDTO dto);

    PostDraftsDTO updateMine(Long id, PostDraftsUpdateDTO dto);

    void deleteMine(Long id);
}

