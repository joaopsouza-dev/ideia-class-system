package com.catolica.ideiaspace.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Reads configuration directly from environment variables using @Value.
 * Required variables cause application startup failure via IllegalStateException.
 */
@Component
public class AppSecurityProperties {

    /**
     * Trechos típicos de valores de exemplo. Se aparecerem em um segredo, a aplicação
     * não sobe, para evitar iniciar em produção com a chave de exemplo do .env.example.
     */
    private static final List<String> MARCADORES_DE_EXEMPLO = List.of(
            "change-me", "changeme", "troque", "exemplo", "example", "placeholder");

    private static final int TAMANHO_MINIMO_SEGREDO = 32;

    @Value("${APP_JWT_SECRET:}")
    private String jwtSecret;

    @Value("${APP_TOKEN_TTL_MINUTES:60}")
    private Integer tokenTtlMinutes;

    @Value("${APP_CPF_KEY:}")
    private String cpfKey;

    @Value("${APP_CPF_HMAC_KEY:}")
    private String cpfHmacKey;

    @Value("${APP_CORS_ORIGINS:http://localhost:5173,http://localhost:3000}")
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
            return List.of("http://localhost:5173", "http://localhost:3000");
        }
        List<String> origins = Arrays.stream(corsOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
        if (origins.stream().anyMatch(o -> o.contains("localhost:3000")) && !origins.contains("http://localhost:5173")) {
            origins.add("http://localhost:5173");
        }
        return origins;
    }

    @PostConstruct
    public void validate() {
        validarSegredo("APP_JWT_SECRET", jwtSecret);
        validarSegredo("APP_CPF_KEY", cpfKey);
        validarSegredo("APP_CPF_HMAC_KEY", cpfHmacKey);
    }

    private void validarSegredo(String nome, String valor) {
        if (!StringUtils.hasText(valor)) {
            throw new IllegalStateException(nome + " must be set");
        }
        if (valor.length() < TAMANHO_MINIMO_SEGREDO) {
            throw new IllegalStateException(
                    nome + " must be set and at least " + TAMANHO_MINIMO_SEGREDO + " characters long");
        }
        String minusculo = valor.toLowerCase(Locale.ROOT);
        for (String marcador : MARCADORES_DE_EXEMPLO) {
            if (minusculo.contains(marcador)) {
                throw new IllegalStateException(
                        nome + " contém um valor de exemplo. Gere um segredo real (ver .env.example e FASE0_RELATORIO.md).");
            }
        }
    }
}
