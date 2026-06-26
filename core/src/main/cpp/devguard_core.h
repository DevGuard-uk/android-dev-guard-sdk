#ifndef DEVGUARD_CORE_H
#define DEVGUARD_CORE_H

#include <stdint.h>
#include <stddef.h>

#if _WIN32
#define DEVGUARD_EXPORT __declspec(dllexport)
#else
#define DEVGUARD_EXPORT __attribute__((visibility("default"))) __attribute__((used))
#endif

DEVGUARD_EXPORT void dg_x9(const char* project_id, long long timestamp, char* output);
DEVGUARD_EXPORT int dg_v2(const char* response_body, const char* signature);
DEVGUARD_EXPORT void dg_s3(const char* token, char* output);
DEVGUARD_EXPORT void dg_g4(const char* scrambled, char* output);
DEVGUARD_EXPORT void dg_h5(const char* input, char* output);
DEVGUARD_EXPORT void dg_x6(
    const char* input,
    size_t input_len,
    const char* key,
    size_t key_len,
    char* output
);
DEVGUARD_EXPORT void dg_d7(const char* passcode, const char* salt, char* output);
DEVGUARD_EXPORT int dg_r8(void);
DEVGUARD_EXPORT int dg_e1(int block_emulators, int is_physical, int is_compromised);
DEVGUARD_EXPORT void dg_u1(char* output);
DEVGUARD_EXPORT int dg_u2(const char* url);

#endif // DEVGUARD_CORE_H
