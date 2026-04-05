package com.example.EnterpriseRagCommunity.service.ai;

final class AiChatJsonSupport {

    private AiChatJsonSupport() {
    }

    static String jsonEscape(String s) {
        return JsonEscapeSupport.escapeJson(s);
    }
}
