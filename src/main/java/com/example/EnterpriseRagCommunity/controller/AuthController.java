package com.example.EnterpriseRagCommunity.controller;

import java.util.HashMap;
import java.util.Map; // 替代旧UserDTO
import java.util.Optional;
import java.util.ArrayList;
import java.util.List;
import java.time.ZoneId;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired; // 替代旧User
import org.springframework.beans.factory.annotation.Value; // 替代旧Tenant
import org.springframework.http.HttpStatus; // 调整枚举包路径
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager; // 添加缺失的 AuthResponse 类导入
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken; // 添加缺失的 ApiResponse 类导入
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.csrf.CsrfToken; // 添加 TenantsRepository 导入
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.EnterpriseRagCommunity.config.AdminSetupManager;
import com.example.EnterpriseRagCommunity.dto.access.UsersCreateDTO;
import com.example.EnterpriseRagCommunity.dto.access.UsersDTO;
import com.example.EnterpriseRagCommunity.dto.access.Security2faPolicyStatusDTO;
import com.example.EnterpriseRagCommunity.dto.access.request.Login2faVerifyRequest;
import com.example.EnterpriseRagCommunity.dto.access.request.LoginRequest;
import com.example.EnterpriseRagCommunity.dto.access.request.RegisterResendRequest;
import com.example.EnterpriseRagCommunity.dto.access.request.RegisterVerifyRequest;
import com.example.EnterpriseRagCommunity.dto.access.response.ApiResponse;
import com.example.EnterpriseRagCommunity.dto.access.response.AuthResponse;
import com.example.EnterpriseRagCommunity.dto.access.response.InitialAdminRegisterResponse;
import com.example.EnterpriseRagCommunity.entity.access.PermissionsEntity;
import com.example.EnterpriseRagCommunity.entity.access.RolePermissionsEntity;
import com.example.EnterpriseRagCommunity.entity.access.TenantsEntity;
import com.example.EnterpriseRagCommunity.entity.access.UserRoleLinksEntity;
import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.entity.access.enums.AccountStatus;
import com.example.EnterpriseRagCommunity.entity.access.enums.EmailVerificationPurpose;
import com.example.EnterpriseRagCommunity.entity.content.BoardModeratorsEntity;
import com.example.EnterpriseRagCommunity.entity.content.BoardsEntity;
import com.example.EnterpriseRagCommunity.repository.access.PermissionsRepository;
import com.example.EnterpriseRagCommunity.repository.access.RolePermissionsRepository;
import com.example.EnterpriseRagCommunity.repository.access.TenantsRepository;
import com.example.EnterpriseRagCommunity.repository.access.UserRoleLinksRepository;
import com.example.EnterpriseRagCommunity.repository.content.BoardModeratorsRepository;
import com.example.EnterpriseRagCommunity.repository.content.BoardsRepository;
import com.example.EnterpriseRagCommunity.security.AccessChangedFilter;
import com.example.EnterpriseRagCommunity.service.AdministratorService;
import com.example.EnterpriseRagCommunity.service.AccountTotpService;
import com.example.EnterpriseRagCommunity.service.access.EmailVerificationService;
import com.example.EnterpriseRagCommunity.service.access.Security2faPolicyService;
import com.example.EnterpriseRagCommunity.service.init.InitialAdminIndexBootstrapService;
import com.example.EnterpriseRagCommunity.service.init.TotpMasterKeyBootstrapService;
import com.example.EnterpriseRagCommunity.service.monitor.AppSettingsService;
import com.example.EnterpriseRagCommunity.service.notify.EmailVerificationMailer;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = {"http://localhost:5173", "http://127.0.0.1:5173"}, allowCredentials = "true")
public class AuthController {
    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);
    private static final String DEFAULT_BOARD_NAME = "默认版块";
    private static final String SESSION_LOGIN2FA_PENDING_USER_ID = "login2fa.pendingUserId";
    private static final String SESSION_LOGIN2FA_PENDING_EMAIL = "login2fa.pendingEmail";
    private static final String SESSION_LOGIN2FA_PENDING_MODE = "login2fa.mode";
    private static final String SESSION_LOGIN2FA_PENDING_CREATED_AT = "login2fa.createdAtMs";

    @Autowired
    private AdministratorService administratorService;

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AdminSetupManager initialAdminSetupState;

    @Autowired
    private TenantsRepository tenantsRepository; // 注入 TenantsRepository


    @Autowired
    private UserRoleLinksRepository userRoleLinksRepository;

    @Autowired
    private PermissionsRepository permissionsRepository;

    @Autowired
    private RolePermissionsRepository rolePermissionsRepository;

    @Autowired
    private AppSettingsService appSettingsService;

    @Autowired
    private BoardsRepository boardsRepository;

    @Autowired
    private BoardModeratorsRepository boardModeratorsRepository;

    @Autowired
    private InitialAdminIndexBootstrapService initialAdminIndexBootstrapService;

    @Autowired
    private TotpMasterKeyBootstrapService totpMasterKeyBootstrapService;

    @Autowired
    private EmailVerificationService emailVerificationService;

    @Autowired
    private EmailVerificationMailer emailVerificationMailer;

    @Autowired
    private Security2faPolicyService security2faPolicyService;

    @Autowired
    private AccountTotpService accountTotpService;

    @Autowired
    private UserDetailsService userDetailsService;

    @Value("${app.tenant.default-code:DEFAULT}")
    private String defaultTenantCode;

    @Value("${app.tenant.default-name:Default Tenant}")
    private String defaultTenantName;

    @Value("${app.logging.auth-request-details:false}")
    private boolean authRequestDetailsLoggingEnabled;

    // Note: 新的UserRolesEntity直接包含tenantId，不再需要单独的Tenant实体关联
    // Note: UserRoleLinksEntity处理用户角色关联，因此移除相关Repository注入

    @GetMapping("/current-admin")
    public ResponseEntity<?> getCurrentAdmin(HttpServletRequest request) {
        logger.debug("getCurrentAdmin start ip={} method={} uri={}", request.getRemoteAddr(), request.getMethod(), request.getRequestURI());
        if (authRequestDetailsLoggingEnabled && logger.isDebugEnabled()) {
            logger.debug("getCurrentAdmin requestHeaders={}", formatRequestHeadersMasked(request));
            logger.debug("getCurrentAdmin requestCookies={}", formatRequestCookiesMasked(request));
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        logger.debug("getCurrentAdmin authSummary={}", summarizeAuthentication(auth));

        if (auth == null) {
            Map<String, String> response = new HashMap<>();
            response.put("message", "未登录或会话已过期 (auth为null)");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        if (!auth.isAuthenticated()) {
            Map<String, String> response = new HashMap<>();
            response.put("message", "未登录或会话已过期 (auth未认证)");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        if (auth.getPrincipal().equals("anonymousUser")) {
            Map<String, String> response = new HashMap<>();
            response.put("message", "未登录或会话已过期 (匿名用户)");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        String username = auth.getName();
        logger.debug("getCurrentAdmin find user by username={}", username);

        Optional<UsersEntity> user = administratorService.findByUsername(username);
        logger.debug("getCurrentAdmin userFound={}", user.isPresent());

        if (user.isPresent()) {
            UsersDTO responseDTO = convertToUserSafeDTO(user.get());
            logger.debug("getCurrentAdmin success username={} email={}", responseDTO.getUsername(), responseDTO.getEmail());
            return ResponseEntity.ok(responseDTO);
        } else {
            Map<String, String> response = new HashMap<>();
            response.put("message", "无法获取管理员信息");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }
    }

    private static String summarizeAuthentication(Authentication auth) {
        if (auth == null) {
            return "null";
        }
        String type = auth.getClass().getSimpleName();
        String name = auth.getName();
        int authorities = auth.getAuthorities() != null ? auth.getAuthorities().size() : 0;
        return "type=" + type + ", name=" + name + ", authenticated=" + auth.isAuthenticated() + ", authorities=" + authorities;
    }

    private static String formatRequestHeadersMasked(HttpServletRequest request) {
        StringBuilder sb = new StringBuilder();
        java.util.Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames != null && headerNames.hasMoreElements()) {
            String name = headerNames.nextElement();
            String value = request.getHeader(name);
            sb.append(name).append(": ").append(maskHeaderValue(name, value)).append("; ");
        }
        return sb.toString();
    }

    private static String formatRequestCookiesMasked(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null || cookies.length == 0) {
            return "none";
        }
        StringBuilder sb = new StringBuilder();
        for (Cookie cookie : cookies) {
            sb.append(cookie.getName()).append("=").append(maskCookieValue(cookie.getValue())).append("; ");
        }
        return sb.toString();
    }

    private static String maskHeaderValue(String headerName, String value) {
        if (value == null) {
            return "null";
        }
        String lower = headerName == null ? "" : headerName.toLowerCase();
        if (lower.equals("cookie")
            || lower.equals("set-cookie")
            || lower.equals("authorization")
            || lower.equals("proxy-authorization")
            || lower.equals("x-api-key")
            || lower.equals("x-xsrf-token")) {
            return "***";
        }
        if (value.length() > 512) {
            return value.substring(0, 512) + "...";
        }
        return value;
    }

    private static String maskCookieValue(String value) {
        if (value == null) {
            return "null";
        }
        if (value.length() <= 6) {
            return "***";
        }
        return value.substring(0, 3) + "***" + value.substring(value.length() - 3);
    }

    private ResponseEntity<?> completeLogin(HttpServletRequest request, HttpServletResponse servletResponse, UsersEntity currentUser, Authentication authentication) {
        if (authentication == null) throw new IllegalArgumentException("authentication is required");
        if (currentUser == null) throw new IllegalArgumentException("user is required");

        SecurityContextHolder.getContext().setAuthentication(authentication);
        HttpSession session = request.getSession(true);
        session.setAttribute("SPRING_SECURITY_CONTEXT", SecurityContextHolder.getContext());

        String sessionId = session.getId();

        Cookie sessionCookie = new Cookie("JSESSIONID", sessionId);
        sessionCookie.setPath("/");
        sessionCookie.setHttpOnly(false);
        sessionCookie.setMaxAge(86400);
        servletResponse.addCookie(sessionCookie);

        currentUser.setLastLoginAt(java.time.LocalDateTime.now());
        administratorService.save(currentUser);

        long accessTs = currentUser.getUpdatedAt() == null
                ? 0L
                : currentUser.getUpdatedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long invalidatedTs = currentUser.getSessionInvalidatedAt() == null
                ? 0L
                : currentUser.getSessionInvalidatedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        session.setAttribute(AccessChangedFilter.SESSION_ACCESS_TS_KEY, accessTs);
        if (invalidatedTs > 0L) {
            session.setAttribute(AccessChangedFilter.SESSION_INVALIDATED_TS_KEY, invalidatedTs);
        }

        UsersDTO userDTO = convertToUserSafeDTO(currentUser);
        AuthResponse authResponse = new AuthResponse(sessionId, 86400L, userDTO);
        return ResponseEntity.ok(authResponse);
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest loginRequest, HttpServletRequest request, HttpServletResponse servletResponse) {
        try {
            logger.debug("login start email={}", loginRequest.getEmail());

            Optional<UsersEntity> preUser = administratorService.findByUsername(loginRequest.getEmail());
            if (preUser.isPresent() && preUser.get().getStatus() == AccountStatus.EMAIL_UNVERIFIED) {
                UsersEntity user = preUser.get();
                if (!passwordEncoder.matches(loginRequest.getPassword(), user.getPasswordHash())) {
                    Map<String, String> errorResponse = new HashMap<>();
                    errorResponse.put("message", "邮箱或密码错误");
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
                }
                Map<String, String> errorResponse = new HashMap<>();
                errorResponse.put("code", "EMAIL_NOT_VERIFIED");
                errorResponse.put("message", "账号未完成邮箱验证，请输入邮箱验证码完成激活");
                errorResponse.put("email", user.getEmail());
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorResponse);
            }

            Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                    loginRequest.getEmail(), // 对齐: SQL users.email → DTO.email
                    loginRequest.getPassword()
                )
            );

            Optional<UsersEntity> user = administratorService.findByUsername(loginRequest.getEmail());
            if (user.isPresent()) {
                UsersEntity currentUser = user.get();
                Security2faPolicyService.Login2faMode login2faMode = security2faPolicyService.evaluateLogin2faModeForUser(currentUser.getId());
                if (login2faMode != Security2faPolicyService.Login2faMode.DISABLED) {
                    Security2faPolicyStatusDTO policy = security2faPolicyService.evaluateForUser(currentUser.getId());
                    boolean allowEmail = policy.isEmailOtpAllowed() && emailVerificationMailer.isEnabled();
                    boolean allowTotp = policy.isTotpAllowed() && accountTotpService.isEnabledByEmail(currentUser.getEmail());
                    List<String> methods = new ArrayList<>();
                    if ((login2faMode == Security2faPolicyService.Login2faMode.EMAIL_ONLY || login2faMode == Security2faPolicyService.Login2faMode.EMAIL_OR_TOTP) && allowEmail) {
                        methods.add("email");
                    }
                    if ((login2faMode == Security2faPolicyService.Login2faMode.TOTP_ONLY || login2faMode == Security2faPolicyService.Login2faMode.EMAIL_OR_TOTP) && allowTotp) {
                        methods.add("totp");
                    }

                    HttpSession session = request.getSession(true);
                    session.setAttribute(SESSION_LOGIN2FA_PENDING_USER_ID, currentUser.getId());
                    session.setAttribute(SESSION_LOGIN2FA_PENDING_EMAIL, currentUser.getEmail());
                    session.setAttribute(SESSION_LOGIN2FA_PENDING_MODE, login2faMode.name());
                    session.setAttribute(SESSION_LOGIN2FA_PENDING_CREATED_AT, System.currentTimeMillis());

                    if (methods.isEmpty()) {
                        Map<String, Object> response = new HashMap<>();
                        response.put("code", "LOGIN_2FA_UNAVAILABLE");
                        response.put("message", "当前账号无法完成登录二次验证（请联系管理员或先启用 TOTP）");
                        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
                    }

                    Map<String, Object> response = new HashMap<>();
                    response.put("code", "LOGIN_2FA_REQUIRED");
                    response.put("message", "登录需要二次验证");
                    response.put("methods", methods);
                    response.put("resendWaitSeconds", 0);
                    response.put("codeTtlSeconds", emailVerificationService.getDefaultTtlSeconds());
                    if (methods.contains("totp")) {
                        Integer digits = accountTotpService.getEnabledDigitsByEmail(currentUser.getEmail());
                        if (digits != null && (digits == 6 || digits == 8)) {
                            response.put("totpDigits", digits);
                        }
                    }
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
                }

                UsersDTO userDTO = convertToUserSafeDTO(currentUser);
                logger.debug("login authenticated user={}", authentication.getName());
                logger.debug("login success username={} email={}", userDTO.getUsername(), userDTO.getEmail());
                return completeLogin(request, servletResponse, currentUser, authentication);
            }

            Map<String, String> responseMap = new HashMap<>();
            responseMap.put("message", "登录成功但无法获取用户信息");
            logger.warn("login success but user record not found email={}", loginRequest.getEmail());
            return ResponseEntity.status(HttpStatus.OK).body(responseMap);
        } catch (Exception e) {
            logger.warn("login failed email={} msg={}", loginRequest.getEmail(), e.getMessage(), e);
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("message", "邮箱或密码错误");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
        }
    }

    @PostMapping("/login/2fa/resend-email")
    public ResponseEntity<?> resendLogin2faEmail(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "会话已过期，请重新登录"));
        }
        Object uidObj = session.getAttribute(SESSION_LOGIN2FA_PENDING_USER_ID);
        Long userId = uidObj instanceof Number ? ((Number) uidObj).longValue() : null;
        String email = (String) session.getAttribute(SESSION_LOGIN2FA_PENDING_EMAIL);
        if (userId == null || userId <= 0 || email == null || email.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "当前会话不处于登录二次验证状态"));
        }

        Security2faPolicyService.Login2faMode login2faMode = security2faPolicyService.evaluateLogin2faModeForUser(userId);
        if (login2faMode == Security2faPolicyService.Login2faMode.DISABLED) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "当前无需二次验证"));
        }
        Security2faPolicyStatusDTO policy = security2faPolicyService.evaluateForUser(userId);
        boolean allowEmail = policy.isEmailOtpAllowed() && emailVerificationMailer.isEnabled();
        boolean emailMode = login2faMode == Security2faPolicyService.Login2faMode.EMAIL_ONLY || login2faMode == Security2faPolicyService.Login2faMode.EMAIL_OR_TOTP;
        if (!emailMode || !allowEmail) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "当前不支持邮箱验证码二次验证"));
        }

        try {
            String code = emailVerificationService.issueCode(userId, EmailVerificationPurpose.LOGIN_2FA);
            emailVerificationMailer.sendVerificationCode(email, code, EmailVerificationPurpose.LOGIN_2FA);
            return ResponseEntity.ok(Map.of(
                    "message", "验证码已发送",
                    "resendWaitSeconds", emailVerificationService.getDefaultResendWaitSeconds(),
                    "codeTtlSeconds", emailVerificationService.getDefaultTtlSeconds()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", "发送失败：" + e.getMessage()));
        }
    }

    @PostMapping("/login/2fa/verify")
    public ResponseEntity<?> verifyLogin2fa(@RequestBody @Valid Login2faVerifyRequest req, HttpServletRequest request, HttpServletResponse servletResponse) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "会话已过期，请重新登录"));
        }
        Object uidObj = session.getAttribute(SESSION_LOGIN2FA_PENDING_USER_ID);
        Long userId = uidObj instanceof Number ? ((Number) uidObj).longValue() : null;
        String email = (String) session.getAttribute(SESSION_LOGIN2FA_PENDING_EMAIL);
        if (userId == null || userId <= 0 || email == null || email.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "当前会话不处于登录二次验证状态"));
        }

        Security2faPolicyService.Login2faMode login2faMode = security2faPolicyService.evaluateLogin2faModeForUser(userId);
        if (login2faMode == Security2faPolicyService.Login2faMode.DISABLED) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "当前无需二次验证"));
        }
        Security2faPolicyStatusDTO policy = security2faPolicyService.evaluateForUser(userId);

        String method = req.getMethod() == null ? "" : req.getMethod().trim().toLowerCase();
        String code = req.getCode() == null ? "" : req.getCode().trim();
        if (code.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "请输入验证码"));
        }

        boolean allowEmail = policy.isEmailOtpAllowed() && emailVerificationMailer.isEnabled();
        boolean allowTotp = policy.isTotpAllowed() && accountTotpService.isEnabledByEmail(email);
        boolean emailMode = login2faMode == Security2faPolicyService.Login2faMode.EMAIL_ONLY || login2faMode == Security2faPolicyService.Login2faMode.EMAIL_OR_TOTP;
        boolean totpMode = login2faMode == Security2faPolicyService.Login2faMode.TOTP_ONLY || login2faMode == Security2faPolicyService.Login2faMode.EMAIL_OR_TOTP;

        try {
            if ("email".equals(method)) {
                if (!emailMode || !allowEmail) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "当前不支持邮箱验证码二次验证"));
                }
                emailVerificationService.verifyAndConsume(userId, EmailVerificationPurpose.LOGIN_2FA, code);
            } else if ("totp".equals(method)) {
                if (!totpMode || !allowTotp) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "当前不支持动态验证码二次验证"));
                }
                accountTotpService.requireValidEnabledCodeByEmail(email, code);
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "验证方式不合法"));
            }

            session.removeAttribute(SESSION_LOGIN2FA_PENDING_USER_ID);
            session.removeAttribute(SESSION_LOGIN2FA_PENDING_EMAIL);
            session.removeAttribute(SESSION_LOGIN2FA_PENDING_MODE);
            session.removeAttribute(SESSION_LOGIN2FA_PENDING_CREATED_AT);

            UsersEntity currentUser = administratorService.findByUsername(email)
                    .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
            UserDetails details = userDetailsService.loadUserByUsername(email);
            Authentication authentication = new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities());
            return completeLogin(request, servletResponse, currentUser, authentication);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", "验证失败：" + e.getMessage()));
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

    /**
     * 检查系统是否需要进行初始管理员设置
     * 此端点允许匿名访问
     */
    @GetMapping("/initial-setup-status")
    public ResponseEntity<?> getInitialSetupStatus() {
        Map<String, Boolean> response = new HashMap<>();
        response.put("setupRequired", initialAdminSetupState.isSetupRequired());
        return ResponseEntity.ok(response);
    }

    /**
     * 普通用户注册
     * 此端点允许匿名访问
     */
    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody com.example.EnterpriseRagCommunity.dto.access.request.RegisterRequest request) {
        try {
            // 检查邮箱是否已存在
            if (administratorService.findByUsername(request.getEmail()).isPresent()) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ApiResponse<>(false, "邮箱已存在", null));
            }

            // 获取/创建默认租户
            TenantsEntity defaultTenant = resolveOrCreateDefaultTenantOrThrow();

            // 1) 创建用户
            UsersEntity user = new UsersEntity();
            user.setTenantId(defaultTenant);
            user.setEmail(request.getEmail());
            user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
            user.setUsername(request.getUsername());
            boolean emailEnabled = emailVerificationMailer.isEnabled();
            user.setStatus(emailEnabled ? AccountStatus.EMAIL_UNVERIFIED : AccountStatus.ACTIVE);

            UsersEntity savedUser = administratorService.save(user);

            // 2) 绑定一个最基础的 USER 角色（重构：不再使用 user_roles 表）
            Long userRoleId = appSettingsService.getLongOrDefault(AppSettingsService.KEY_DEFAULT_REGISTER_ROLE_ID, 1L);
            if (userRoleId <= 0) {
                userRoleId = 1L;
            }

            UserRoleLinksEntity link = new UserRoleLinksEntity();
            link.setUserId(savedUser.getId());
            link.setRoleId(userRoleId);
            userRoleLinksRepository.save(link);

            UsersDTO responseDTO = convertToUserSafeDTO(savedUser);
            if (emailVerificationMailer.isEnabled()) {
                String code = emailVerificationService.issueCode(savedUser.getId(), EmailVerificationPurpose.REGISTER);
                emailVerificationMailer.sendVerificationCode(savedUser.getEmail(), code, EmailVerificationPurpose.REGISTER);
                return ResponseEntity.status(HttpStatus.CREATED)
                        .body(new ApiResponse<>(true, "注册成功，请查收邮箱验证码完成激活", responseDTO));
            }
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new ApiResponse<>(true, "注册成功", responseDTO));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiResponse<>(false, "注册失败：" + e.getMessage(), null));
        }
    }

    @PostMapping("/register/verify")
    public ResponseEntity<?> verifyRegister(@Valid @RequestBody RegisterVerifyRequest req) {
        if (!emailVerificationMailer.isEnabled()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse<>(false, "邮箱验证码未启用", null));
        }
        try {
            UsersEntity user = administratorService.findByUsername(req.getEmail())
                    .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
            if (user.getStatus() != AccountStatus.EMAIL_UNVERIFIED) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new ApiResponse<>(false, "当前账号无需激活", null));
            }
            emailVerificationService.verifyAndConsume(user.getId(), EmailVerificationPurpose.REGISTER, req.getCode());
            user.setStatus(AccountStatus.ACTIVE);
            UsersEntity saved = administratorService.save(user);
            UsersDTO responseDTO = convertToUserSafeDTO(saved);
            return ResponseEntity.ok(new ApiResponse<>(true, "激活成功", responseDTO));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse<>(false, e.getMessage(), null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(false, "激活失败：" + e.getMessage(), null));
        }
    }

    @PostMapping("/register/resend-code")
    public ResponseEntity<?> resendRegisterCode(@Valid @RequestBody RegisterResendRequest req) {
        if (!emailVerificationMailer.isEnabled()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "邮箱服务未启用"));
        }
        try {
            Optional<UsersEntity> found = administratorService.findByUsername(req.getEmail());
            if (found.isEmpty() || found.get().getStatus() != AccountStatus.EMAIL_UNVERIFIED) {
                return ResponseEntity.ok(Map.of("message", "如果账号需要激活，验证码已发送"));
            }

            UsersEntity user = found.get();
            String code = emailVerificationService.issueCode(user.getId(), EmailVerificationPurpose.REGISTER);
            emailVerificationMailer.sendVerificationCode(user.getEmail(), code, EmailVerificationPurpose.REGISTER);
            return ResponseEntity.ok(Map.of(
                    "message", "如果账号需要激活，验证码已发送",
                    "resendWaitSeconds", emailVerificationService.getDefaultResendWaitSeconds(),
                    "codeTtlSeconds", emailVerificationService.getDefaultTtlSeconds()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", "发送失败：" + e.getMessage()));
        }
    }

    /**
     * 注册初始管理员账户
     * 此端点允许匿名访问，但只有在系统需要初始设置时才会执行注册
     */
    @PostMapping("/register-initial-admin")
    public ResponseEntity<?> registerInitialAdmin(@Valid @RequestBody com.example.EnterpriseRagCommunity.dto.access.request.RegisterRequest request) {
        // 检查是否需要初始设置
        if (!initialAdminSetupState.isSetupRequired()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ApiResponse<>(false, "系统已完成初始化，不能再注册初始管理员", null));
        }

        try {
            // 检查邮箱是否已存在
            if (administratorService.findByUsername(request.getEmail()).isPresent()) { // 对齐: 使用 findByUsername 方法，参数为 email
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse<>(false, "邮箱已存在", null));
            }

            logger.info("开始创建初始管理员账户...");
            TenantsEntity defaultTenant = resolveOrCreateDefaultTenantOrThrow();
            BoardsEntity defaultBoard = boardsRepository
                    .findFirstByTenantIdIsNullAndParentIdIsNullAndName(DEFAULT_BOARD_NAME)
                    .orElseThrow(() -> new IllegalStateException("默认版块不存在，无法绑定默认版主"));
            if (defaultBoard.getId() == null) {
                throw new IllegalStateException("默认版块ID为空，无法绑定默认版主");
            }

            Long adminRoleId = 2L;

            // 创建用户账户
            UsersEntity user = new UsersEntity(); // 对齐: User → UsersEntity
            user.setTenantId(defaultTenant); // 设置TenantsEntity对象
            user.setEmail(request.getEmail());
            user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
            user.setUsername(request.getUsername() != null ? request.getUsername() : "管理员"); // 修复: RegisterRequest 使用 getUsername() 而非 getDisplayName()
            user.setStatus(AccountStatus.ACTIVE);

            logger.debug("保存用户账户...");
            UsersEntity savedUser = administratorService.save(user); // 对齐: 保存新实体
            logger.info("用户账户保存成功。");
            UserRoleLinksEntity link = new UserRoleLinksEntity();
            link.setUserId(savedUser.getId());
            link.setRoleId(adminRoleId);
            userRoleLinksRepository.save(link);

            if (!boardModeratorsRepository.existsByBoardIdAndUserId(defaultBoard.getId(), savedUser.getId())) {
                BoardModeratorsEntity moderator = new BoardModeratorsEntity();
                moderator.setBoardId(defaultBoard.getId());
                moderator.setUserId(savedUser.getId());
                boardModeratorsRepository.save(moderator);
            }

            ensureCoreRbacPermissionsSeededIfEmpty();
            ensurePermissionExists("admin_ui", "access", "进入管理员后台 (/admin) 的总开关");
            var existingRolePerms = rolePermissionsRepository.findByRoleId(adminRoleId);
            java.util.Set<Long> existingPermissionIds = new java.util.HashSet<>();
            for (var rp : existingRolePerms) {
                if (rp.getPermissionId() != null) {
                    existingPermissionIds.add(rp.getPermissionId());
                }
            }

            java.util.List<PermissionsEntity> allPermissions = permissionsRepository.findAll();
            for (PermissionsEntity perm : allPermissions) {
                if (perm.getId() == null) continue;
                if (existingPermissionIds.contains(perm.getId())) continue;

                RolePermissionsEntity rp = new RolePermissionsEntity();
                rp.setRoleId(adminRoleId);
                rp.setRoleName("ADMIN");
                rp.setPermissionId(perm.getId());
                rp.setAllow(true);
                rolePermissionsRepository.save(rp);
            }

            logger.info("ADMIN 角色已授予权限数量: {}", allPermissions.size());

            try {
                initialAdminIndexBootstrapService.bootstrap(savedUser.getId());
            } catch (Exception ex) {
                logger.warn("初始化检索/RAG 与审核嵌入索引失败: {}", ex.getMessage(), ex);
            }

            logger.info("更新系统状态为已完成初始设置...");
            initialAdminSetupState.setSetupRequired(false);
            logger.info("系统状态更新完成。");
            UsersDTO responseDTO = convertToUserSafeDTO(savedUser);

            TotpMasterKeyBootstrapService.Result totpResult = totpMasterKeyBootstrapService.generateAndPersistToOsEnv();
            InitialAdminRegisterResponse resp = new InitialAdminRegisterResponse();
            resp.setUser(responseDTO);
            InitialAdminRegisterResponse.TotpMasterKeySetup setup = new InitialAdminRegisterResponse.TotpMasterKeySetup();
            setup.setEnvVarName(totpResult.getEnvVarName());
            setup.setKeyBase64(totpResult.getKeyBase64());
            setup.setAttempted(totpResult.isAttempted());
            setup.setSucceeded(totpResult.isSucceeded());
            setup.setScope(totpResult.getScope());
            setup.setCommand(totpResult.getCommand());
            setup.setFallbackCommand(totpResult.getFallbackCommand());
            setup.setMessage(totpResult.getMessage());
            setup.setError(totpResult.getError());
            setup.setRestartRequired(true);
            resp.setTotpMasterKeySetup(setup);

            logger.debug("registerInitialAdmin success response ready");
            return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>(true, "初始管理员注册成功", resp));
        } catch (Exception e) {
            logger.error("registerInitialAdmin failed: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiResponse<>(false, "注册失败：" + e.getMessage(), null));
        }
    }

    private TenantsEntity resolveOrCreateDefaultTenantOrThrow() {
        // 1) 优先按 code 查找（稳定，不依赖自增ID）
        Optional<TenantsEntity> tenantOpt = Optional.empty();
        if (defaultTenantCode != null && !defaultTenantCode.trim().isEmpty()) {
            tenantOpt = tenantsRepository.findByCode(defaultTenantCode.trim());
        }

        // 2) 退化：取第一条 tenant
        if (!tenantOpt.isPresent()) {
            tenantOpt = tenantsRepository.findFirstByOrderByIdAsc();
        }

        // 3) 仍然没有：创建一个默认 tenant
        if (!tenantOpt.isPresent()) {
            TenantsEntity tenant = new TenantsEntity();
            tenant.setCode((defaultTenantCode != null && !defaultTenantCode.trim().isEmpty()) ? defaultTenantCode.trim() : "DEFAULT");
            tenant.setName((defaultTenantName != null && !defaultTenantName.trim().isEmpty()) ? defaultTenantName.trim() : "Default Tenant");
            tenant.setCreatedAt(java.time.LocalDateTime.now());
            tenant.setUpdatedAt(java.time.LocalDateTime.now());
            return tenantsRepository.save(tenant);
        }

        return tenantOpt.get();
    }

    /**
     * 将 UsersEntity 实体转换为 UsersCreateDTO
     */
    private UsersCreateDTO convertToUserDTO(UsersEntity user) { // 对齐: 参数和返回类型改为新类
        UsersCreateDTO dto = new UsersCreateDTO();
        // 注意: UsersCreateDTO 没有 setId, setCreatedAt, setUpdatedAt 方法，这些字段在 DTO 中不存在
        // 只设置 UsersCreateDTO 中实际存在的字段
        if (user.getTenantId() != null) {
            dto.setTenantId(user.getTenantId().getId()); // 获取 TenantsEntity 的 ID 作为 tenantId
        }
        dto.setEmail(user.getEmail());
        dto.setUsername(user.getUsername()); // 对齐: displayName → username
        dto.setPasswordHash(user.getPasswordHash()); // 对齐: 添加passwordHash
        dto.setStatus(user.getStatus());
        // Note: 新的UsersCreateDTO没有avatarUrl字段，根据需求决定是否保留或映射到metadata
        // UsersCreateDTO 没有 setLastLoginAt, setCreatedAt, setUpdatedAt 方法，跳过
        dto.setIsDeleted(user.getIsDeleted()); // 对齐: 添加isDeleted
        // Note: metadata字段未在旧代码中设置，此处留空或根据需求填充
        // 转换角色信息 - 简化处理，假设通过其他服务获取
        // 由于角色关联已重构，此处省略角色转换逻辑
        // 如果需要，应调用UserRoleService获取用户角色
        return dto;
    }

    /**
     * Safe mapping for returning user to frontend (includes id, excludes passwordHash).
     */
    private UsersDTO convertToUserSafeDTO(UsersEntity user) {
        UsersDTO dto = new UsersDTO();
        dto.setId(user.getId());
        if (user.getTenantId() != null) {
            dto.setTenantId(user.getTenantId().getId());
        }
        dto.setEmail(user.getEmail());
        dto.setUsername(user.getUsername());
        dto.setStatus(user.getStatus());
        dto.setIsDeleted(user.getIsDeleted());
        dto.setMetadata(user.getMetadata());
        return dto;
    }

    /**
     * 如果 permissions 表为空，则补种一批核心权限（与 V6__rbac_seed_permissions.sql 对齐）。
     * 说明：Flyway 正常情况下会执行 V6，但在某些环境（手动导库/旧库 baseline）可能未执行。
     */
    private void ensureCoreRbacPermissionsSeededIfEmpty() {
        long cnt = permissionsRepository.count();
        if (cnt > 0) return;

        logger.info("permissions 表为空，开始补种核心 RBAC 权限...");

        // 1) Admin UI access + sidebar sections
        ensurePermissionExists("admin_ui", "access", "进入管理员后台 (/admin) 的总开关");
        ensurePermissionExists("admin_content", "access", "访问后台-内容管理模块");
        ensurePermissionExists("admin_review", "access", "访问后台-审核中心模块");
        ensurePermissionExists("admin_semantic", "access", "访问后台-语义增强模块");
        ensurePermissionExists("admin_retrieval", "access", "访问后台-检索与RAG模块");
        ensurePermissionExists("admin_metrics", "access", "访问后台-评估与监控模块");
        ensurePermissionExists("admin_users", "access", "访问后台-用户与权限模块");

        // 1.1) Admin sub-pages/forms (fine-grained UI gating, optional)
        ensurePermissionExists("admin_content_board", "access", "后台-内容管理-版块管理");
        ensurePermissionExists("admin_content_post", "access", "后台-内容管理-帖子管理");
        ensurePermissionExists("admin_content_comment", "access", "后台-内容管理-评论管理");
        ensurePermissionExists("admin_content_tags", "access", "后台-内容管理-标签体系管理");
        ensurePermissionExists("admin_review_queue", "access", "后台-审核中心-审核队列面板");
        ensurePermissionExists("admin_review_rules", "access", "后台-审核中心-规则过滤层");
        ensurePermissionExists("admin_review_embed", "access", "后台-审核中心-嵌入相似检测");
        ensurePermissionExists("admin_review_llm", "access", "后台-审核中心-LLM 审核层");
        ensurePermissionExists("admin_review_fallback", "access", "后台-审核中心-置信回退机制");
        ensurePermissionExists("admin_review_logs", "access", "后台-审核中心-审核日志与追溯");
        ensurePermissionExists("admin_review_risk_tags", "access", "后台-审核中心-风险标签生成");
        ensurePermissionExists("admin_semantic_title_gen", "access", "后台-语义增强-标题生成");
        ensurePermissionExists("admin_semantic_multi_label", "access", "后台-语义增强-多任务标签生成");
        ensurePermissionExists("admin_semantic_summary", "access", "后台-语义增强-帖子摘要");
        ensurePermissionExists("admin_semantic_translate", "access", "后台-语义增强-翻译");
        ensurePermissionExists("admin_retrieval_index", "access", "后台-检索与RAG-向量索引构建");
        ensurePermissionExists("admin_retrieval_hybrid", "access", "后台-检索与RAG-Hybrid 检索配置");
        ensurePermissionExists("admin_retrieval_context", "access", "后台-检索与RAG-动态上下文裁剪");
        ensurePermissionExists("admin_retrieval_citation", "access", "后台-检索与RAG-引用与来源展示配置");
        ensurePermissionExists("admin_metrics_metrics", "access", "后台-评估与监控-指标采集层");
        ensurePermissionExists("admin_metrics_abtest", "access", "后台-评估与监控-实验对比脚本");
        ensurePermissionExists("admin_metrics_token", "access", "后台-评估与监控-Token 成本统计");
        ensurePermissionExists("admin_metrics_label_quality", "access", "后台-评估与监控-标签质量评估工具");
        ensurePermissionExists("admin_metrics_cost", "access", "后台-评估与监控-审核成本分析");
        ensurePermissionExists("admin_users_user_role", "access", "后台-用户与权限-用户管理");
        ensurePermissionExists("admin_users_roles", "access", "后台-用户与权限-角色管理");
        ensurePermissionExists("admin_users_matrix", "access", "后台-用户与权限-权限管理");
        ensurePermissionExists("admin_users_2fa", "access", "后台-用户与权限-高权限操作 2FA 策略");

        // 2) Admin APIs used by current pages
        ensurePermissionExists("admin_posts", "read", "后台帖子查询");
        ensurePermissionExists("admin_posts", "update", "后台帖子更新/状态调整");
        ensurePermissionExists("admin_posts", "delete", "后台帖子删除/软删");
        ensurePermissionExists("admin_comments", "read", "后台评论查询");
        ensurePermissionExists("admin_comments", "update", "后台评论状态/删除标记更新");
        ensurePermissionExists("admin_comments", "delete", "后台评论删除/软删");
        ensurePermissionExists("admin_boards", "read", "后台板块查询");
        ensurePermissionExists("admin_boards", "write", "后台板块创建/更新/删除");
        ensurePermissionExists("admin_tags", "read", "后台标签查询");
        ensurePermissionExists("admin_tags", "write", "后台标签创建/更新/删除");
        ensurePermissionExists("admin_moderation_queue", "read", "审核队列查询");
        ensurePermissionExists("admin_moderation_queue", "action", "审核队列处理(approve/reject/backfill)");
        ensurePermissionExists("admin_moderation_rules", "read", "审核规则查询");
        ensurePermissionExists("admin_moderation_rules", "write", "审核规则创建/更新/删除");
        ensurePermissionExists("admin_moderation_embed", "read", "嵌入相似检测配置/查询");
        ensurePermissionExists("admin_moderation_embed", "write", "嵌入相似检测配置更新");
        ensurePermissionExists("admin_moderation_llm", "read", "LLM 审核层配置/查询");
        ensurePermissionExists("admin_moderation_llm", "write", "LLM 审核层配置更新");
        ensurePermissionExists("admin_moderation_logs", "read", "审核日志查询");
        ensurePermissionExists("admin_risk_tags", "read", "风险标签查询");
        ensurePermissionExists("admin_risk_tags", "write", "风险标签生成/配置更新");
        ensurePermissionExists("admin_semantic_title_gen", "action", "标题生成任务(触发/预览/保存)");
        ensurePermissionExists("admin_semantic_multi_label", "action", "多任务标签生成任务(触发/预览/保存)");
        ensurePermissionExists("admin_semantic_summary", "action", "帖子摘要生成任务(触发/预览/保存)");
        ensurePermissionExists("admin_semantic_translate", "action", "翻译任务(触发/预览/保存)");
        ensurePermissionExists("admin_retrieval_index", "action", "向量索引构建/重建");
        ensurePermissionExists("admin_retrieval_hybrid", "write", "Hybrid 检索配置更新");
        ensurePermissionExists("admin_retrieval_context", "write", "动态上下文裁剪配置更新");
        ensurePermissionExists("admin_retrieval_citation", "write", "引用与来源展示配置更新");
        ensurePermissionExists("admin_metrics_metrics", "read", "指标采集层读取");
        ensurePermissionExists("admin_metrics_abtest", "action", "实验对比脚本执行");
        ensurePermissionExists("admin_metrics_token", "read", "Token 成本统计读取");
        ensurePermissionExists("admin_metrics_label_quality", "read", "标签质量评估读取");
        ensurePermissionExists("admin_metrics_cost", "read", "审核成本分析读取");
        ensurePermissionExists("admin_users", "read", "后台用户/权限数据读取(通用)");
        ensurePermissionExists("admin_users", "write", "后台用户/权限数据写入(通用)");
        ensurePermissionExists("admin_permissions", "read", "权限列表查询");
        ensurePermissionExists("admin_permissions", "write", "权限创建/更新/删除");
        ensurePermissionExists("admin_role_permissions", "read", "角色-权限矩阵读取");
        ensurePermissionExists("admin_role_permissions", "write", "角色-权限矩阵写入/更新/删除");
        ensurePermissionExists("admin_user_roles", "read", "用户-角色关联读取");
        ensurePermissionExists("admin_user_roles", "write", "用户-角色关联写入/更新/清空");
        ensurePermissionExists("admin_hot_scores", "action", "热度分重算/运维动作");

        // 3) Portal page-level permissions (VIEW)
        ensurePermissionExists("portal_discover", "view", "前台-发现页(整体)");
        ensurePermissionExists("portal_discover_home", "view", "前台-发现-推荐/首页");
        ensurePermissionExists("portal_discover_boards", "view", "前台-发现-版块");
        ensurePermissionExists("portal_discover_tags", "view", "前台-发现-标签");
        ensurePermissionExists("portal_discover_hot", "view", "前台-发现-热榜");
        ensurePermissionExists("portal_search", "view", "前台-搜索(整体)");
        ensurePermissionExists("portal_search_posts", "view", "前台-搜索-帖子");
        ensurePermissionExists("portal_posts", "view", "前台-帖子模块(整体)");
        ensurePermissionExists("portal_posts_detail", "view", "前台-帖子详情页");
        ensurePermissionExists("portal_posts_mine", "view", "前台-我的帖子");
        ensurePermissionExists("portal_posts_drafts", "view", "前台-草稿箱");
        ensurePermissionExists("portal_posts_bookmarks", "view", "前台-收藏列表");
        ensurePermissionExists("portal_interact", "view", "前台-互动中心(整体)");
        ensurePermissionExists("portal_interact_replies", "view", "前台-互动-回复");
        ensurePermissionExists("portal_interact_likes", "view", "前台-互动-点赞");
        ensurePermissionExists("portal_interact_mentions", "view", "前台-互动-提及");
        ensurePermissionExists("portal_interact_reports", "view", "前台-互动-举报");
        ensurePermissionExists("portal_assistant", "view", "前台-助手(整体)");
        ensurePermissionExists("portal_assistant_chat", "view", "前台-助手-聊天");
        ensurePermissionExists("portal_assistant_history", "view", "前台-助手-历史");
        ensurePermissionExists("portal_assistant_collections", "view", "前台-助手-收藏/知识库");
        ensurePermissionExists("portal_assistant_settings", "view", "前台-助手-设置");
        ensurePermissionExists("portal_account", "view", "前台-账号中心(整体)");
        ensurePermissionExists("portal_account_profile", "view", "前台-账号-资料");
        ensurePermissionExists("portal_account_security", "view", "前台-账号-安全");
        ensurePermissionExists("portal_account_preferences", "view", "前台-账号-偏好");
        ensurePermissionExists("portal_account_connections", "view", "前台-账号-绑定与连接");

        // 4) Portal feature permissions (WRITE/ACTION)
        ensurePermissionExists("portal_posts", "create", "前台发帖/保存草稿/编辑帖子");
        ensurePermissionExists("portal_posts", "update", "前台编辑帖子");
        ensurePermissionExists("portal_posts", "delete", "前台删除帖子(若支持)");
        ensurePermissionExists("portal_comments", "create", "前台发表评论/回复");
        ensurePermissionExists("portal_comments", "delete", "前台删除自己的评论(若支持)");
        ensurePermissionExists("portal_reactions", "like", "前台点赞");
        ensurePermissionExists("portal_reactions", "unlike", "前台取消点赞");
        ensurePermissionExists("portal_favorites", "bookmark", "前台收藏");
        ensurePermissionExists("portal_favorites", "unbookmark", "前台取消收藏");
        ensurePermissionExists("portal_reports", "create", "前台举报内容");

        logger.info("核心 RBAC 权限补种完成。");
    }

    /**
     * 幂等确保某个 permission(resource, action) 存在。
     */
    private void ensurePermissionExists(String resource, String action, String description) {
        if (resource == null || resource.isBlank() || action == null || action.isBlank()) return;

        // 走唯一键查询，避免全表扫描，并确保“缺一条也能补齐”（不依赖 permissions 表为空）。
        if (permissionsRepository.findByResourceAndAction(resource, action).isPresent()) {
            return;
        }

        PermissionsEntity entity = new PermissionsEntity();
        entity.setResource(resource);
        entity.setAction(action);
        entity.setDescription(description);
        permissionsRepository.save(entity);
    }
}
