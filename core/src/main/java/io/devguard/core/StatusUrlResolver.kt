package io.devguard.core

/**
 * Resolves DevGuard API URLs. Custom values must be HTTPS on devguard.uk (or a subdomain).
 * Default endpoint is reconstructed from native obfuscated segments (dg_u1).
 */
object StatusUrlResolver {
    private const val INVALID_URL_MESSAGE =
        "DevGuard Security Alert: statusUrl must be an HTTPS endpoint on the devguard.uk domain."

    fun resolve(statusUrl: String?): String {
        val candidate = if (statusUrl.isNullOrBlank()) {
            NativeBridge.defaultStatusUrl()
        } else {
            statusUrl.trim()
        }
        if (!NativeBridge.isAllowedStatusUrl(candidate)) {
            throw IllegalArgumentException(INVALID_URL_MESSAGE)
        }
        return candidate
    }

    fun isAllowed(url: String): Boolean = NativeBridge.isAllowedStatusUrl(url.trim())
}
