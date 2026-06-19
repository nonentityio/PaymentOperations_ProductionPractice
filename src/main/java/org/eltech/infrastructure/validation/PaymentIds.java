package org.eltech.infrastructure.validation;

import java.util.UUID;

public final class PaymentIds {
    private PaymentIds() {
    }

    public static UUID parseOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}

