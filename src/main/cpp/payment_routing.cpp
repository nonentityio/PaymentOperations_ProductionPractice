#include <cctype>
#include <jni.h>
#include <string>

using namespace std;

long HIGH_VALUE_MINOR = 10000000L;
long DIRECT_LIMIT_MINOR = 500000L;

string java_string_to_cpp(JNIEnv *env, jstring value) {
    if (value == nullptr) {
        return {};
    }
    const char *chars = env->GetStringUTFChars(value, nullptr);
    if (chars == nullptr) {
        return {};
    }
    string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
}

string lower_ascii(string value) {
    for (int i = 0; i < value.length(); i++) {
        value[i] = static_cast<char>(tolower(static_cast<unsigned char>(value[i])));
    }
    return value;
}

bool starts_with(const string &value, const string &prefix) {
    return value.rfind(prefix, 0) == 0;
}

string detect_provider(const string &requisite, const string &requested_provider) {
    string normalized_requisite = lower_ascii(requisite);
    string normalized_provider = lower_ascii(requested_provider);

    if (starts_with(normalized_requisite, "banka") || starts_with(normalized_requisite, "bank-a")) {
        return "provider-a";
    }
    if (starts_with(normalized_requisite, "bankb") || starts_with(normalized_requisite, "bank-b")) {
        return "provider-b";
    }
    if (normalized_provider == "provider-a" || normalized_provider == "provider-b" || normalized_provider == "demo-provider") {
        return normalized_provider;
    }
    return "demo-provider";
}

const char *processing_mode(long amount_minor, const string &currency) {
    if (amount_minor >= HIGH_VALUE_MINOR) {
        return "CONTROLLED";
    }
    if (currency == "KGS" && amount_minor <= DIRECT_LIMIT_MINOR) {
        return "FAST_PATH";
    }
    return "STANDARD";
}

extern "C" JNIEXPORT jstring JNICALL Java_org_eltech_infrastructure_routing_NativePaymentRouter_routeNative(
    JNIEnv *env,
    jclass clazz,
    jstring requisite_value,
    jstring requested_provider_value,
    jstring currency_value,
    jlong amount_minor
) {
    (void) clazz;

    string requisite = java_string_to_cpp(env, requisite_value);
    string requested_provider = java_string_to_cpp(env, requested_provider_value);
    string currency = java_string_to_cpp(env, currency_value);
    string provider = detect_provider(requisite, requested_provider);
    int priority = 5;
    if (amount_minor >= HIGH_VALUE_MINOR) {
        priority = 1;
    }

    string encoded = provider
        + "|"
        + to_string(priority)
        + "|"
        + processing_mode(amount_minor, currency);

    return env->NewStringUTF(encoded.c_str());
}
