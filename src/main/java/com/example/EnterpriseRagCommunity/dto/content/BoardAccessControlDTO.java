package com.example.EnterpriseRagCommunity.dto.content;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class BoardAccessControlDTO {
    private Long boardId;
    private List<Long> viewRoleIds = new ArrayList<>();
    private List<Long> postRoleIds = new ArrayList<>();
    private List<Long> moderatorUserIds = new ArrayList<>();
}
