package org.eltech.infrastructure.routing;

import java.nio.file.Files;
import java.nio.file.Path;

public final class NativePaymentRouter {
    static {
        loadNativeLibrary();
    }

    private NativePaymentRouter() {
    }

    public static RoutingDecision route(String requisite, String requestedProviderId, String currency, long amountMinor) {
        String encoded = routeNative(requisite, requestedProviderId, currency, amountMinor);
        String[] parts = encoded.split("\\|", -1);
        if (parts.length != 3) {
            throw new IllegalStateException("native router returned invalid route: " + encoded);
        }
        return new RoutingDecision(parts[0], parsePriority(parts[1]), parts[2], "native-cpp");
    }

    public static boolean isNativeAvailable() {
        return true;
    }

    private static native String routeNative(
        String requisite,
        String requestedProviderId,
        String currency,
        long amountMinor
    );

    private static void loadNativeLibrary() {
        String explicitPath = System.getenv("PAYMENT_NATIVE_ROUTER_PATH");
        if (explicitPath != null && !explicitPath.isBlank()) {
            try {
                System.load(explicitPath);
                return;
            } catch (UnsatisfiedLinkError ignored) {
                throw new IllegalStateException("cannot load native router from PAYMENT_NATIVE_ROUTER_PATH", ignored);
            }
        }

        String mappedName = System.mapLibraryName("payment_routing");
        Path buildNativePath = Path.of("build", "native", mappedName).toAbsolutePath();
        if (Files.isRegularFile(buildNativePath)) {
            try {
                System.load(buildNativePath.toString());
                return;
            } catch (UnsatisfiedLinkError ignored) {
                throw new IllegalStateException("cannot load native router from " + buildNativePath, ignored);
            }
        }

        try {
            System.loadLibrary("payment_routing");
        } catch (UnsatisfiedLinkError ignored) {
            throw new IllegalStateException("native router library payment_routing is required", ignored);
        }
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
