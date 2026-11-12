package com.catolica.ideiaspace;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@SpringBootTest
class DatabaseRlsSmokeTests {

    @Value("${spring.flyway.url:${spring.datasource.url}}")
    private String dbUrl;

    @Value("${spring.flyway.user}")
    private String dbUser;

    @Value("${spring.flyway.password}")
    private String dbPassword;

    private void executeSqlFile(String resourcePath) throws Exception {
        ClassPathResource resource = new ClassPathResource(resourcePath);
        String sql;
        try (InputStream is = resource.getInputStream()) {
            sql = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }

        try (Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }

    @Test
    @DisplayName("Executa Smoke Tests de catálogo, integridade e permissões (03_smoke_tests_asserted.sql)")
    void testSmokeCatalogAndPermissions() {
        assertDoesNotThrow(() -> executeSqlFile("db/test/03_smoke_tests_asserted.sql"));
    }

    @Test
    @DisplayName("Executa os 17 testes de políticas de segurança Row Level Security (02_rls_tests.sql)")
    void testRowLevelSecurityPolicies() {
        assertDoesNotThrow(() -> executeSqlFile("db/test/02_rls_tests.sql"));
    }
}
