package com.example.EnterpriseRagCommunity.security;

import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.service.access.AccessControlService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
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
public class AccessChangedFilter extends OncePerRequestFilter {

    public static final String SESSION_ACCESS_TS_KEY = "ACCESS_TS";

    private final AccessControlService accessControlService;

    @Value("${security.access-refresh.enabled:true}")
    private boolean enabled;

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
            UsersEntity user = accessControlService.loadActiveUserByEmail(auth.getName());
            long dbTs = user.getUpdatedAt() == null
                    ? 0L
                    : user.getUpdatedAt().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();

            Object v = session.getAttribute(SESSION_ACCESS_TS_KEY);
            long sessionTs = (v instanceof Number) ? ((Number) v).longValue() : 0L;

            if (dbTs > 0L && (sessionTs == 0L || dbTs != sessionTs)) {
                List<GrantedAuthority> newAuthorities = accessControlService.buildAuthorities(user.getId());
                Authentication newAuth = new UsernamePasswordAuthenticationToken(auth.getPrincipal(), auth.getCredentials(), newAuthorities);

                // Replace authentication in current security context (and therefore in session).
                SecurityContext ctx = SecurityContextHolder.getContext();
                ctx.setAuthentication(newAuth);
                session.setAttribute(SESSION_ACCESS_TS_KEY, dbTs);
            }
        } catch (Exception ignored) {
            // Never break requests due to refresh logic.
        }

        filterChain.doFilter(request, response);
    }
}
