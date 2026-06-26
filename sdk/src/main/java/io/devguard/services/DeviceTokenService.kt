package io.devguard.services

import io.devguard.core.DevGuardConstants
import io.devguard.core.NativeBridge

internal class DeviceTokenService(private val storage: SecureStorageService) {
    fun getToken(): String? {
        val scrambled = storage.read(DevGuardConstants.TOKEN_KEY) ?: return null
        return try {
            NativeBridge.secureGetToken(scrambled)
        } catch (_: Exception) {
            null
        }
    }

    fun saveToken(token: String) {
        val scrambled = NativeBridge.secureSaveToken(token)
        storage.write(DevGuardConstants.TOKEN_KEY, scrambled)
    }

    fun clearToken() {
        storage.delete(DevGuardConstants.TOKEN_KEY)
    }

    fun generateFingerprint(deviceId: String, model: String?, os: String?): String {
        val raw = "$deviceId|${model ?: "unknown"}|${os ?: "unknown"}"
        return NativeBridge.hashSha256Hex(raw).take(16)
    }

    fun saveFingerprint(fingerprint: String) {
        storage.write(DevGuardConstants.FINGERPRINT_KEY, fingerprint)
    }
}
