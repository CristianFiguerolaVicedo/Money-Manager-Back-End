package com.cristian.moneymanager.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class ClientIpResolverTest {

    private static final String SECRET = "a-very-long-proxy-secret-for-the-tests-0123456789";

    private static String netlifySignature(String secret, String issuer) {
        return Jwts.builder().setIssuer(issuer).setExpiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256).compact();
    }

    private static MockHttpServletRequest request(String netlifyIp, String signature, String trueClientIp) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        if (netlifyIp != null) request.addHeader("X-Nf-Client-Connection-Ip", netlifyIp);
        if (signature != null) request.addHeader("x-nf-sign", signature);
        if (trueClientIp != null) request.addHeader("True-Client-IP", trueClientIp);
        return request;
    }

    @Test
    void trustsNetlifyIpOnlyWithAValidSignature() {
        ClientIpResolver resolver = new ClientIpResolver(SECRET);

        assertThat(resolver.resolve(request("185.1.1.1", netlifySignature(SECRET, "netlify"), "9.9.9.9"))).isEqualTo("185.1.1.1");
        // Forged header without signature, or signed with another key: ignored
        assertThat(resolver.resolve(request("185.1.1.1", null, "9.9.9.9"))).isEqualTo("9.9.9.9");
        assertThat(resolver.resolve(request("185.1.1.1", netlifySignature("another-secret-that-is-long-enough-0123456789", "netlify"), "9.9.9.9")))
                .isEqualTo("9.9.9.9");
        assertThat(resolver.resolve(request("185.1.1.1", netlifySignature(SECRET, "someone-else"), "9.9.9.9"))).isEqualTo("9.9.9.9");
    }

    @Test
    void withoutSecretUsesTheNetlifyHeaderAsBestEffort() {
        ClientIpResolver resolver = new ClientIpResolver("");
        assertThat(resolver.resolve(request("185.1.1.1", null, "9.9.9.9"))).isEqualTo("185.1.1.1");
        assertThat(resolver.resolve(request(null, null, "9.9.9.9"))).isEqualTo("9.9.9.9");
        assertThat(resolver.resolve(request(null, null, null))).isEqualTo("10.0.0.1");
    }

    @Test
    void shortSecretIsIgnored() {
        ClientIpResolver resolver = new ClientIpResolver("short");
        assertThat(resolver.validNetlifySignature(netlifySignature(SECRET, "netlify"))).isFalse();
    }
}
