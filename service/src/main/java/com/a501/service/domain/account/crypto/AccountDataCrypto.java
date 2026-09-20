package com.a501.service.domain.account.crypto;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AccountDataCrypto {
    private static final int IV_BYTES = 12;
    private final SecretKeySpec encryptionKey;
    private final SecretKeySpec lookupKey;
    private final SecureRandom random = new SecureRandom();

    public AccountDataCrypto(@Value("${app.account.encryption-key}") String encodedKey) {
        byte[] key = Base64.getDecoder().decode(encodedKey);
        if (key.length != 32) {
            throw new IllegalArgumentException("Account encryption key must decode to 32 bytes");
        }
        encryptionKey = new SecretKeySpec(key, "AES");
        byte[] separated = MessageDigestHolder.sha256(ByteBuffer.allocate(key.length + 7)
                .put(key).put("lookup!".getBytes(StandardCharsets.UTF_8)).array());
        lookupKey = new SecretKeySpec(separated, "HmacSHA256");
    }

    public String encrypt(String value) {
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(
                    ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array());
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Could not protect account data", exception);
        }
    }

    public String decrypt(String encoded) {
        try {
            ByteBuffer data = ByteBuffer.wrap(Base64.getDecoder().decode(encoded));
            byte[] iv = new byte[IV_BYTES];
            data.get(iv);
            byte[] encrypted = new byte[data.remaining()];
            data.get(encrypted);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey, new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Could not read protected account data", exception);
        }
    }

    public String lookup(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(lookupKey);
            return Base64.getEncoder().encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Could not index account data", exception);
        }
    }

    private static final class MessageDigestHolder {
        static byte[] sha256(byte[] value) {
            try {
                return MessageDigest.getInstance("SHA-256").digest(value);
            } catch (GeneralSecurityException exception) {
                throw new IllegalStateException(exception);
            }
        }
    }
}
