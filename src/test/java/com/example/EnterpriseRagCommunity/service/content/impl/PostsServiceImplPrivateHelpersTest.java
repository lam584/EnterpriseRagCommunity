package com.example.EnterpriseRagCommunity.service.content.impl;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PostsServiceImplPrivateHelpersTest {

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void currentUsernameOrNullCoversDifferentAuthAndNameBranches() throws Exception {
        Method m = PostsServiceImpl.class.getDeclaredMethod("currentUsernameOrNull");
        m.setAccessible(true);

        SecurityContextHolder.clearContext();
        assertNull(m.invoke(null));

        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.unauthenticated("alice@example.com", "N/A"));
        assertNull(m.invoke(null));

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("anonymousUser", "N/A", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")))
        );
        assertNull(m.invoke(null));

        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getPrincipal()).thenReturn("user");
        when(auth.getName()).thenReturn(null);
        SecurityContextHolder.getContext().setAuthentication(auth);
        assertNull(m.invoke(null));

        Authentication auth2 = mock(Authentication.class);
        when(auth2.isAuthenticated()).thenReturn(true);
        when(auth2.getPrincipal()).thenReturn("user");
        when(auth2.getName()).thenReturn("   ");
        SecurityContextHolder.getContext().setAuthentication(auth2);
        assertNull(m.invoke(null));

        Authentication auth3 = mock(Authentication.class);
        when(auth3.isAuthenticated()).thenReturn(true);
        when(auth3.getPrincipal()).thenReturn("user");
        when(auth3.getName()).thenThrow(new RuntimeException("boom"));
        SecurityContextHolder.getContext().setAuthentication(auth3);
        assertNull(m.invoke(null));

        Authentication auth4 = mock(Authentication.class);
        when(auth4.isAuthenticated()).thenReturn(true);
        when(auth4.getPrincipal()).thenReturn("user");
        when(auth4.getName()).thenReturn(" alice@example.com ");
        SecurityContextHolder.getContext().setAuthentication(auth4);
        assertEquals("alice@example.com", m.invoke(null));
    }

    @Test
    void mapOfNonNullCoversNullOddAndEmptyCases() throws Exception {
        Method m = PostsServiceImpl.class.getDeclaredMethod("mapOfNonNull", Object[].class);
        m.setAccessible(true);

        assertEquals(Map.of(), m.invoke(null, new Object[]{null}));
        assertEquals(Map.of(), m.invoke(null, new Object[]{new Object[]{}}));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            try {
                m.invoke(null, new Object[]{new Object[]{"k1"}});
            } catch (Exception e) {
                throw (RuntimeException) e.getCause();
            }
        });
        assertEquals("kv 必须为偶数长度", ex.getMessage());

        Map<?, ?> r1 = (Map<?, ?>) m.invoke(null, new Object[]{new Object[]{null, 1, "k2", null}});
        assertEquals(Map.of(), r1);

        Map<?, ?> r2 = (Map<?, ?>) m.invoke(null, new Object[]{new Object[]{"k1", 1, null, 2, "k3", "v3"}});
        assertEquals(2, r2.size());
        assertEquals(1, r2.get("k1"));
        assertEquals("v3", r2.get("k3"));
    }

    @Test
    void safeTextCoversNullBlankMaxLenAndTruncate() throws Exception {
        Method m = PostsServiceImpl.class.getDeclaredMethod("safeText", String.class, int.class);
        m.setAccessible(true);

        assertNull(m.invoke(null, null, 10));
        assertNull(m.invoke(null, "   \n\t", 10));
        assertEquals("", m.invoke(null, "abc", 0));
        assertEquals("ab", m.invoke(null, "ab", 10));
        assertEquals("ab", m.invoke(null, "ab\r\n\tcd", 2));
    }

    @Test
    void tagHelpersCoverDifferentShapes() throws Exception {
        Method normalize = PostsServiceImpl.class.getDeclaredMethod("normalizeTagSlug", String.class);
        normalize.setAccessible(true);
        assertNull(normalize.invoke(null, new Object[]{null}));
        assertNull(normalize.invoke(null, "   "));
        assertNull(normalize.invoke(null, "\n\t"));
        assertEquals("a", normalize.invoke(null, " a "));
        String longTag = "x".repeat(120);
        assertEquals(96, ((String) normalize.invoke(null, longTag)).length());

        Method resolveTags = PostsServiceImpl.class.getDeclaredMethod("resolveTags", List.class, Map.class);
        resolveTags.setAccessible(true);
        List<?> out1 = (List<?>) resolveTags.invoke(null, Arrays.asList(" a ", null, "  ", "b\t"), null);
        assertEquals(List.of("a", "b"), out1);

        Map<String, Object> meta1 = new HashMap<>();
        meta1.put("tags", "not-a-list");
        List<?> out2 = (List<?>) resolveTags.invoke(null, null, meta1);
        assertEquals(List.of(), out2);

        Map<String, Object> meta2 = new HashMap<>();
        meta2.put("tags", Arrays.asList(" a ", null, 123, "  "));
        List<?> out3 = (List<?>) resolveTags.invoke(null, null, meta2);
        assertEquals(List.of("a", "123"), out3);

        Method merge = PostsServiceImpl.class.getDeclaredMethod("mergeMetadataWithTags", Map.class, List.class);
        merge.setAccessible(true);
        assertNull(merge.invoke(null, new Object[]{null, null}));

        Map<String, Object> r1 = (Map<String, Object>) merge.invoke(null, meta2, null);
        assertNotNull(r1);
        assertEquals(meta2, r1);

        Map<String, Object> r2 = (Map<String, Object>) merge.invoke(null, null, List.of(" a ", " "));
        assertNotNull(r2);
        assertEquals(List.of("a"), r2.get("tags"));
    }
}
