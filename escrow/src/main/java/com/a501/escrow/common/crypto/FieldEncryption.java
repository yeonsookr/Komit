package com.a501.escrow.common.crypto;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import com.a501.escrow.config.SecurityProperties;
import org.springframework.stereotype.Component;

@Component
public class FieldEncryption {

    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private final SecurityProperties properties;
    private final SecretKeySpec key;
    private final SecureRandom secureRandom = new SecureRandom();

    public FieldEncryption(SecurityProperties properties) {
        this.properties = properties;
        byte[] keyBytes = Base64.getDecoder().decode(properties.encryptionKeyBase64());
        if (keyBytes.length != 32) {
            throw new IllegalArgumentException("FIELD_ENCRYPTION_KEY_BASE64는 32바이트 AES-256 키여야 합니다.");
        }
        this.key = new SecretKeySpec(keyBytes, "AES");
    }

    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            ByteBuffer envelope = ByteBuffer.allocate(iv.length + ciphertext.length);
            envelope.put(iv).put(ciphertext);
            return "v" + properties.encryptionKeyVersion() + ":"
                    + Base64.getEncoder().encodeToString(envelope.array());
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("민감정보를 암호화할 수 없습니다.", exception);
        }
    }

    public String decrypt(String envelope) {
        String prefix = "v" + properties.encryptionKeyVersion() + ":";
        if (!envelope.startsWith(prefix)) {
            throw new IllegalArgumentException("지원하지 않는 암호화 키 버전입니다.");
        }
        byte[] payload = Base64.getDecoder().decode(envelope.substring(prefix.length()));
        if (payload.length <= IV_LENGTH) {
            throw new IllegalArgumentException("암호화 봉투 형식이 유효하지 않습니다.");
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            byte[] ciphertext = new byte[payload.length - IV_LENGTH];
            System.arraycopy(payload, 0, iv, 0, IV_LENGTH);
            System.arraycopy(payload, IV_LENGTH, ciphertext, 0, ciphertext.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException exception) {
            throw new IllegalArgumentException("암호화 봉투를 복호화할 수 없습니다.", exception);
        }
    }
}
