package org.eltech.infrastructure

import org.eltech.infrastructure.validation.ServiceRequisiteValidator
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ServiceRequisiteValidatorTest {
    @Test
    fun utilityElectricityAcceptsOnlyElectricityAccountFormat() {
        assertTrue(ServiceRequisiteValidator.validate("UTILITY", "utility.electricity", "EL-12345678", BigDecimal("250.00")).valid)
        assertFalse(ServiceRequisiteValidator.validate("UTILITY", "utility.electricity", "WATER-12345678", BigDecimal("250.00")).valid)
    }

    @Test
    fun utilityServicesUseDifferentRequisiteFormatsInsideSameCategory() {
        assertTrue(ServiceRequisiteValidator.validate("UTILITY", "utility.water", "WATER-12345678", BigDecimal("180.00")).valid)
        assertTrue(ServiceRequisiteValidator.validate("UTILITY", "utility.gas", "GAS-12345678", BigDecimal("180.00")).valid)
        assertTrue(ServiceRequisiteValidator.validate("UTILITY", "internet.home", "NET-12345678", BigDecimal("650.00")).valid)

        assertFalse(ServiceRequisiteValidator.validate("UTILITY", "utility.water", "EL-12345678", BigDecimal("180.00")).valid)
        assertFalse(ServiceRequisiteValidator.validate("UTILITY", "internet.home", "GAS-12345678", BigDecimal("650.00")).valid)
    }

    @Test
    fun cardServiceRequiresCardNumber() {
        assertTrue(ServiceRequisiteValidator.validate("CARD_PAYMENT", "card.repayment", "4111 1111 1111 1111", BigDecimal("1000.00")).valid)
        assertTrue(ServiceRequisiteValidator.validate("CARD_PAYMENT", "card.repayment", "4111111111111111", BigDecimal("1000.00")).valid)
        assertFalse(ServiceRequisiteValidator.validate("CARD_PAYMENT", "card.repayment", "EL-12345678", BigDecimal("1000.00")).valid)
    }

    @Test
    fun topupServicesHaveAmountLimit() {
        assertTrue(ServiceRequisiteValidator.validate("MOBILE_TOPUP", "mobile.operator", "996700333444", BigDecimal("100000.00")).valid)
        assertTrue(ServiceRequisiteValidator.validate("MOBILE_TOPUP", "mobile.plus", "996555777888", BigDecimal("100000.00")).valid)
        assertTrue(ServiceRequisiteValidator.validate("MOBILE_TOPUP", "mobile.lite", "996222333444", BigDecimal("100000.00")).valid)
        assertFalse(ServiceRequisiteValidator.validate("MOBILE_TOPUP", "mobile.operator", "996700333444", BigDecimal("100000.01")).valid)
        assertFalse(ServiceRequisiteValidator.validate("WALLET", "wallet.topup", "WAL-ABC12345", BigDecimal("100000.01")).valid)
    }

    @Test
    fun serviceIdMustBelongToRequestedCategory() {
        val result = ServiceRequisiteValidator.validate("UTILITY", "card.repayment", "4111 1111 1111 1111", BigDecimal("1000.00"))

        assertFalse(result.valid)
        assertEquals("serviceId card.repayment does not match category UTILITY", result.message)
    }

    @Test
    fun unknownServiceIdIsRejected() {
        val result = ServiceRequisiteValidator.validate("UTILITY", "utility.unknown", "EL-12345678", BigDecimal("250.00"))

        assertFalse(result.valid)
        assertEquals("unknown serviceId: utility.unknown", result.message)
    }
}
