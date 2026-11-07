package com.catolica.ideiaspace.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Reads configuration directly from environment variables using @Value.
 * Required variables cause application startup failure via IllegalStateException.
 */
@Component
public class AppSecurityProperties {
    @Value("${APP_JWT_SECRET:}")
    private String jwtSecret;

    @Value("${APP_TOKEN_TTL_MINUTES:60}")
    private Integer tokenTtlMinutes;

    @Value("${APP_CPF_KEY:}")
    private String cpfKey;

    @Value("${APP_CPF_HMAC_KEY:}")
    private String cpfHmacKey;

    @Value("${APP_CORS_ORIGINS:}")
    private String corsOrigins;

    public String getJwtSecret() { return jwtSecret; }
    public void setJwtSecret(String jwtSecret) { this.jwtSecret = jwtSecret; }
    public Integer getTokenTtlMinutes() { return tokenTtlMinutes; }
    public void setTokenTtlMinutes(Integer tokenTtlMinutes) { this.tokenTtlMinutes = tokenTtlMinutes; }
    public String getCpfKey() { return cpfKey; }
    public void setCpfKey(String cpfKey) { this.cpfKey = cpfKey; }
    public String getCpfHmacKey() { return cpfHmacKey; }
    public void setCpfHmacKey(String cpfHmacKey) { this.cpfHmacKey = cpfHmacKey; }
    public String getCorsOrigins() { return corsOrigins; }
    public void setCorsOrigins(String corsOrigins) { this.corsOrigins = corsOrigins; }

    /** Returns the list of origins split by comma, trimmed, ignoring empty entries. */
    public List<String> getCorsOriginsList() {
        if (!StringUtils.hasText(corsOrigins)) {
            return List.of();
        }
        return Arrays.stream(corsOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    @PostConstruct
    public void validate() {
        if (!StringUtils.hasText(jwtSecret) || jwtSecret.length() < 32) {
            throw new IllegalStateException("APP_JWT_SECRET must be set and at least 32 characters long");
        }
        if (!StringUtils.hasText(cpfKey)) {
            throw new IllegalStateException("APP_CPF_KEY must be set");
        }
        if (!StringUtils.hasText(cpfHmacKey)) {
            throw new IllegalStateException("APP_CPF_HMAC_KEY must be set");
        }
    }
}
