package io.devguard.core

internal object DevGuardConstants {
    val DEFAULT_API_URL: String
        get() = NativeBridge.defaultStatusUrl()
    const val CACHE_KEY = "dev_guard_cache_response"
    const val WIPE_NONCE_KEY = "dev_guard_wipe_nonce"
    const val TOKEN_KEY = "dev_guard_device_token"
    const val FINGERPRINT_KEY = "dev_guard_fingerprint"
    const val LAST_SYNC_KEY = "dev_guard_last_sync"
    const val USAGE_LOGS_KEY = "dev_guard_usage_logs"
    const val USERNAME_KEY = "dev_guard_username"
    const val EMAIL_KEY = "dev_guard_email"
    const val PHONE_KEY = "dev_guard_phone"
    const val CUSTOM_DATA_KEY = "dev_guard_custom_data"
    const val TUNNEL_V1 = "v1-gzip"
    const val HDR_SIG = "X-DevGuard-Signature"
    const val HDR_TS = "X-DevGuard-Timestamp"
    const val HDR_TUNNEL = "X-DevGuard-Tunnel"
    const val HDR_API_KEY = "X-DevGuard-Api-Key"
    const val RESP_SIG = "X-DevGuard-Response-Signature"
}
