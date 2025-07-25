// 文件路径： java/com/example/hellospringboot/config/SecurityConfig.java
package com.example.NewsHubCommunity.config;

import com.example.NewsHubCommunity.entity.Administrator; // 添加对 Administrator 实体的导入
import com.example.NewsHubCommunity.service.AdministratorService; // 改为注入Service
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.Optional;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    // 改为注入Service
    @Autowired
    private AdministratorService administratorService;

    // API路径的安全配置，使用HTTP Basic认证
    @Bean
    @Order(1)
    public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http) throws Exception {
        System.out.println("配置 API 安全过滤链开始...");
        // 创建CSRF令牌处理器
        CookieCsrfTokenRepository tokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        XorCsrfTokenRequestAttributeHandler delegate = new XorCsrfTokenRequestAttributeHandler();
        // 使用无状态CSRF处理器
        CsrfTokenRequestHandler requestHandler = delegate::handle;
        System.out.println("CSRF令牌处理器已创建。");
        http
                .securityMatcher("/api/**")
                .cors(cors -> {
                    System.out.println("配置 CORS 开始...");
                    cors.configurationSource(corsConfigurationSource());
                    System.out.println("CORS 配置完成。");
                })
                // 保留CSRF保护
                .csrf(csrf -> {
                    System.out.println("配置 CSRF 开始...");
                    // ...existing code...
                    System.out.println("CSRF 配置完成。");
                })
                // 保留 httpBasic 也行，只要后面不��出 WWW-Authenticate 就不会弹窗
                .httpBasic(Customizer.withDefaults())
                .authorizeHttpRequests(authz -> {
                    System.out.println("配置请求授权开始...");
                    authz
                        .requestMatchers(
                                "/api/auth/login",
                                "/api/auth/csrf-token",
                                "/api/auth/current-admin",    // ← 前端允许匿名调用��接口
                                "/api/auth/initial-setup-status",  // ← 新增：允许匿名访问初始设置状态查询
                                "/api/auth/register-initial-admin" // ← 新增：允许匿名注册初始管理员
                        ).permitAll()
                        .anyRequest().authenticated();
                    System.out.println("请求授权配置完成。");
                })
                // 遇到未经认证时，直接 401，不携带 WWW-Authenticate 头
                .exceptionHandling(ex -> {
                    System.out.println("配置异常处理开始...");
                    ex.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED));
                    System.out.println("异常处理配置完成。");
                });
        System.out.println("配置 API 安全过滤链完成。");
        return http.build();
    }

    // 主要安全配置，用于处理Web页面请求，使用表单登录
    @Bean
    @Order(2) // 较低的优先级，匹配所有其他请求
    public SecurityFilterChain webSecurityFilterChain(HttpSecurity http) throws Exception {
        System.out.println("配置 Web 安全过滤链开始...");
        http
                .securityMatcher("/**") // 匹配所有其他请求
                // 配置CORS
                .cors(cors -> {
                    System.out.println("配置 CORS 开始...");
                    cors.configurationSource(corsConfigurationSource());
                    System.out.println("CORS 配置完成。");
                })

                // 配置CSRF - 确保所有接口都受CSRF保护
                .csrf(csrf -> {
                    System.out.println("配置 CSRF 开始...");
                    // ...existing code...
                    System.out.println("CSRF 配置完成。");
                })

                // 会话管理
                .sessionManagement(session -> {
                    System.out.println("配置会话管理开始...");
                    session
                        .sessionCreationPolicy(SessionCreationPolicy.ALWAYS)
                        .invalidSessionUrl("/login?expired")
                        .maximumSessions(1);
                    System.out.println("会话管理配置完成。");
                })

                // 配置请求授权
                .authorizeHttpRequests(authorize -> {
                    System.out.println("配置请求授权开始...");
                    authorize
                        // 允许匿名访问登录页面、静态资源和错误页面
                        .requestMatchers("/", "/login","/register", "/error", "/css/**", "/js/**", "/images/**", "/webjars/**").permitAll()
                        // 允许访问Vite资源
                        .requestMatchers("/vite-manifest.json", "/src/**", "/assets/**", "/@vite/**", "/@fs/**").permitAll()
                        // 所有其他请求需要认证
                        .anyRequest().authenticated();
                    System.out.println("请求授权配置完成。");
                })

                // 表单登录配置
                .formLogin(formLogin -> {
                    System.out.println("配置表单登录开始...");
                    formLogin
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .defaultSuccessUrl("/welcome", true)
                        .failureUrl("/login?error")
                        .permitAll();
                    System.out.println("表单登录配置完成。");
                });
        System.out.println("配置 Web 安全过滤链完成。");
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // 允许前端开发服务器域名
        configuration.setAllowedOrigins(Arrays.asList("http://localhost:5173"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "X-CSRF-TOKEN"));
        configuration.setExposedHeaders(Arrays.asList("X-CSRF-TOKEN"));
        configuration.setAllowCredentials(true); // 允许凭证（cookie）传递
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public UserDetailsService userDetailsService() {
        // 使用AdministratorService查找用户
        return username -> {
            Optional<Administrator> userOptional = administratorService.findByUsername(username);

            Administrator user = userOptional
                    .orElseThrow(() -> new UsernameNotFoundException("用户 '" + username + "' 不存在"));

            return org.springframework.security.core.userdetails.User
                    .withUsername(user.getAccount())
                    .password(user.getPassword())
                    .authorities("USER")
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
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(authProvider);
    }
}
