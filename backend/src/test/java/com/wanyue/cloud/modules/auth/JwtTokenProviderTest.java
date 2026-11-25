package com.wanyue.cloud.modules.auth;

import com.wanyue.cloud.modules.auth.support.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.AbstractDeserializer;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.io.Reader;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenProviderTest {
    private final JsonMapper json = new JsonMapper();
    private final byte[] secret = "test-only-secret-at-least-32-bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    private final String base64Secret = Base64.getEncoder().encodeToString(secret);
    private final JwtTokenProvider tokens = new JwtTokenProvider(base64Secret, "cloudlane", 900, json);

    private AbstractDeserializer<Map<String, ?>> deserializer() {
        return new AbstractDeserializer<>() {
            @Override
            @SuppressWarnings("unchecked")
            protected Map<String, ?> doDeserialize(Reader reader) {
                return json.readValue(reader, Map.class);
            }
        };
    }

    @Test
    void generatedTokenHasValidSignatureIdentityExpiryAndUniqueId() {
        String token = tokens.createAccessToken(2124534695294148609L, 3);
        Claims claims = Jwts.parser().json(deserializer()).verifyWith(Keys.hmacShaKeyFor(secret))
                .requireIssuer("cloudlane").build().parseSignedClaims(token).getPayload();
        assertEquals("2124534695294148609", claims.getSubject());
        assertEquals("access", claims.get("tokenType", String.class));
        assertEquals(3, claims.get("sessionVersion", Integer.class));
        assertEquals(900_000L, claims.getExpiration().getTime() - claims.getIssuedAt().getTime());
        assertNotNull(claims.getId());
        assertNotEquals(token, tokens.createAccessToken(2124534695294148609L, 3));
        assertFalse(claims.containsKey("password"));
        assertFalse(claims.containsKey("email"));
    }

    @Test
    void expiredTokensAndWrongSigningKeysAreRejected() {
        String token = tokens.createAccessToken(20L, 0);
        assertThrows(ExpiredJwtException.class, () -> Jwts.parser().json(deserializer())
                .verifyWith(Keys.hmacShaKeyFor(secret))
                .clock(() -> Date.from(Instant.now().plusSeconds(1000))).build().parseSignedClaims(token));
        byte[] wrongSecret = "different-test-secret-at-least-32-bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        assertThrows(io.jsonwebtoken.security.SecurityException.class, () -> Jwts.parser().json(deserializer())
                .verifyWith(Keys.hmacShaKeyFor(wrongSecret)).build().parseSignedClaims(token));
    }

    @Test
    void invalidKeyAndLifetimeFailAtInitializationWithoutLeakingSecret() {
        assertThrows(IllegalArgumentException.class, () -> new JwtTokenProvider("bad-base64-!", "cloudlane", 900, json));
        assertThrows(IllegalArgumentException.class, () -> new JwtTokenProvider("c2hvcnQ=", "cloudlane", 900, json));
        assertThrows(IllegalArgumentException.class, () -> new JwtTokenProvider(base64Secret, "cloudlane", 0, json));
        assertThrows(IllegalArgumentException.class, () -> tokens.createAccessToken(null, 0));
    }
}
