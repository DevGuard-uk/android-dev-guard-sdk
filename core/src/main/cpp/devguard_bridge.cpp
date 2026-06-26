#include <jni.h>
#include "devguard_core.h"

extern "C" JNIEXPORT jstring JNICALL
Java_io_devguard_core_NativeBridge_generateSignatureNative(
        JNIEnv* env,
        jobject,
        jstring projectId,
        jlong timestamp) {
    const char* project_id_c = env->GetStringUTFChars(projectId, 0);
    char output[65];
    dg_x9(project_id_c, (long long)timestamp, output);
    env->ReleaseStringUTFChars(projectId, project_id_c);
    return env->NewStringUTF(output);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_io_devguard_core_NativeBridge_verifyResponseNative(
        JNIEnv* env,
        jobject,
        jstring responseBody,
        jstring signature) {
    const char* response_body_c = env->GetStringUTFChars(responseBody, 0);
    const char* signature_c = env->GetStringUTFChars(signature, 0);
    int result = dg_v2(response_body_c, signature_c);
    env->ReleaseStringUTFChars(responseBody, response_body_c);
    env->ReleaseStringUTFChars(signature, signature_c);
    return result == 1 ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_io_devguard_core_NativeBridge_secureSaveTokenNative(
        JNIEnv* env,
        jobject,
        jstring token) {
    const char* token_c = env->GetStringUTFChars(token, 0);
    char output[512];
    dg_s3(token_c, output);
    env->ReleaseStringUTFChars(token, token_c);
    return env->NewStringUTF(output);
}

extern "C" JNIEXPORT jstring JNICALL
Java_io_devguard_core_NativeBridge_secureGetTokenNative(
        JNIEnv* env,
        jobject,
        jstring scrambled) {
    const char* scrambled_c = env->GetStringUTFChars(scrambled, 0);
    char output[512];
    dg_g4(scrambled_c, output);
    env->ReleaseStringUTFChars(scrambled, scrambled_c);
    return env->NewStringUTF(output);
}

extern "C" JNIEXPORT jstring JNICALL
Java_io_devguard_core_NativeBridge_hashSha256Native(
        JNIEnv* env,
        jobject,
        jstring input) {
    const char* input_c = env->GetStringUTFChars(input, 0);
    char output[65];
    dg_h5(input_c, output);
    env->ReleaseStringUTFChars(input, input_c);
    return env->NewStringUTF(output);
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_io_devguard_core_NativeBridge_xorTransformNative(
        JNIEnv* env,
        jobject,
        jbyteArray input,
        jbyteArray key) {
    jsize input_len = env->GetArrayLength(input);
    jsize key_len = env->GetArrayLength(key);
    jbyte* input_bytes = env->GetByteArrayElements(input, nullptr);
    jbyte* key_bytes = env->GetByteArrayElements(key, nullptr);

    char* input_c = reinterpret_cast<char*>(input_bytes);
    char* key_c = reinterpret_cast<char*>(key_bytes);
    char* output = new char[input_len + 1];
    dg_x6(input_c, (size_t)input_len, key_c, (size_t)key_len, output);

    jbyteArray result = env->NewByteArray(input_len);
    env->SetByteArrayRegion(result, 0, input_len, reinterpret_cast<jbyte*>(output));

    env->ReleaseByteArrayElements(input, input_bytes, JNI_ABORT);
    env->ReleaseByteArrayElements(key, key_bytes, JNI_ABORT);
    delete[] output;
    return result;
}

extern "C" JNIEXPORT jint JNICALL
Java_io_devguard_core_NativeBridge_evaluatePolicyNative(
        JNIEnv* env,
        jobject,
        jint blockEmulators,
        jint isPhysical,
        jint isCompromised) {
    return dg_e1(blockEmulators, isPhysical, isCompromised);
}

extern "C" JNIEXPORT jint JNICALL
Java_io_devguard_core_NativeBridge_getTotalRamMbNative(JNIEnv*, jobject) {
    return dg_r8();
}

extern "C" JNIEXPORT jstring JNICALL
Java_io_devguard_core_NativeBridge_defaultStatusUrlNative(JNIEnv* env, jobject) {
    char output[128];
    dg_u1(output);
    return env->NewStringUTF(output);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_io_devguard_core_NativeBridge_isAllowedStatusUrlNative(
        JNIEnv* env,
        jobject,
        jstring url) {
    const char* url_c = env->GetStringUTFChars(url, 0);
    int result = dg_u2(url_c);
    env->ReleaseStringUTFChars(url, url_c);
    return result == 1 ? JNI_TRUE : JNI_FALSE;
}
