package com.cristian.moneymanager.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

/**
 * Works out the real client IP for rate limiting.
 *
 * The web reaches the API through a Netlify proxy. Measured behaviour of that proxy: it overwrites
 * X-Nf-Client-Connection-Ip with the real client IP, but passes X-Forwarded-For and True-Client-IP through
 * as the client sent them (so those can be faked). Netlify also signs every proxied request (x-nf-sign,
 * HS256) with NETLIFY_PROXY_SECRET, which we verify: only then is X-Nf-Client-Connection-Ip trusted, so
 * nobody can fake it by calling Render directly.
 */
@Slf4j
@Component
public class ClientIpResolver {

    static final String SIGN_HEADER = "x-nf-sign";
    static final String NETLIFY_IP_HEADER = "X-Nf-Client-Connection-Ip";

    private final SecretKey proxyKey;

    public ClientIpResolver(@Value("${app.netlify.proxy-secret:}") String proxySecret) {
        if (proxySecret == null || proxySecret.isBlank()) {
            this.proxyKey = null;
            log.warn("NETLIFY_PROXY_SECRET is not set: requests coming through the Netlify proxy cannot be verified");
        } else if (proxySecret.trim().getBytes(StandardCharsets.UTF_8).length < 32) {
            this.proxyKey = null;
            log.error("NETLIFY_PROXY_SECRET is too short (use at least 32 characters): proxy signatures are not checked");
        } else {
            this.proxyKey = Keys.hmacShaKeyFor(proxySecret.trim().getBytes(StandardCharsets.UTF_8));
        }
    }

    public String resolve(HttpServletRequest request) {
        String netlifyIp = header(request, NETLIFY_IP_HEADER);
        if (netlifyIp != null && (proxyKey == null || validNetlifySignature(header(request, SIGN_HEADER)))) {
            // Without a configured secret this is a best effort (still better than one shared bucket for
            // every user behind Netlify); with it, the header is proven to come from our proxy.
            return netlifyIp;
        }
        // Direct request to Render (no Netlify): Render's edge provides the client address
        String trueClientIp = header(request, "True-Client-IP");
        if (trueClientIp != null) {
            return trueClientIp;
        }
        String forwarded = header(request, "X-Forwarded-For");
        if (forwarded != null) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    boolean validNetlifySignature(String jws) {
        if (proxyKey == null || jws == null) {
            return false;
        }
        try {
            Claims claims = Jwts.parserBuilder().setSigningKey(proxyKey).build().parseClaimsJws(jws).getBody();
            return "netlify".equals(claims.getIssuer());
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private static String header(HttpServletRequest request, String name) {
        String value = request.getHeader(name);
        return value == null || value.isBlank() ? null : value.trim();
    }
}
