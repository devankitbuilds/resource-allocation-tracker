package com.rat.tracker.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;

import com.rat.tracker.service.SessionService;

class JwtAuthwnticationFilterTests {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void forcedPasswordChangeBlocksOtherRoutesButAllowsPasswordChangeRoute() throws Exception {
        JwtService jwtService = mock(JwtService.class);
        CustomUserDetailsService userDetailsService = mock(CustomUserDetailsService.class);
        SessionService sessionService = mock(SessionService.class);
        UUID sessionId = UUID.randomUUID();
        var forcedUser = User.withUsername("viewer@example.com")
                .password("encoded")
                .authorities("ROLE_VIEWER", "PASSWORD_CHANGE_REQUIRED")
                .build();
        when(jwtService.extractUsername("valid-token")).thenReturn("viewer@example.com");
        when(jwtService.extractSessionId("valid-token")).thenReturn(sessionId);
        when(jwtService.isTokenValid("valid-token", forcedUser)).thenReturn(true);
        when(userDetailsService.loadUserByUsername("viewer@example.com")).thenReturn(forcedUser);
        when(sessionService.isSessionActive(sessionId, "viewer@example.com")).thenReturn(true);
        ExposedJwtFilter filter = new ExposedJwtFilter(jwtService, userDetailsService, sessionService);

        MockHttpServletRequest blockedRequest = request("/resources");
        MockHttpServletResponse blockedResponse = new MockHttpServletResponse();
        MockFilterChain blockedChain = new MockFilterChain();
        filter.filter(blockedRequest, blockedResponse, blockedChain);

        assertEquals(403, blockedResponse.getStatus());
        assertTrue(blockedChain.getRequest() == null);

        SecurityContextHolder.clearContext();
        MockHttpServletRequest passwordRequest = request("/auth/password/change");
        MockHttpServletResponse passwordResponse = new MockHttpServletResponse();
        MockFilterChain passwordChain = new MockFilterChain();
        filter.filter(passwordRequest, passwordResponse, passwordChain);

        assertEquals(200, passwordResponse.getStatus());
        assertTrue(passwordChain.getRequest() != null);
        assertTrue(SecurityContextHolder.getContext().getAuthentication().isAuthenticated());
    }

    private static MockHttpServletRequest request(String servletPath) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContextPath("/api/v1");
        request.setServletPath(servletPath);
        request.addHeader("Authorization", "Bearer valid-token");
        return request;
    }

    private static final class ExposedJwtFilter extends JwtAuthwnticationFilter {
        private ExposedJwtFilter(
                JwtService jwtService,
                CustomUserDetailsService userDetailsService,
                SessionService sessionService) {
            super(jwtService, userDetailsService, sessionService);
        }

        private void filter(
                MockHttpServletRequest request,
                MockHttpServletResponse response,
                MockFilterChain chain) throws Exception {
            doFilterInternal(request, response, chain);
        }
    }
}
