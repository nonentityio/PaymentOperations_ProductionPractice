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
