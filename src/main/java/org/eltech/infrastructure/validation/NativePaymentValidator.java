package org.eltech.infrastructure.validation;

import java.nio.file.Files;
import java.nio.file.Path;

public final class NativePaymentValidator {
    private static final int OK = 0;
    private static final int INVALID_REQUISITE = 1;
    private static final int INVALID_PROVIDER = 2;
    private static final int INVALID_CURRENCY = 3;
    private static final int INVALID_AMOUNT = 4;
    private static final boolean NATIVE_AVAILABLE = loadNativeLibrary();

    private NativePaymentValidator() {
    }

    public static ValidationResult validate(String requisite, String providerId, String currency, long amountMinor) {
        int code;
        String engine;

        if (NATIVE_AVAILABLE) {
            code = validatePaymentNative(requisite, providerId, currency, amountMinor);
            engine = "native-c";
        } else {
            code = validateOnJvm(requisite, providerId, currency, amountMinor);
            engine = "jvm-fallback";
        }

        return new ValidationResult(code == OK, messageFor(code), engine);
    }

    public static boolean isNativeAvailable() {
        return NATIVE_AVAILABLE;
    }

    private static native int validatePaymentNative(
        String requisite,
        String providerId,
        String currency,
        long amountMinor
    );

    private static boolean loadNativeLibrary() {
        String explicitPath = System.getenv("PAYMENT_NATIVE_VALIDATOR_PATH");
        if (explicitPath != null && !explicitPath.isBlank()) {
            try {
                System.load(explicitPath);
                return true;
            } catch (UnsatisfiedLinkError ignored) {
                return false;
            }
        }

        String mappedName = System.mapLibraryName("payment_validation");
        Path buildNativePath = Path.of("build", "native", mappedName).toAbsolutePath();
        if (Files.isRegularFile(buildNativePath)) {
            try {
                System.load(buildNativePath.toString());
                return true;
            } catch (UnsatisfiedLinkError ignored) {
                return false;
            }
        }

        try {
            System.loadLibrary("payment_validation");
            return true;
        } catch (UnsatisfiedLinkError ignored) {
            return false;
        }
    }

    private static int validateOnJvm(String requisite, String providerId, String currency, long amountMinor) {
        if (amountMinor <= 0 || amountMinor > 1_000_000_000_000L) {
            return INVALID_AMOUNT;
        }
        if (!validRequisite(requisite)) {
            return INVALID_REQUISITE;
        }
        if (!validProvider(providerId)) {
            return INVALID_PROVIDER;
        }
        if (!validCurrency(currency)) {
            return INVALID_CURRENCY;
        }
        return OK;
    }

    private static boolean validRequisite(String value) {
        if (value == null || value.length() < 6 || value.length() > 34) {
            return false;
        }
        int payloadChars = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!(Character.isLetterOrDigit(c) || c == '-' || c == ' ')) {
                return false;
            }
            if (Character.isLetterOrDigit(c)) {
                payloadChars++;
            }
        }
        return payloadChars >= 6;
    }

    private static boolean validProvider(String value) {
        if (value == null || value.isBlank() || value.length() > 64) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!(Character.isLetterOrDigit(c) || c == '-' || c == '_')) {
                return false;
            }
        }
        return true;
    }

    private static boolean validCurrency(String value) {
        if (value == null || value.length() != 3) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < 'A' || c > 'Z') {
                return false;
            }
        }
        return true;
    }

    private static String messageFor(int code) {
        if (code == OK) {
            return "payment validation passed";
        }
        if (code == INVALID_REQUISITE) {
            return "invalid payment requisite";
        }
        if (code == INVALID_PROVIDER) {
            return "invalid provider id";
        }
        if (code == INVALID_CURRENCY) {
            return "invalid currency code";
        }
        if (code == INVALID_AMOUNT) {
            return "invalid payment amount";
        }
        return "payment validation failed";
    }

    public record ValidationResult(boolean valid, String message, String engine) {
    }
}
