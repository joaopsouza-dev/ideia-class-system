package com.catolica.ideiaspace.security;

import org.springframework.stereotype.Component;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

/**
 * Service for creating and validating JWT‑like tokens.
 * Token format: base64url(payload) + "." + base64url(signature)
 * Payload: "usuarioId|perfil|expiracaoEpochSeconds"
 */
@Component
public class TokenService {

    private final String secret;
    private final int ttlMinutes;

    public TokenService(com.catolica.ideiaspace.config.AppSecurityProperties props) {
        this.secret = props.getJwtSecret();
        this.ttlMinutes = props.getTokenTtlMinutes() != null ? props.getTokenTtlMinutes() : 60;
    }

    public String generate(Long usuarioId, String perfil) {
        long exp = Instant.now().plus(ttlMinutes, ChronoUnit.MINUTES).getEpochSecond();
        String payload = String.format("%d|%s|%d", usuarioId, perfil, exp);
        String signature = hmacSha256(payload, secret);
        return base64UrlEncode(payload) + "." + base64UrlEncode(signature);
    }

    public TokenClaims validate(String token) {
        if (token == null) return null;
        String[] parts = token.split("\\.");
        if (parts.length != 2) return null;
        String payload;
        String signature;
        try {
            payload = base64UrlDecode(parts[0]);
            signature = base64UrlDecode(parts[1]);
        } catch (IllegalArgumentException e) {
            return null;
        }
        // recompute expected signature
        String expectedSig = hmacSha256(payload, secret);
        if (!MessageDigest.isEqual(expectedSig.getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8))) {
            return null;
        }
        String[] segs = payload.split("\\|");
        if (segs.length != 3) return null;
        try {
            Long usuarioId = Long.valueOf(segs[0]);
            String perfil = segs[1];
            long exp = Long.parseLong(segs[2]);
            if (Instant.now().getEpochSecond() > exp) {
                return null;
            }
            return new TokenClaims(usuarioId, perfil);
        } catch (Exception e) {
            return null;
        }
    }

    private String hmacSha256(String data, String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return new String(hash, StandardCharsets.ISO_8859_1);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to calculate HMAC", e);
        }
    }

    private String base64UrlEncode(String str) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(str.getBytes(StandardCharsets.UTF_8));
    }

    private String base64UrlEncode(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String base64UrlDecode(String encoded) {
        byte[] decoded = Base64.getUrlDecoder().decode(encoded);
        return new String(decoded, StandardCharsets.UTF_8);
    }

    public record TokenClaims(Long usuarioId, String perfil) {}
}
