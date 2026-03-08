package com.example.EnterpriseRagCommunity.security;

import com.example.EnterpriseRagCommunity.repository.access.UsersRepository;
import com.example.EnterpriseRagCommunity.service.access.AccessControlService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Refreshes session-stored authorities when RBAC is changed.
 *
 * Why needed:
 * - This project is session-based (JSESSIONID).
 * - Authorities are computed at login time and stored in the session SecurityContext.
 * - When an admin edits role-permission / user-role mapping, existing sessions keep stale authorities.
 *
 * Strategy:
 * - Store a lightweight "access timestamp" in session (based on users.updated_at).
 * - If users.updated_at changes, rebuild authorities from DB and replace Authentication in SecurityContext.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnBean({AccessControlService.class, UsersRepository.class})
public class AccessChangedFilter extends OncePerRequestFilter {

    public static final String SESSION_ACCESS_TS_KEY = "ACCESS_TS";
    public static final String SESSION_INVALIDATED_TS_KEY = "AUTH_INVALIDATED_AT";
    public static final String SESSION_ACCESS_VER_KEY = "ACCESS_VER";
    public static final String SESSION_LAST_CHECK_AT_KEY = "ACCESS_LAST_CHECK_AT";

    private final AccessControlService accessControlService;
    private final UsersRepository usersRepository;

    @Value("${security.access-refresh.enabled:true}")
    private boolean enabled;

    @Value("${security.access-refresh.check-interval-ms:5000}")
    private long checkIntervalMs;

    @Override
    protected void doFilterInternal(HttpServletRequest request, jakarta.servlet.http.HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        if (!enabled) {
            filterChain.doFilter(request, response);
            return;
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getName() == null) {
            filterChain.doFilter(request, response);
            return;
        }

        // Avoid any accidental refresh loop on login endpoint.
        String uri = request.getRequestURI();
        if (uri != null && (uri.startsWith("/api/auth/login") || uri.startsWith("/api/auth/logout"))) {
            filterChain.doFilter(request, response);
            return;
        }

        // Only session-backed flows need this; if no session, skip.
        HttpSession session = request.getSession(false);
        if (session == null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            long now = System.currentTimeMillis();
            Object lastCheckRaw = session.getAttribute(SESSION_LAST_CHECK_AT_KEY);
            long lastCheckAt = (lastCheckRaw instanceof Number) ? ((Number) lastCheckRaw).longValue() : 0L;
            if (checkIntervalMs > 0 && lastCheckAt > 0 && now - lastCheckAt < checkIntervalMs) {
                filterChain.doFilter(request, response);
                return;
            }
            session.setAttribute(SESSION_LAST_CHECK_AT_KEY, now);

            var meta = usersRepository.findAccessMetaByEmail(auth.getName()).orElseThrow(java.util.NoSuchElementException::new);

            long dbInvalidatedTs = meta.getSessionInvalidatedAt() == null
                    ? 0L
                    : meta.getSessionInvalidatedAt().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
            Object inv = session.getAttribute(SESSION_INVALIDATED_TS_KEY);
            long sessionInvalidatedTs = (inv instanceof Number) ? ((Number) inv).longValue() : 0L;

            if (dbInvalidatedTs > 0L && dbInvalidatedTs != sessionInvalidatedTs && dbInvalidatedTs > sessionInvalidatedTs) {
                try {
                    session.invalidate();
                } catch (IllegalStateException ignored) {
                }
                SecurityContextHolder.clearContext();
                response.setStatus(401);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"message\":\"登录状态已失效，请重新登录\"}");
                return;
            }

            if (dbInvalidatedTs > 0L && sessionInvalidatedTs == 0L) {
                session.setAttribute(SESSION_INVALIDATED_TS_KEY, dbInvalidatedTs);
            }

            Object verRaw = session.getAttribute(SESSION_ACCESS_VER_KEY);
            long sessionVer = (verRaw instanceof Number) ? ((Number) verRaw).longValue() : -1L;
            long dbVer = meta.getAccessVersion() == null ? 0L : meta.getAccessVersion();

            if (sessionVer < 0) {
                session.setAttribute(SESSION_ACCESS_VER_KEY, dbVer);
            }

            if (dbVer != sessionVer) {
                List<GrantedAuthority> newAuthorities = accessControlService.buildAuthorities(meta.getId());
                Authentication newAuth = new UsernamePasswordAuthenticationToken(auth.getPrincipal(), auth.getCredentials(), newAuthorities);

                // Replace authentication in current security context (and therefore in session).
                SecurityContext ctx = SecurityContextHolder.getContext();
                ctx.setAuthentication(newAuth);
                session.setAttribute(SESSION_ACCESS_VER_KEY, dbVer);
            }
        } catch (java.util.NoSuchElementException e) {
            boolean hasSessionMarker =
                    session.getAttribute(SESSION_ACCESS_VER_KEY) != null || session.getAttribute(SESSION_INVALIDATED_TS_KEY) != null;
            if (hasSessionMarker) {
                try {
                    session.invalidate();
                } catch (IllegalStateException ignored) {
                }
                SecurityContextHolder.clearContext();
                response.setStatus(401);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"message\":\"登录状态已失效，请重新登录\"}");
                return;
            }
        } catch (Exception ignored) {
        }

        filterChain.doFilter(request, response);
    }
}
