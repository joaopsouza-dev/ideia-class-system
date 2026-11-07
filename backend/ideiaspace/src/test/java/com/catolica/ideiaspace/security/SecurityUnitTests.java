package com.catolica.ideiaspace.security;

import com.catolica.ideiaspace.config.AppSecurityProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SecurityUnitTests {

    private String generateTestSecret() {
        return UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
    }

    private String generateTestKey() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private AppSecurityProperties createValidProperties() {
        AppSecurityProperties props = new AppSecurityProperties();
        props.setJwtSecret(generateTestSecret());
        props.setTokenTtlMinutes(60);
        props.setCpfKey(generateTestKey());
        props.setCpfHmacKey(generateTestKey());
        props.setCorsOrigins("http://localhost:3000");
        props.validate();
        return props;
    }

    // 1. PasswordHasher: hash + verify com senha correta retorna true.
    @Test
    @DisplayName("PasswordHasher: hash + verify com senha correta retorna true")
    void passwordHasher_correctPassword_returnsTrue() {
        PasswordHasher hasher = new PasswordHasher();
        String password = "pwd-" + UUID.randomUUID();

        String hashed = hasher.hash(password);

        assertNotNull(hashed);
        assertTrue(hasher.verify(password, hashed));
    }

    // 2. PasswordHasher: verify com senha errada retorna false.
    @Test
    @DisplayName("PasswordHasher: verify com senha errada retorna false")
    void passwordHasher_wrongPassword_returnsFalse() {
        PasswordHasher hasher = new PasswordHasher();
        String password = "pwd-" + UUID.randomUUID();
        String wrongPassword = "wrong-" + UUID.randomUUID();

        String hashed = hasher.hash(password);

        assertNotNull(hashed);
        assertFalse(hasher.verify(wrongPassword, hashed));
    }

    // 3. PasswordHasher: verify com string em formato inválido retorna false, sem exceção.
    @Test
    @DisplayName("PasswordHasher: verify com string em formato inválido retorna false, sem exceção")
    void passwordHasher_invalidStoredFormat_returnsFalseWithoutException() {
        PasswordHasher hasher = new PasswordHasher();
        String password = "pwd-" + UUID.randomUUID();

        assertDoesNotThrow(() -> {
            assertFalse(hasher.verify(password, null));
            assertFalse(hasher.verify(password, ""));
            assertFalse(hasher.verify(password, "invalid_stored_string"));
            assertFalse(hasher.verify(password, "pbkdf2_sha256"));
            assertFalse(hasher.verify(password, "pbkdf2_sha256$1000$salt"));
            assertFalse(hasher.verify(password, "pbkdf2_sha256$not_a_number$c2FsdA==$aGFzaA=="));
            assertFalse(hasher.verify(password, "other_algo$1000$c2FsdA==$aGFzaA=="));
            assertFalse(hasher.verify(password, "pbkdf2_sha256$1000$invalid_b64!$invalid_b64!"));
        });
    }

    // 4. TokenService: generate + validate retorna usuarioId e perfil corretos.
    @Test
    @DisplayName("TokenService: generate + validate retorna usuarioId e perfil corretos")
    void tokenService_generateAndValidate_returnsCorrectClaims() {
        AppSecurityProperties props = createValidProperties();
        TokenService tokenService = new TokenService(props);

        Long usuarioId = 101L;
        String perfil = "COORDENADOR";

        String token = tokenService.generate(usuarioId, perfil);
        assertNotNull(token);

        TokenService.TokenClaims claims = tokenService.validate(token);
        assertNotNull(claims);
        assertEquals(usuarioId, claims.usuarioId());
        assertEquals(perfil, claims.perfil());
    }

    // 5. TokenService: token com um caractere alterado no payload retorna null.
    @Test
    @DisplayName("TokenService: token com um caractere alterado no payload retorna null")
    void tokenService_tamperedPayload_returnsNull() {
        AppSecurityProperties props = createValidProperties();
        TokenService tokenService = new TokenService(props);

        Long usuarioId = 202L;
        String perfil = "ALUNO";
        String token = tokenService.generate(usuarioId, perfil);

        String[] parts = token.split("\\.");
        assertEquals(2, parts.length);

        char originalChar = parts[0].charAt(0);
        char alteredChar = (originalChar == 'A') ? 'B' : 'A';
        String alteredPayload = alteredChar + parts[0].substring(1);
        String tamperedToken = alteredPayload + "." + parts[1];

        TokenService.TokenClaims claims = tokenService.validate(tamperedToken);
        assertNull(claims);
    }

    // 6. TokenService: token com Base64 inválido, por exemplo "!!!.!!!", retorna null sem exceção.
    @Test
    @DisplayName("TokenService: token com Base64 inválido retorna null sem exceção")
    void tokenService_invalidBase64_returnsNullWithoutException() {
        AppSecurityProperties props = createValidProperties();
        TokenService tokenService = new TokenService(props);

        assertDoesNotThrow(() -> {
            assertNull(tokenService.validate("!!!.!!!"));
            assertNull(tokenService.validate("???.*%#"));
            assertNull(tokenService.validate("not_base64_payload.not_base64_signature"));
        });
    }

    // 7. AppSecurityProperties: validate() lança IllegalStateException quando o segredo tem menos de 32 caracteres.
    @Test
    @DisplayName("AppSecurityProperties: validate() lança IllegalStateException quando segredo tem menos de 32 caracteres")
    void appSecurityProperties_shortSecret_throwsIllegalStateException() {
        AppSecurityProperties props = new AppSecurityProperties();
        String shortSecret = UUID.randomUUID().toString().substring(0, 15);
        assertTrue(shortSecret.length() < 32);

        props.setJwtSecret(shortSecret);
        props.setCpfKey(generateTestKey());
        props.setCpfHmacKey(generateTestKey());

        IllegalStateException ex = assertThrows(IllegalStateException.class, props::validate);
        assertTrue(ex.getMessage().contains("APP_JWT_SECRET"));
    }

    @Test
    @DisplayName("TokenService: validações adicionais de formato, nulidade e expiração")
    void tokenService_additionalValidations() {
        AppSecurityProperties props = createValidProperties();
        TokenService tokenService = new TokenService(props);

        // Token nulo
        assertNull(tokenService.validate(null));

        // Partes diferentes de 2
        assertNull(tokenService.validate("token_sem_ponto"));
        assertNull(tokenService.validate("parte1.parte2.parte3"));

        // Token com TTL zero/expirado
        AppSecurityProperties expiredProps = new AppSecurityProperties();
        expiredProps.setJwtSecret(generateTestSecret());
        expiredProps.setTokenTtlMinutes(-1);
        expiredProps.setCpfKey(generateTestKey());
        expiredProps.setCpfHmacKey(generateTestKey());
        expiredProps.validate();

        TokenService expiredTokenService = new TokenService(expiredProps);
        String expiredToken = expiredTokenService.generate(303L, "PROFESSOR");
        assertNull(expiredTokenService.validate(expiredToken));
    }
}
