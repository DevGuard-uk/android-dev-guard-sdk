package io.devguard.services

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import io.devguard.core.DevGuardConstants

internal class SecureStorageService(context: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "devguard_secure_prefs",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun write(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    fun read(key: String): String? = prefs.getString(key, null)

    fun delete(key: String) {
        prefs.edit().remove(key).apply()
    }

    fun deleteAllDeviceUserKeys() {
        prefs.edit()
            .remove(DevGuardConstants.USERNAME_KEY)
            .remove(DevGuardConstants.EMAIL_KEY)
            .remove(DevGuardConstants.PHONE_KEY)
            .remove(DevGuardConstants.CUSTOM_DATA_KEY)
            .apply()
    }
}
