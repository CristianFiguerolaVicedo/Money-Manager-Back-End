package com.cristian.moneymanager.security;

import com.cristian.moneymanager.service.RefreshTokenService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * The refresh-token cookie: HttpOnly (JavaScript cannot read it, so an XSS cannot steal the session),
 * Secure, SameSite=Strict (never sent by other sites, which also protects the cookie-based endpoints from CSRF)
 * and limited to the API path. The web reaches the API through a Netlify proxy on its own domain,
 * so this is a first-party cookie that also works in Safari.
 */
@Component
public class SessionCookie {

    public static final String NAME = "mm_session";

    @Value("${app.cookie.secure:true}")
    private boolean secure;

    public void set(HttpServletRequest request, HttpServletResponse response, String rawToken) {
        response.addHeader(HttpHeaders.SET_COOKIE, build(request, rawToken, RefreshTokenService.LIFETIME.toSeconds()));
    }

    public void clear(HttpServletRequest request, HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, build(request, "", 0));
    }

    public String read(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        return Arrays.stream(request.getCookies())
                .filter(cookie -> NAME.equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }

    private String build(HttpServletRequest request, String value, long maxAgeSeconds) {
        String path = request.getContextPath() == null || request.getContextPath().isEmpty() ? "/" : request.getContextPath();
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path(path)
                .maxAge(maxAgeSeconds)
                .build()
                .toString();
    }
}
