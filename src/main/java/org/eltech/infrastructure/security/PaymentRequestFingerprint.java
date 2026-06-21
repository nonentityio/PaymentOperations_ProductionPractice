package org.eltech.infrastructure.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class PaymentRequestFingerprint {
    private static final char[] HEX = "0123456789abcdef".toCharArray();
    private static final ThreadLocal<MessageDigest> SHA_256 = ThreadLocal.withInitial(() -> {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    });

    private PaymentRequestFingerprint() {
    }

    public static String hash(
        String clientId,
        String amount,
        String currency,
        String requisite,
        String providerId
    ) {
        return hash(clientId, amount, currency, requisite, providerId, "TRANSFER");
    }

    public static String hash(
        String clientId,
        String amount,
        String currency,
        String requisite,
        String providerId,
        String serviceCategory
    ) {
        MessageDigest digest = SHA_256.get();
        digest.reset();
        digest.update(clientId.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) '|');
        digest.update(amount.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) '|');
        digest.update(currency.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) '|');
        digest.update(requisite.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) '|');
        digest.update(providerId.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) '|');
        digest.update(serviceCategory.getBytes(StandardCharsets.UTF_8));
        return toHex(digest.digest());
    }

    private static String toHex(byte[] bytes) {
        char[] result = new char[bytes.length * 2];
        for (int i = 0, j = 0; i < bytes.length; i++) {
            int value = bytes[i] & 0xff;
            result[j++] = HEX[value >>> 4];
            result[j++] = HEX[value & 0x0f];
        }
        return new String(result);
    }
}
