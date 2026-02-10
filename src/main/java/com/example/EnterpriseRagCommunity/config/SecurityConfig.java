// 文件路径： java/com/example/hellospringboot/config/SecurityConfig.java
package com.example.EnterpriseRagCommunity.config;


import java.util.Arrays; // 替代旧User
import java.util.Optional; // 新增导入

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.entity.access.enums.AccountStatus;
import com.example.EnterpriseRagCommunity.security.AccessChangedFilter;
import com.example.EnterpriseRagCommunity.service.AdministratorService;
import com.example.EnterpriseRagCommunity.service.access.AccessControlService;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    private static final Logger logger = LoggerFactory.getLogger(SecurityConfig.class);

    // 改为注入Service
    @Autowired
    private AdministratorService administratorService;

    @Autowired
    private AccessControlService accessControlService;

    @Autowired
    private AccessChangedFilter accessChangedFilter;

    @Bean
    @Order(1) // API 链优先级更高，只拦截 /api/**
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        logger.debug("配置统一的安全过滤链...");

        http
                .securityMatcher("/api/**") // 仅匹配 API 请求，避免与 Web 链重叠
                // Make RBAC changes effective immediately for session-based auth.
                // Must run after SecurityContext is loaded from session.
                .addFilterAfter(accessChangedFilter, SecurityContextHolderFilter.class)
                .cors(cors -> {
                    logger.debug("配置 CORS...");
                    cors.configurationSource(corsConfigurationSource());
                })
                .csrf(csrf -> {
                    logger.debug("配置 CSRF...");
                    // 使用 CsrfTokenRequestAttributeHandler 替代 XorCsrfTokenRequestAttributeHandler
                    // 以避免前端获取的原始 Token 与后端期望的 XOR 掩码 Token 不一致导致的 403 问题
                    CsrfTokenRequestAttributeHandler requestHandler = new CsrfTokenRequestAttributeHandler();
                    // 显式设置属性名，确保兼容性
                    requestHandler.setCsrfRequestAttributeName("_csrf");
                    
                    csrf
                        // 使用 Cookie 存储并暴露到前端
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(requestHandler)
                        // 忽略初始化与认证相关端点的 CSRF 校验，避免初始化阶段 403
                        .ignoringRequestMatchers(
                                "/api/auth/register-initial-admin",
                                "/api/auth/register", // 普通注册：允许匿名调用
                                "/api/auth/register/verify",
                                "/api/auth/register/resend-code",
                                "/api/auth/login",
                                "/api/auth/logout",
                                "/api/auth/csrf-token",
                                "/api/auth/password-reset/status",
                                "/api/auth/password-reset/reset",
                                "/api/auth/password-reset/send-code",
                                "/api/auth/initial-setup-status",
                                // 手动触发热度分重算：脚本/运维调用时通常不带 CSRF token
                                "/api/admin/hot-scores/recompute-24h",
                                "/api/admin/hot-scores/recompute-7d",
                                "/api/admin/hot-scores/recompute-all"
                        );
                })
                .authorizeHttpRequests(authz -> {
                    logger.debug("配置请求授权...");
                    authz
                        .requestMatchers(
                                // 仅放行 API 认证相关端点
                                "/api/setup/**",
                                "/api/auth/login",
                                "/api/auth/logout",
                                "/api/auth/csrf-token",
                                "/api/auth/password-reset/status",
                                "/api/auth/password-reset/reset",
                                "/api/auth/password-reset/send-code",
                                "/api/auth/initial-setup-status",
                                "/api/auth/register-initial-admin",
                                "/api/auth/register", // 普通注册：允许匿名调用
                                "/api/auth/register/verify",
                                "/api/auth/register/resend-code",
                                // 放行热榜（今日热门/热榜页）
                                "/api/hot"
                        ).permitAll()
                        // 当前登录信息/权限上下文：必须登录
                        .requestMatchers(
                                "/api/auth/current-admin",
                                "/api/auth/access-context"
                        ).authenticated()
                        // 前台发现页：允许匿名浏览板块/帖子
                        .requestMatchers(org.springframework.http.HttpMethod.GET,
                                "/api/boards",
                                "/api/posts",
                                "/api/posts/*",
                                "/api/portal/users/*/profile"
                        ).permitAll()
                        // 手动触发热度分重算：需要登录（建议后续改为仅管理员）
                        .requestMatchers(
                                "/api/admin/hot-scores/recompute-24h",
                                "/api/admin/hot-scores/recompute-all"
                        ).authenticated()
                        // 其他 /api/** 请求都需要认证
                        .anyRequest().authenticated();
                })
                // 遇到未经认证时，直接 401，不携带 WWW-Authenticate 头
                .exceptionHandling(ex -> {
                    logger.debug("配置异常处理开始...");
                    ex.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED));
                    logger.debug("异常处理配置完成。");
                });
        logger.debug("配置 API 安全过滤链完成。");
        return http.build();
    }

    // 主要安全配置，用于处理Web页面请求，使用表单登录
    @Bean
    @Order(2) // 较低的优先级，匹配所有其他请求
    public SecurityFilterChain webSecurityFilterChain(HttpSecurity http) throws Exception {
        logger.debug("配置 Web 安全过滤链开始...");
        http
                .securityMatcher("/**") // 匹配所有其他请求
                .cors(cors -> {
                    logger.debug("配置 CORS 开始...");
                    cors.configurationSource(corsConfigurationSource());
                    logger.debug("CORS 配置完成。");
                })
                .csrf(csrf -> {
                    logger.debug("配置 CSRF 开始...");
                    // 使用 CsrfTokenRequestAttributeHandler 替代 XorCsrfTokenRequestAttributeHandler
                    CsrfTokenRequestAttributeHandler requestHandler = new CsrfTokenRequestAttributeHandler();
                    requestHandler.setCsrfRequestAttributeName("_csrf");
                    
                    csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(requestHandler);
                    logger.debug("CSRF 配置完成。");
                })
                .sessionManagement(session -> {
                    logger.debug("配置会话管理开始...");
                    session
                        .sessionCreationPolicy(SessionCreationPolicy.ALWAYS);
                    logger.debug("会话管理配置完成。");
                })
                .authorizeHttpRequests(authorize -> {
                    logger.debug("配置请求授权开始...");
                    authorize
                        // 放行静态资源与前端入口
                        .requestMatchers("/", "/index.html", "/assets/**", "/fonts/**", "/favicon.ico", "/robots.txt", "/vite-manifest.json").permitAll()
                        .requestMatchers("/css/**", "/js/**", "/images/**", "/webjars/**").permitAll()
                        // 其余所有非 /api/** 的页面路由交给 SPA，无需鉴权
                        .anyRequest().permitAll();
                });
        logger.debug("配置 Web 安全过滤链完成。");
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // 允许前端开发服务器域名（同时支持 localhost 与 127.0.0.1）
        configuration.setAllowedOrigins(Arrays.asList("http://localhost:5173", "http://127.0.0.1:5173"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "X-CSRF-TOKEN", "X-XSRF-TOKEN"));
        configuration.setExposedHeaders(Arrays.asList("X-CSRF-TOKEN", "X-XSRF-TOKEN"));
        configuration.setAllowCredentials(true); // 允许凭证（cookie）传递
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public UserDetailsService userDetailsService() {
        // 使用 AdministratorService 查找用户，认证逻辑从 username 改为 email
        return email -> {
            Optional<UsersEntity> userOptional = administratorService.findByUsername(email);

            UsersEntity user = userOptional
                    .orElseThrow(() -> new UsernameNotFoundException("用户 '" + email + "' 不存在"));

            // 用户状态检查使用新 AccountStatus 枚举
            if (user.getStatus() != AccountStatus.ACTIVE) {
                throw new UsernameNotFoundException("用户账户不可用");
            }

            return org.springframework.security.core.userdetails.User
                    .withUsername(user.getEmail())
                    .password(user.getPasswordHash())
                    // 关键：把 DB 中的角色、权限灌进 authorities
                    .authorities(accessControlService.buildAuthorities(user.getId()))
                    .build();
        };
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // 使用 BCrypt 进行密码加密，这是推荐的方式
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
        var authProvider = new org.springframework.security.authentication.dao.DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(authProvider);
    }
}
