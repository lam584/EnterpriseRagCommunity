package com.example.EnterpriseRagCommunity.controller.moderation.admin;

import com.example.EnterpriseRagCommunity.dto.moderation.ModerationRulesUpdateDTO;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationRulesEntity;
import com.example.EnterpriseRagCommunity.service.moderation.admin.AdminModerationRulesService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminModerationRulesControllerBranchTest {

    @Mock
    private AdminModerationRulesService service;

    @Test
    void update_setsDtoIdFromPath_whenDtoIdIsNull() {
        AdminModerationRulesController controller = new AdminModerationRulesController(service);
        ModerationRulesUpdateDTO dto = new ModerationRulesUpdateDTO();
        ModerationRulesEntity saved = new ModerationRulesEntity();
        saved.setId(12L);
        when(service.update(anyLong(), same(dto))).thenReturn(saved);

        ModerationRulesEntity actual = controller.update(12L, dto);

        assertEquals(12L, dto.getId());
        assertSame(saved, actual);
        verify(service).update(12L, dto);
    }

    @Test
    void update_keepsDtoId_whenDtoIdAlreadySet() {
        AdminModerationRulesController controller = new AdminModerationRulesController(service);
        ModerationRulesUpdateDTO dto = new ModerationRulesUpdateDTO();
        dto.setId(99L);
        ModerationRulesEntity saved = new ModerationRulesEntity();
        saved.setId(99L);
        when(service.update(anyLong(), same(dto))).thenReturn(saved);

        ModerationRulesEntity actual = controller.update(13L, dto);

        assertEquals(99L, dto.getId());
        assertSame(saved, actual);
        verify(service).update(13L, dto);
    }
}
