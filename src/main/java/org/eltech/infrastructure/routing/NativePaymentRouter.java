package org.eltech.infrastructure.routing;

import java.nio.file.Files;
import java.nio.file.Path;

public final class NativePaymentRouter {
    private static final boolean NATIVE_AVAILABLE = loadNativeLibrary();

    private NativePaymentRouter() {
    }

    public static RoutingDecision route(String requisite, String requestedProviderId, String currency, long amountMinor) {
        if (!NATIVE_AVAILABLE) {
            return routeOnJvm(requisite, requestedProviderId, currency, amountMinor);
        }

        String encoded = routeNative(requisite, requestedProviderId, currency, amountMinor);
        String[] parts = encoded.split("\\|", -1);
        if (parts.length != 3) {
            return routeOnJvm(requisite, requestedProviderId, currency, amountMinor);
        }
        return new RoutingDecision(parts[0], parsePriority(parts[1]), parts[2], "native-cpp");
    }

    public static boolean isNativeAvailable() {
        return NATIVE_AVAILABLE;
    }

    private static native String routeNative(
        String requisite,
        String requestedProviderId,
        String currency,
        long amountMinor
    );

    private static boolean loadNativeLibrary() {
        String explicitPath = System.getenv("PAYMENT_NATIVE_ROUTER_PATH");
        if (explicitPath != null && !explicitPath.isBlank()) {
            try {
                System.load(explicitPath);
                return true;
            } catch (UnsatisfiedLinkError ignored) {
                return false;
            }
        }

        String mappedName = System.mapLibraryName("payment_routing");
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
            System.loadLibrary("payment_routing");
            return true;
        } catch (UnsatisfiedLinkError ignored) {
            return false;
        }
    }

    private static RoutingDecision routeOnJvm(
        String requisite,
        String requestedProviderId,
        String currency,
        long amountMinor
    ) {
        String providerId = detectProvider(requisite, requestedProviderId);
        int priority = 5;
        if (amountMinor >= 10_000_000L) {
            priority = 1;
        }

        String mode = "STANDARD";
        if (amountMinor >= 10_000_000L) {
            mode = "CONTROLLED";
        } else if ("KGS".equals(currency) && amountMinor <= 500_000L) {
            mode = "FAST_PATH";
        }

        return new RoutingDecision(providerId, priority, mode, "jvm-fallback");
    }

    private static String detectProvider(String requisite, String requestedProviderId) {
        String normalizedRequisite = requisite == null ? "" : requisite.toLowerCase();
        String normalizedProvider = requestedProviderId == null ? "" : requestedProviderId.toLowerCase();

        if (normalizedRequisite.startsWith("banka") || normalizedRequisite.startsWith("bank-a")) {
            return "provider-a";
        }
        if (normalizedRequisite.startsWith("bankb") || normalizedRequisite.startsWith("bank-b")) {
            return "provider-b";
        }
        if ("provider-a".equals(normalizedProvider)) {
            return normalizedProvider;
        }
        if ("provider-b".equals(normalizedProvider)) {
            return normalizedProvider;
        }
        if ("demo-provider".equals(normalizedProvider)) {
            return normalizedProvider;
        }
        return "demo-provider";
    }

    private static int parsePriority(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return 5;
        }
    }

    public record RoutingDecision(String providerId, int priority, String processingMode, String engine) {
    }
}
