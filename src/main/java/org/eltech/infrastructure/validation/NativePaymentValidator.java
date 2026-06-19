package org.eltech.infrastructure.validation;

import java.nio.file.Files;
import java.nio.file.Path;

public final class NativePaymentValidator {
    private static final int OK = 0;
    private static final int INVALID_REQUISITE = 1;
    private static final int INVALID_PROVIDER = 2;
    private static final int INVALID_CURRENCY = 3;
    private static final int INVALID_AMOUNT = 4;

    static {
        loadNativeLibrary();
    }

    private NativePaymentValidator() {
    }

    public static ValidationResult validate(String requisite, String providerId, String currency, long amountMinor) {
        int code = validatePaymentNative(requisite, providerId, currency, amountMinor);
        return new ValidationResult(code == OK, messageFor(code), "native-c");
    }

    public static boolean isNativeAvailable() {
        return true;
    }

    private static native int validatePaymentNative(
        String requisite,
        String providerId,
        String currency,
        long amountMinor
    );

    private static void loadNativeLibrary() {
        String explicitPath = System.getenv("PAYMENT_NATIVE_VALIDATOR_PATH");
        if (explicitPath != null && !explicitPath.isBlank()) {
            try {
                System.load(explicitPath);
                return;
            } catch (UnsatisfiedLinkError ignored) {
                throw new IllegalStateException("cannot load native validator from PAYMENT_NATIVE_VALIDATOR_PATH", ignored);
            }
        }

        String mappedName = System.mapLibraryName("payment_validation");
        Path buildNativePath = Path.of("build", "native", mappedName).toAbsolutePath();
        if (Files.isRegularFile(buildNativePath)) {
            try {
                System.load(buildNativePath.toString());
                return;
            } catch (UnsatisfiedLinkError ignored) {
                throw new IllegalStateException("cannot load native validator from " + buildNativePath, ignored);
            }
        }

        try {
            System.loadLibrary("payment_validation");
        } catch (UnsatisfiedLinkError ignored) {
            throw new IllegalStateException("native validator library payment_validation is required", ignored);
        }
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
