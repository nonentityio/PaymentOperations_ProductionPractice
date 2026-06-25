#include <ctype.h>
#include <jni.h>
#include <stdint.h>
#include <string.h>

#define VALIDATION_OK 0
#define INVALID_REQUISITE 1
#define INVALID_PROVIDER 2
#define INVALID_CURRENCY 3
#define INVALID_AMOUNT 4

static int is_provider_char(unsigned char c) {
    return isalnum(c) || c == '-' || c == '_';
}

static int is_requisite_char(unsigned char c) {
    return isalnum(c) || c == '-' || c == ' ';
}


static int validate_provider_bytes(const unsigned char *provider, int length) {
    if (provider == NULL || length <= 0 || length > 64) {
        return INVALID_PROVIDER;
    }
    for (int i = 0; i < length; i++) {
        if (!is_provider_char(provider[i])) {
            return INVALID_PROVIDER;
        }
    }
    return VALIDATION_OK;
}

static int validate_currency_bytes(const unsigned char *currency, int offset, int length) {
    if (currency == NULL || length != 3) {
        return INVALID_CURRENCY;
    }
    for (int i = 0; i < 3; i++) {
        unsigned char c = currency[offset + i];
        if (c < 'A' || c > 'Z') {
            return INVALID_CURRENCY;
        }
    }
    return VALIDATION_OK;
}

static int validate_requisite_bytes(const unsigned char *requisite, int length) {
    int payload_chars = 0;

    if (requisite == NULL || length < 6 || length > 34) {
        return INVALID_REQUISITE;
    }

    for (int i = 0; i < length; i++) {
        unsigned char c = requisite[i];
        if (!is_requisite_char(c)) {
            return INVALID_REQUISITE;
        }
        if (isalnum(c)) {
            payload_chars++;
        }
    }

    return payload_chars >= 6 ? VALIDATION_OK : INVALID_REQUISITE;
}

static int validate_provider(const char *provider) {
    size_t length;
    if (provider == NULL) {
        return INVALID_PROVIDER;
    }
    length = strlen(provider);
    if (length == 0 || length > 64) {
        return INVALID_PROVIDER;
    }
    for (size_t i = 0; i < length; i++) {
        if (!is_provider_char((unsigned char) provider[i])) {
            return INVALID_PROVIDER;
        }
    }
    return VALIDATION_OK;
}

static int validate_currency(const char *currency) {
    if (currency == NULL || strlen(currency) != 3) {
        return INVALID_CURRENCY;
    }
    for (size_t i = 0; i < 3; i++) {
        unsigned char c = (unsigned char) currency[i];
        if (c < 'A' || c > 'Z') {
            return INVALID_CURRENCY;
        }
    }
    return VALIDATION_OK;
}

static int validate_requisite(const char *requisite) {
    size_t length;
    int payload_chars = 0;

    if (requisite == NULL) {
        return INVALID_REQUISITE;
    }

    length = strlen(requisite);
    if (length < 6 || length > 34) {
        return INVALID_REQUISITE;
    }

    for (size_t i = 0; i < length; i++) {
        unsigned char c = (unsigned char) requisite[i];
        if (!is_requisite_char(c)) {
            return INVALID_REQUISITE;
        }
        if (isalnum(c)) {
            payload_chars++;
        }
    }

    return payload_chars >= 6 ? VALIDATION_OK : INVALID_REQUISITE;
}

JNIEXPORT jint JNICALL Java_org_eltech_infrastructure_validation_NativePaymentValidator_validatePaymentNative(
    JNIEnv *env,
    jclass clazz,
    jstring requisite_value,
    jstring provider_value,
    jstring currency_value,
    jlong amount_minor
) {
    const char *requisite;
    const char *provider;
    const char *currency;
    int result;

    (void) clazz;

    if (amount_minor <= 0 || amount_minor > 1000000000000LL) {
        return INVALID_AMOUNT;
    }

    requisite = (*env)->GetStringUTFChars(env, requisite_value, 0);
    provider = (*env)->GetStringUTFChars(env, provider_value, 0);
    currency = (*env)->GetStringUTFChars(env, currency_value, 0);

    if (requisite == NULL || provider == NULL || currency == NULL) {
        if (requisite != NULL) {
            (*env)->ReleaseStringUTFChars(env, requisite_value, requisite);
        }
        if (provider != NULL) {
            (*env)->ReleaseStringUTFChars(env, provider_value, provider);
        }
        if (currency != NULL) {
            (*env)->ReleaseStringUTFChars(env, currency_value, currency);
        }
        return INVALID_REQUISITE;
    }

    result = validate_requisite(requisite);
    if (result == VALIDATION_OK) {
        result = validate_provider(provider);
    }
    if (result == VALIDATION_OK) {
        result = validate_currency(currency);
    }

    (*env)->ReleaseStringUTFChars(env, requisite_value, requisite);
    (*env)->ReleaseStringUTFChars(env, provider_value, provider);
    (*env)->ReleaseStringUTFChars(env, currency_value, currency);

    return result;
}


JNIEXPORT jint JNICALL Java_org_eltech_infrastructure_validation_NativePaymentValidator_countValidPackedBatchNative(
    JNIEnv *env,
    jclass clazz,
    jbyteArray requisite_values,
    jintArray requisite_offsets_values,
    jintArray requisite_lengths_values,
    jbyteArray provider_values,
    jintArray provider_offsets_values,
    jintArray provider_lengths_values,
    jbyteArray currency_values,
    jintArray currency_offsets_values,
    jintArray currency_lengths_values,
    jlongArray amount_minor_values,
    jint iterations
) {
    jbyte *requisites;
    jint *requisite_offsets;
    jint *requisite_lengths;
    jbyte *providers;
    jint *provider_offsets;
    jint *provider_lengths;
    jbyte *currencies;
    jint *currency_offsets;
    jint *currency_lengths;
    jlong *amounts;
    jsize count;
    jint accepted = 0;

    (void) clazz;

    if (iterations <= 0) {
        return 0;
    }

    count = (*env)->GetArrayLength(env, amount_minor_values);
    if (count <= 0) {
        return 0;
    }

    requisites = (*env)->GetByteArrayElements(env, requisite_values, NULL);
    requisite_offsets = (*env)->GetIntArrayElements(env, requisite_offsets_values, NULL);
    requisite_lengths = (*env)->GetIntArrayElements(env, requisite_lengths_values, NULL);
    providers = (*env)->GetByteArrayElements(env, provider_values, NULL);
    provider_offsets = (*env)->GetIntArrayElements(env, provider_offsets_values, NULL);
    provider_lengths = (*env)->GetIntArrayElements(env, provider_lengths_values, NULL);
    currencies = (*env)->GetByteArrayElements(env, currency_values, NULL);
    currency_offsets = (*env)->GetIntArrayElements(env, currency_offsets_values, NULL);
    currency_lengths = (*env)->GetIntArrayElements(env, currency_lengths_values, NULL);
    amounts = (*env)->GetLongArrayElements(env, amount_minor_values, NULL);

    if (requisites == NULL || requisite_offsets == NULL || requisite_lengths == NULL ||
        providers == NULL || provider_offsets == NULL || provider_lengths == NULL ||
        currencies == NULL || currency_offsets == NULL || currency_lengths == NULL || amounts == NULL) {
        if (requisites != NULL) (*env)->ReleaseByteArrayElements(env, requisite_values, requisites, JNI_ABORT);
        if (requisite_offsets != NULL) (*env)->ReleaseIntArrayElements(env, requisite_offsets_values, requisite_offsets, JNI_ABORT);
        if (requisite_lengths != NULL) (*env)->ReleaseIntArrayElements(env, requisite_lengths_values, requisite_lengths, JNI_ABORT);
        if (providers != NULL) (*env)->ReleaseByteArrayElements(env, provider_values, providers, JNI_ABORT);
        if (provider_offsets != NULL) (*env)->ReleaseIntArrayElements(env, provider_offsets_values, provider_offsets, JNI_ABORT);
        if (provider_lengths != NULL) (*env)->ReleaseIntArrayElements(env, provider_lengths_values, provider_lengths, JNI_ABORT);
        if (currencies != NULL) (*env)->ReleaseByteArrayElements(env, currency_values, currencies, JNI_ABORT);
        if (currency_offsets != NULL) (*env)->ReleaseIntArrayElements(env, currency_offsets_values, currency_offsets, JNI_ABORT);
        if (currency_lengths != NULL) (*env)->ReleaseIntArrayElements(env, currency_lengths_values, currency_lengths, JNI_ABORT);
        if (amounts != NULL) (*env)->ReleaseLongArrayElements(env, amount_minor_values, amounts, JNI_ABORT);
        return 0;
    }

    for (jint i = 0; i < iterations; i++) {
        jint index = i % count;
        int result;

        if (amounts[index] <= 0 || amounts[index] > 1000000000000LL) {
            continue;
        }

        result = validate_requisite_bytes(
            (const unsigned char *) requisites + requisite_offsets[index],
            requisite_lengths[index]
        );
        if (result == VALIDATION_OK) {
            result = validate_provider_bytes(
                (const unsigned char *) providers + provider_offsets[index],
                provider_lengths[index]
            );
        }
        if (result == VALIDATION_OK) {
            result = validate_currency_bytes(
                (const unsigned char *) currencies,
                currency_offsets[index],
                currency_lengths[index]
            );
        }
        if (result == VALIDATION_OK) {
            accepted++;
        }
    }

    (*env)->ReleaseByteArrayElements(env, requisite_values, requisites, JNI_ABORT);
    (*env)->ReleaseIntArrayElements(env, requisite_offsets_values, requisite_offsets, JNI_ABORT);
    (*env)->ReleaseIntArrayElements(env, requisite_lengths_values, requisite_lengths, JNI_ABORT);
    (*env)->ReleaseByteArrayElements(env, provider_values, providers, JNI_ABORT);
    (*env)->ReleaseIntArrayElements(env, provider_offsets_values, provider_offsets, JNI_ABORT);
    (*env)->ReleaseIntArrayElements(env, provider_lengths_values, provider_lengths, JNI_ABORT);
    (*env)->ReleaseByteArrayElements(env, currency_values, currencies, JNI_ABORT);
    (*env)->ReleaseIntArrayElements(env, currency_offsets_values, currency_offsets, JNI_ABORT);
    (*env)->ReleaseIntArrayElements(env, currency_lengths_values, currency_lengths, JNI_ABORT);
    (*env)->ReleaseLongArrayElements(env, amount_minor_values, amounts, JNI_ABORT);

    return accepted;
}
