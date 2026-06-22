package org.eltech.infrastructure

import org.eltech.infrastructure.security.PaymentRequestFingerprint
import org.eltech.infrastructure.validation.PaymentIds
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PaymentInfrastructureTest {
    @Test
    fun fingerprintIsStableForSamePaymentPayload() {
        val first = PaymentRequestFingerprint.hash(
            "eldik-test-bank",
            "10.00",
            "KGS",
            "ELDIK2-996700333444",
            "eldik2-test-bank",
            "TRANSFER"
        )
        val second = PaymentRequestFingerprint.hash(
            "eldik-test-bank",
            "10.00",
            "KGS",
            "ELDIK2-996700333444",
            "eldik2-test-bank",
            "TRANSFER"
        )

        assertEquals(first, second)
        assertEquals(64, first.length)
        assertTrue(first.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun fingerprintChangesWhenCategoryChanges() {
        val transfer = PaymentRequestFingerprint.hash("bank", "1.00", "KGS", "REQ", "provider", "TRANSFER")
        val utility = PaymentRequestFingerprint.hash("bank", "1.00", "KGS", "REQ", "provider", "UTILITY")

        assertNotEquals(transfer, utility)
    }

    @Test
    fun paymentIdParserReturnsNullForInvalidValues() {
        val id = UUID.randomUUID()

        assertEquals(id, PaymentIds.parseOrNull(id.toString()))
        assertNull(PaymentIds.parseOrNull("not-uuid"))
        assertNull(PaymentIds.parseOrNull(""))
    }
}
