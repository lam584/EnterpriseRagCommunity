package com.example.FinalAssignments.controller;

import com.example.FinalAssignments.dto.AdminResponseDTO;
import com.example.FinalAssignments.entity.Administrator;
import com.example.FinalAssignments.service.AdministratorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true")
public class AuthController {
    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    @Autowired
    private AdministratorService administratorService;

    @Autowired
    private AuthenticationManager authenticationManager;

    @GetMapping("/current-admin")
    public ResponseEntity<?> getCurrentAdmin(HttpServletRequest request) {
        System.out.println("=========== getCurrentAdmin方法开始执行 ===========");
        // 记录请求信息
        System.out.println("请求IP地址: " + request.getRemoteAddr());
        System.out.println("请求方法: " + request.getMethod());
        System.out.println("请求URI: " + request.getRequestURI());

        StringBuilder headers = new StringBuilder();
        java.util.Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String name = headerNames.nextElement();
            headers.append(name).append(": ").append(request.getHeader(name)).append("; ");
        }
        System.out.println("请求头信息: " + headers.toString());

        if (request.getCookies() != null) {
            StringBuilder cookies = new StringBuilder();
            for (jakarta.servlet.http.Cookie cookie : request.getCookies()) {
                cookies.append(cookie.getName()).append("=").append(cookie.getValue()).append("; ");
            }
            System.out.println("Cookie信息: " + cookies.toString());
        } else {
            System.out.println("Cookie信息: 无");
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        System.out.println("当前身份验证状态: " + (auth != null ? auth.toString() : "null"));

        if (auth == null) {
            System.out.println("身份验证为空");
            Map<String, String> response = new HashMap<>();
            response.put("message", "未登录或会话已过期 (auth为null)");
            System.out.println("=========== getCurrentAdmin方法结束: 未授权 - auth为null ===========");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        if (!auth.isAuthenticated()) {
            System.out.println("身份验证未通过认证");
            Map<String, String> response = new HashMap<>();
            response.put("message", "未登录或会话已过期 (auth未认证)");
            System.out.println("=========== getCurrentAdmin方法结束: 未授权 - auth未认证 ===========");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        if (auth.getPrincipal().equals("anonymousUser")) {
            System.out.println("匿名用户");
            Map<String, String> response = new HashMap<>();
            response.put("message", "未登录或会话已过期 (匿名用户)");
            System.out.println("=========== getCurrentAdmin方法结束: 未授权 - 匿名用户 ===========");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        String username = auth.getName();
        System.out.println("正在查找用户名为 " + username + " 的管理员");

        Optional<Administrator> admin = administratorService.findByUsername(username);
        System.out.println("管理员查找结果: " + (admin.isPresent() ? "找到" : "未找到"));

        if (admin.isPresent()) {
            // 使用DTO替代直接返回实体
            AdminResponseDTO responseDTO = AdminResponseDTO.fromEntity(admin.get());
            System.out.println("成功获取管理员信息: ID=" + responseDTO.getId() + ", 用户名=" + responseDTO.getAccount());
            System.out.println("=========== getCurrentAdmin方法结束: 成功 ===========");
            return ResponseEntity.ok(responseDTO);
        } else {
            System.out.println("无法获取管理员信息 (用户名 " + username + ")");
            Map<String, String> response = new HashMap<>();
            response.put("message", "无法获取管理员信息");
            System.out.println("=========== getCurrentAdmin方法结束: 未找到管理员 ===========");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest loginRequest, HttpServletRequest request, HttpServletResponse servletResponse) {
        try {
            System.out.println("=========== login方法开始执行 ===========");
            System.out.println("尝试登录用户: " + loginRequest.getUsername());

            Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                    loginRequest.getUsername(),
                    loginRequest.getPassword()
                )
            );

            // 设置SecurityContext
            SecurityContextHolder.getContext().setAuthentication(authentication);

            // 确保会话创建并持久化
            request.getSession(true).setAttribute(
                "SPRING_SECURITY_CONTEXT",
                SecurityContextHolder.getContext()
            );

            // 获取会话ID
            String sessionId = request.getSession().getId();
            System.out.println("认证成功，已设置SecurityContext，当前身份: " + authentication.getName());
            System.out.println("当前会话ID: " + sessionId);

            // 明确设置JSESSIONID Cookie，确保前端能收到
            Cookie sessionCookie = new Cookie("JSESSIONID", sessionId);
            sessionCookie.setPath("/");
            sessionCookie.setHttpOnly(false);  // 允许JavaScript访问以便前端检测
            sessionCookie.setMaxAge(86400);    // 设置Cookie有效期为1天
            // 在开发环境中可以设置SameSite=None来解决跨域问题，但生产环境应谨慎使用
            // sessionCookie.setAttribute("SameSite", "Lax");
            servletResponse.addCookie(sessionCookie);

            // 记录所有设置的Cookie
            System.out.println("设置的Cookie: JSESSIONID=" + sessionId);

            Optional<Administrator> admin = administratorService.findByUsername(loginRequest.getUsername());

            if (admin.isPresent()) {
                // 使用DTO替代直接返回实体
                AdminResponseDTO responseDTO = AdminResponseDTO.fromEntity(admin.get());
                System.out.println("成功获取管理员信息: ID=" + responseDTO.getId() + ", 用户名=" + responseDTO.getAccount());
                System.out.println("=========== login方法结束: 成功 ===========");
                return ResponseEntity.ok(responseDTO);
            } else {
                Map<String, String> responseMap = new HashMap<>();
                responseMap.put("message", "登录成功但无法获取管理员信息");
                System.out.println("登录成功但无法获取管理员信息");
                System.out.println("=========== login方法结束: 成功但未找到管理员 ===========");
                return ResponseEntity.status(HttpStatus.OK).body(responseMap);
            }
        } catch (Exception e) {
            System.out.println("登录失败: " + e.getMessage());
            System.out.println("=========== login方法结束: 失败 ===========");
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("message", "用户名或密码错误");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request) {
        try {
            // 获取当前认证信息
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();

            // 检查用户是否已登录
            if (auth != null) {
                // 清除认证信息
                SecurityContextHolder.clearContext();

                // 使会话失效（如果有）
                if (request.getSession(false) != null) {
                    request.getSession().invalidate();
                }
            }

            Map<String, String> response = new HashMap<>();
            response.put("message", "退出登录成功");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, String> response = new HashMap<>();
            response.put("message", "退出登录失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @GetMapping("/csrf-token")
    public ResponseEntity<?> getCsrfToken(HttpServletRequest request) {
        CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        Map<String, String> response = new HashMap<>();

        if (csrfToken != null) {
            response.put("token", csrfToken.getToken());
            return ResponseEntity.ok(response);
        } else {
            response.put("message", "无法获取CSRF令牌");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
}

class LoginRequest {
    private String username;
    private String password;

    // Getters and setters
    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
