package com.example.EnterpriseRagCommunity.security;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import jakarta.annotation.Resource;

@SpringBootTest
@AutoConfigureMockMvc
class RegisterResendCodeSecurityTest {

    @Resource
    private MockMvc mockMvc;

    @Test
    void resendRegisterCode_shouldNotReturn401Or403_whenAnonymous() throws Exception {
        var result = mockMvc.perform(post("/api/auth/register/resend-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@example.com\"}"))
                .andReturn();

        int status = result.getResponse().getStatus();
        assertNotEquals(401, status);
        assertNotEquals(403, status);
    }
}

