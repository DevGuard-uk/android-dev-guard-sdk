package io.devguard.services

import android.content.Context
import android.net.ConnectivityManager
import android.os.BatteryManager
import android.os.Build
import android.provider.Settings
import io.devguard.core.NativeBridge
import io.devguard.crash.PluginCrashReporter
import io.devguard.crash.SdkIdentity
import io.devguard.models.DeviceMetadata
import io.devguard.models.GuardResponse
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileReader

internal class HardwareService(
    private val context: Context,
    private val storage: SecureStorageService,
    private val tokenService: DeviceTokenService,
    private val usageLogger: UsageLogger,
    private val cachedResponse: GuardResponse?,
) {
    private val advancedTelemetry: Boolean
        get() = cachedResponse?.betaFeatures?.get("advancedTelemetry") == true

    fun quickResolveDeviceId(): String {
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?: "unknown_android"
    }

    fun collect(forceLogs: Boolean = false): DeviceMetadata {
        val deviceId = quickResolveDeviceId()
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val versionName = packageInfo.versionName ?: "1.0"
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }
        val isPhysical = !isProbablyEmulator()

        val username = storage.read(io.devguard.core.DevGuardConstants.USERNAME_KEY)
        val email = storage.read(io.devguard.core.DevGuardConstants.EMAIL_KEY)
        val phone = storage.read(io.devguard.core.DevGuardConstants.PHONE_KEY)
        val customDataRaw = storage.read(io.devguard.core.DevGuardConstants.CUSTOM_DATA_KEY)
        val customData = customDataRaw?.let {
            try {
                JSONObject(it).toMap()
            } catch (_: Exception) {
                null
            }
        }

        val model = Build.MODEL
        val brand = Build.BRAND
        val os = "Android ${Build.VERSION.RELEASE}"

        return DeviceMetadata(
            deviceId = deviceId,
            deviceName = Build.DEVICE,
            model = model,
            brand = brand,
            os = os,
            appVersion = "$versionName+$versionCode",
            isPhysicalDevice = isPhysical,
            username = username,
            email = email,
            phone = phone,
            customData = customData,
            battery = if (advancedTelemetry) getBatteryLevel() else null,
            ram = if (advancedTelemetry) "${NativeBridge.getTotalRamMb()} MB" else null,
            storage = if (advancedTelemetry) getStorage() else null,
            networkType = if (advancedTelemetry) getNetworkType() else null,
            usageLogs = if (forceLogs) usageLogger.getLogs() else null,
            deviceToken = tokenService.getToken(),
            fingerprint = tokenService.generateFingerprint(deviceId, model, os),
            sdkRuntime = SdkIdentity.SDK_RUNTIME,
            sdkVersion = SdkIdentity.SDK_VERSION,
            hostPlatform = "android",
            hostPlatformVersion = Build.VERSION.RELEASE,
        )
    }

    private fun isProbablyEmulator(): Boolean {
        return (Build.FINGERPRINT.startsWith("generic")
            || Build.FINGERPRINT.startsWith("unknown")
            || Build.MODEL.contains("google_sdk")
            || Build.MODEL.contains("Emulator")
            || Build.MODEL.contains("Android SDK built for x86")
            || Build.MANUFACTURER.contains("Genymotion")
            || Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic")
            || "google_sdk" == Build.PRODUCT)
    }

    fun isDeviceCompromised(): Boolean {
        val paths = listOf(
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
        )
        return paths.any { File(it).exists() }
    }

    private fun getBatteryLevel(): String? {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager ?: return null
        val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        return "$level%"
    }

    private fun getStorage(): String? {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("df", "-h", "/data"))
            val output = process.inputStream.bufferedReader().use(BufferedReader::readText)
            val lines = output.lines().filter { it.isNotBlank() }
            if (lines.size >= 2) lines[1].split(Regex("\\s+")).getOrNull(1) else null
        } catch (_: Exception) {
            null
        }
    }

    private fun getNetworkType(): String? {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return null
        val network = cm.activeNetwork ?: return "none"
        val caps = cm.getNetworkCapabilities(network) ?: return "unknown"
        return when {
            caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
            else -> "other"
        }
    }

    private fun JSONObject.toMap(): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        keys().forEach { key ->
            when (val value = get(key)) {
                is JSONObject -> map[key] = value.toMap()
                is JSONArray -> map[key] = value.toList()
                else -> map[key] = value
            }
        }
        return map
    }

    private fun JSONArray.toList(): List<Any?> {
        val list = mutableListOf<Any?>()
        for (i in 0 until length()) {
            list.add(get(i))
        }
        return list
    }
}
