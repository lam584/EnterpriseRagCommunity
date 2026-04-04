package com.example.EnterpriseRagCommunity.controller.content;

import com.example.EnterpriseRagCommunity.dto.content.PortalSearchHitDTO;
import com.example.EnterpriseRagCommunity.service.content.PortalSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/portal/search")
@CrossOrigin(origins = {"http://localhost:5173", "http://127.0.0.1:5173"}, allowCredentials = "true")
@RequiredArgsConstructor
public class PortalSearchController {
    private final PortalSearchService portalSearchService;

    @GetMapping
    public Page<PortalSearchHitDTO> search(@RequestParam(value = "q", required = false) String q,
                                          @RequestParam(value = "keyword", required = false) String keyword,
                                          @RequestParam(value = "boardId", required = false) Long boardId,
                                          @RequestParam(value = "page", defaultValue = "1") int page,
                                          @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
        String queryText = (q != null && !q.isBlank()) ? q : keyword;
        return portalSearchService.search(queryText, boardId, page, pageSize);
    }
}
