package com.catolica.ideiaspace.security;

import org.springframework.stereotype.Component;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.SecureRandom;
import java.security.MessageDigest;
import java.util.Base64;


/**
 * Utility for hashing passwords using PBKDF2WithHmacSHA256.
 * Stored format: pbkdf2_sha256$iterations$saltBase64$hashBase64
 */
@Component
public class PasswordHasher {
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int ITERATIONS = 310_000;
    private static final int SALT_LENGTH = 16; // bytes
    private static final int HASH_LENGTH = 32; // bytes (256 bits)
    private static final SecureRandom RANDOM = new SecureRandom();

    public String hash(String password) {
        byte[] salt = new byte[SALT_LENGTH];
        RANDOM.nextBytes(salt);
        byte[] hash = pbkdf2(password.toCharArray(), salt);
        String saltB64 = Base64.getEncoder().encodeToString(salt);
        String hashB64 = Base64.getEncoder().encodeToString(hash);
        return String.format("pbkdf2_sha256$%d$%s$%s", ITERATIONS, saltB64, hashB64);
    }

    public boolean verify(String password, String stored) {
        if (stored == null) return false;
        try {
            String[] parts = stored.split("\\$");
            if (parts.length != 4) return false;
            if (!"pbkdf2_sha256".equals(parts[0])) return false;
            int iterations = Integer.parseInt(parts[1]);
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expectedHash = Base64.getDecoder().decode(parts[3]);
            byte[] actualHash = pbkdf2(password.toCharArray(), salt, iterations);
            return MessageDigest.isEqual(expectedHash, actualHash);
        } catch (Exception e) {
            return false;
        }
    }

    private byte[] pbkdf2(char[] password, byte[] salt) {
        return pbkdf2(password, salt, ITERATIONS);
    }

    private byte[] pbkdf2(char[] password, byte[] salt, int iterations) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, HASH_LENGTH * 8);
            SecretKeyFactory skf = SecretKeyFactory.getInstance(ALGORITHM);
            return skf.generateSecret(spec).getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException("Error while hashing password", e);
        }
    }
}
