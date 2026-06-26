package io.devguard.services

import android.util.Base64
import io.devguard.core.DevGuardConstants
import io.devguard.core.NativeBridge
import io.devguard.models.GuardResponse
import org.json.JSONObject

internal class CacheService(
    private val storage: SecureStorageService,
    private val projectId: String?,
) {
    private fun encrypt(plainText: String): String {
        if (projectId == null) return plainText
        val key = projectId.toByteArray(Charsets.UTF_8)
        val bytes = plainText.toByteArray(Charsets.UTF_8)
        val encrypted = NativeBridge.xorTransform(bytes, key)
        return Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }

    private fun decrypt(encryptedText: String): String {
        if (projectId == null) return encryptedText
        return try {
            val bytes = Base64.decode(encryptedText, Base64.NO_WRAP)
            val key = projectId.toByteArray(Charsets.UTF_8)
            val decrypted = NativeBridge.xorTransform(bytes, key)
            String(decrypted, Charsets.UTF_8)
        } catch (_: Exception) {
            encryptedText
        }
    }

    fun saveResponse(response: GuardResponse) {
        val json = JSONObject(response.toJson()).toString()
        storage.write(DevGuardConstants.CACHE_KEY, encrypt(json))
    }

    fun getResponse(): GuardResponse? {
        val data = storage.read(DevGuardConstants.CACHE_KEY) ?: return null
        return try {
            val decrypted = decrypt(data)
            val json = JSONObject(decrypted)
            GuardResponse.fromJson(json.toMap())
        } catch (_: Exception) {
            null
        }
    }

    fun clear() {
        storage.delete(DevGuardConstants.CACHE_KEY)
    }

    fun getLastWipeNonce(): Int? =
        storage.read(DevGuardConstants.WIPE_NONCE_KEY)?.toIntOrNull()

    fun setLastWipeNonce(nonce: Int) {
        storage.write(DevGuardConstants.WIPE_NONCE_KEY, nonce.toString())
    }

    private fun JSONObject.toMap(): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        keys().forEach { key ->
            map[key] = get(key)
        }
        return map
    }
}
