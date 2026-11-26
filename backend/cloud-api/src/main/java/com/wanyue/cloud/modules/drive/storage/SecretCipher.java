package com.wanyue.cloud.modules.drive.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class SecretCipher {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final SecretKeySpec keySpec;

    public SecretCipher(@Value("${cloud.auth.jwt.secret}") String masterSecret) {
        try {
            byte[] key = MessageDigest.getInstance("SHA-256").digest(masterSecret.getBytes(StandardCharsets.UTF_8));
            this.keySpec = new SecretKeySpec(key, "AES");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) return "";
        try {
            byte[] iv = new byte[12];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
            return "enc:v1:" + Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new IllegalStateException("凭据加密失败", e);
        }
    }

    public String decrypt(String ciphertext) {
        if (ciphertext == null || ciphertext.isEmpty()) return "";
        if (!ciphertext.startsWith("enc:v1:")) return ciphertext;
        try {
            byte[] combined = Base64.getDecoder().decode(ciphertext.substring("enc:v1:".length()));
            byte[] iv = new byte[12];
            byte[] data = new byte[combined.length - 12];
            System.arraycopy(combined, 0, iv, 0, 12);
            System.arraycopy(combined, 12, data, 0, data.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(data), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("凭据解密失败", e);
        }
    }
}
