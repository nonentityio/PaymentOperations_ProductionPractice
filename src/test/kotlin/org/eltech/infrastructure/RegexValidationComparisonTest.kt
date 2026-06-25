package org.eltech.infrastructure

import org.eltech.infrastructure.validation.NativePaymentValidator
import kotlin.system.measureNanoTime
import java.nio.charset.StandardCharsets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RegexValidationComparisonTest {
    @Test
    fun manualAndRegexValidationReturnSameResultForPaymentRequisites() {
        val samples = listOf(
            "996700333444" to true,
            "ELDIK2-996700333444" to true,
            "ACC 123456" to true,
            "12345" to false,
            "BAD@ACCOUNT" to false,
            "" to false,
            "12345678901234567890123456789012345" to false
        )

        samples.forEach { (requisite, expected) ->
            assertEquals(expected, manualRequisiteValid(requisite), "manual validator failed for $requisite")
            assertEquals(expected, precompiledRegexRequisiteValid(requisite), "regex validator failed for $requisite")
        }
    }

    @Test
    fun manualValidationIsFasterThanRegexCompiledForEveryPayment() {
        val requisites = arrayOf(
            "996700333444",
            "ELDIK2-996700333444",
            "ACC 123456",
            "996555777888",
            "BAD@ACCOUNT",
            "12345"
        )
        val iterations = 250_000

        repeat(3) {
            runManual(requisites, 20_000)
            runRegexCompiledEveryTime(requisites, 20_000)
            runPrecompiledRegex(requisites, 20_000)
        }

        val manualNs = measureNanoTime { runManual(requisites, iterations) }
        val precompiledRegexNs = measureNanoTime { runPrecompiledRegex(requisites, iterations) }
        val compiledEveryTimeRegexNs = measureNanoTime { runRegexCompiledEveryTime(requisites, iterations) }

        println("manual validation: ${manualNs / 1_000_000.0} ms")
        println("precompiled regex validation: ${precompiledRegexNs / 1_000_000.0} ms")
        println("regex compiled per payment: ${compiledEveryTimeRegexNs / 1_000_000.0} ms")
        println("compile-per-payment regex / manual ratio: ${compiledEveryTimeRegexNs.toDouble() / manualNs}")
        println("precompiled regex / manual ratio: ${precompiledRegexNs.toDouble() / manualNs}")

        assertTrue(
            compiledEveryTimeRegexNs > manualNs,
            "Regex compiled on every payment should have higher overhead than simple manual validation"
        )
    }



    @Test
    fun nativeCValidatorKotlinValidatorAndRegexValidatorPerformanceAreCompared() {
        val cases = arrayOf(
            ValidationCase("996700333444", "eldik2-test-bank", "KGS", 10_000, true),
            ValidationCase("ELDIK2-996700333444", "eldik2-test-bank", "KGS", 25_500, true),
            ValidationCase("ACC 123456", "merchant-network", "USD", 3_000, true),
            ValidationCase("BAD@ACCOUNT", "eldik2-test-bank", "KGS", 10_000, false),
            ValidationCase("12345", "eldik2-test-bank", "KGS", 10_000, false),
            ValidationCase("996700333444", "bad provider!", "KGS", 10_000, false),
            ValidationCase("996700333444", "eldik2-test-bank", "KG", 10_000, false),
            ValidationCase("996700333444", "eldik2-test-bank", "KGS", -1, false)
        )
        val iterations = 250_000

        cases.forEach { case ->
            assertEquals(case.valid, kotlinManualPaymentValid(case), "manual validator mismatch for $case")
            assertEquals(case.valid, kotlinRegexPaymentValid(case), "regex validator mismatch for $case")
            assertEquals(case.valid, NativePaymentValidator.validate(case.requisite, case.provider, case.currency, case.amountMinor).valid(), "native C validator mismatch for $case")
        }

        repeat(3) {
            runKotlinManual(cases, 20_000)
            runKotlinRegex(cases, 20_000)
            runNativeC(cases, 20_000)
            runNativeCPackedBatch(pack(cases), 20_000)
        }

        val packed = pack(cases)
        val kotlinManualNs = measureNanoTime { runKotlinManual(cases, iterations) }
        val kotlinRegexNs = measureNanoTime { runKotlinRegex(cases, iterations) }
        val nativeCNs = measureNanoTime { runNativeC(cases, iterations) }
        val nativeCBatchNs = measureNanoTime { runNativeCPackedBatch(packed, iterations) }

        println("kotlin manual payment validation: ${kotlinManualNs / 1_000_000.0} ms")
        println("kotlin precompiled regex payment validation: ${kotlinRegexNs / 1_000_000.0} ms")
        println("native C JNI single payment validation: ${nativeCNs / 1_000_000.0} ms")
        println("native C JNI packed batch validation: ${nativeCBatchNs / 1_000_000.0} ms")
        println("regex / kotlin manual ratio: ${kotlinRegexNs.toDouble() / kotlinManualNs}")
        println("native C JNI single / kotlin manual ratio: ${nativeCNs.toDouble() / kotlinManualNs}")
        println("native C JNI packed batch / kotlin manual ratio: ${nativeCBatchNs.toDouble() / kotlinManualNs}")
        println("native C JNI single / packed batch ratio: ${nativeCNs.toDouble() / nativeCBatchNs}")
        println("regex / native C JNI packed batch ratio: ${kotlinRegexNs.toDouble() / nativeCBatchNs}")

        assertEquals(runKotlinManual(cases, iterations), runKotlinRegex(cases, iterations))
        assertEquals(runKotlinManual(cases, iterations), runNativeC(cases, iterations))
        assertEquals(runKotlinManual(cases, iterations), runNativeCPackedBatch(packed, iterations))
    }

    private fun runManual(values: Array<String>, iterations: Int): Int {
        var accepted = 0
        repeat(iterations) { index ->
            if (manualRequisiteValid(values[index % values.size])) accepted++
        }
        return accepted
    }

    private fun runPrecompiledRegex(values: Array<String>, iterations: Int): Int {
        var accepted = 0
        repeat(iterations) { index ->
            if (precompiledRegexRequisiteValid(values[index % values.size])) accepted++
        }
        return accepted
    }

    private fun runRegexCompiledEveryTime(values: Array<String>, iterations: Int): Int {
        var accepted = 0
        repeat(iterations) { index ->
            if (regexCompiledEveryTimeRequisiteValid(values[index % values.size])) accepted++
        }
        return accepted
    }

    private fun manualRequisiteValid(requisite: String): Boolean {
        if (requisite.length !in 6..34) return false

        var payloadChars = 0
        for (char in requisite) {
            val valid = char.isLetterOrDigit() || char == '-' || char == ' '
            if (!valid) return false
            if (char.isLetterOrDigit()) payloadChars++
        }

        return payloadChars >= 6
    }

    private fun precompiledRegexRequisiteValid(requisite: String): Boolean {
        if (!requisitePattern.matches(requisite)) return false
        return requisite.count { it.isLetterOrDigit() } >= 6
    }

    private fun regexCompiledEveryTimeRequisiteValid(requisite: String): Boolean {
        val pattern = Regex("^[A-Za-z0-9 -]{6,34}$")
        if (!pattern.matches(requisite)) return false
        return requisite.count { it.isLetterOrDigit() } >= 6
    }



    private fun runKotlinManual(values: Array<ValidationCase>, iterations: Int): Int {
        var accepted = 0
        repeat(iterations) { index ->
            if (kotlinManualPaymentValid(values[index % values.size])) accepted++
        }
        return accepted
    }

    private fun runKotlinRegex(values: Array<ValidationCase>, iterations: Int): Int {
        var accepted = 0
        repeat(iterations) { index ->
            if (kotlinRegexPaymentValid(values[index % values.size])) accepted++
        }
        return accepted
    }

    private fun runNativeC(values: Array<ValidationCase>, iterations: Int): Int {
        var accepted = 0
        repeat(iterations) { index ->
            val case = values[index % values.size]
            if (NativePaymentValidator.validate(case.requisite, case.provider, case.currency, case.amountMinor).valid()) accepted++
        }
        return accepted
    }


    private fun runNativeCPackedBatch(values: PackedValidationCases, iterations: Int): Int {
        return NativePaymentValidator.countValidPackedBatch(
            values.requisites,
            values.requisiteOffsets,
            values.requisiteLengths,
            values.providers,
            values.providerOffsets,
            values.providerLengths,
            values.currencies,
            values.currencyOffsets,
            values.currencyLengths,
            values.amountsMinor,
            iterations
        )
    }

    private fun pack(values: Array<ValidationCase>): PackedValidationCases {
        val requisiteBytes = mutableListOf<Byte>()
        val requisiteOffsets = IntArray(values.size)
        val requisiteLengths = IntArray(values.size)
        val providerBytes = mutableListOf<Byte>()
        val providerOffsets = IntArray(values.size)
        val providerLengths = IntArray(values.size)
        val currencyBytes = mutableListOf<Byte>()
        val currencyOffsets = IntArray(values.size)
        val currencyLengths = IntArray(values.size)
        val amounts = LongArray(values.size)

        values.forEachIndexed { index, value ->
            val requisite = value.requisite.toByteArray(StandardCharsets.US_ASCII)
            requisiteOffsets[index] = requisiteBytes.size
            requisiteLengths[index] = requisite.size
            requisite.forEach(requisiteBytes::add)

            val provider = value.provider.toByteArray(StandardCharsets.US_ASCII)
            providerOffsets[index] = providerBytes.size
            providerLengths[index] = provider.size
            provider.forEach(providerBytes::add)

            val currency = value.currency.toByteArray(StandardCharsets.US_ASCII)
            currencyOffsets[index] = currencyBytes.size
            currencyLengths[index] = currency.size
            currency.forEach(currencyBytes::add)

            amounts[index] = value.amountMinor
        }

        return PackedValidationCases(
            requisites = requisiteBytes.toByteArray(),
            requisiteOffsets = requisiteOffsets,
            requisiteLengths = requisiteLengths,
            providers = providerBytes.toByteArray(),
            providerOffsets = providerOffsets,
            providerLengths = providerLengths,
            currencies = currencyBytes.toByteArray(),
            currencyOffsets = currencyOffsets,
            currencyLengths = currencyLengths,
            amountsMinor = amounts
        )
    }

    private fun kotlinManualPaymentValid(case: ValidationCase): Boolean {
        if (case.amountMinor <= 0 || case.amountMinor > 1_000_000_000_000L) return false
        if (!manualRequisiteValid(case.requisite)) return false
        if (case.provider.isEmpty() || case.provider.length > 64) return false
        for (char in case.provider) {
            if (!(char.isLetterOrDigit() || char == '-' || char == '_')) return false
        }
        if (case.currency.length != 3) return false
        for (char in case.currency) {
            if (char !in 'A'..'Z') return false
        }
        return true
    }

    private fun kotlinRegexPaymentValid(case: ValidationCase): Boolean {
        if (case.amountMinor <= 0 || case.amountMinor > 1_000_000_000_000L) return false
        if (!precompiledRegexRequisiteValid(case.requisite)) return false
        if (!providerPattern.matches(case.provider)) return false
        if (!currencyPattern.matches(case.currency)) return false
        return true
    }

    private data class ValidationCase(
        val requisite: String,
        val provider: String,
        val currency: String,
        val amountMinor: Long,
        val valid: Boolean
    )

    private data class PackedValidationCases(
        val requisites: ByteArray,
        val requisiteOffsets: IntArray,
        val requisiteLengths: IntArray,
        val providers: ByteArray,
        val providerOffsets: IntArray,
        val providerLengths: IntArray,
        val currencies: ByteArray,
        val currencyOffsets: IntArray,
        val currencyLengths: IntArray,
        val amountsMinor: LongArray
    )

    private companion object {
        val requisitePattern = Regex("^[A-Za-z0-9 -]{6,34}$")
        val providerPattern = Regex("^[A-Za-z0-9_-]{1,64}$")
        val currencyPattern = Regex("^[A-Z]{3}$")
    }
}
