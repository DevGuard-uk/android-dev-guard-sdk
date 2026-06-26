package io.devguard.services

import io.devguard.core.DevGuardConstants
import io.devguard.core.NativeBridge
import io.devguard.core.PolicyLock
import io.devguard.core.StatusUrlResolver
import io.devguard.models.DeviceMetadata
import io.devguard.models.GuardResponse
import io.devguard.models.LicenseStatus
import io.devguard.models.StatusFetchFailure
import io.devguard.models.StatusFetchResult
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPOutputStream
import android.util.Base64

internal class RestClient(
    baseUrl: String,
    private val secret: String?,
) {
    private val baseUrl: String = StatusUrlResolver.resolve(baseUrl)

    private fun authHeaders(signature: String, timestamp: Long, includeTunnel: Boolean = false): Map<String, String> {
        if (!StatusUrlResolver.isAllowed(baseUrl)) {
            return mapOf("Content-Type" to "application/json")
        }
        val headers = mutableMapOf(
            "Content-Type" to "application/json",
            DevGuardConstants.HDR_SIG to signature,
            DevGuardConstants.HDR_TS to timestamp.toString(),
            DevGuardConstants.HDR_API_KEY to (secret ?: ""),
        )
        if (includeTunnel) {
            headers[DevGuardConstants.HDR_TUNNEL] = DevGuardConstants.TUNNEL_V1
        }
        return headers
    }

    fun fetchStatus(projectId: String, metadata: DeviceMetadata?): StatusFetchResult {
        if (!StatusUrlResolver.isAllowed(baseUrl)) {
            return StatusFetchResult(failure = StatusFetchFailure.NETWORK_ERROR)
        }
        return try {
            val timestamp = System.currentTimeMillis()
            val signature = NativeBridge.generateSignature(projectId, timestamp)

            val metadataMap = metadata?.toPayloadMap() ?: emptyMap()
            val metadataJson = JSONObject(metadataMap).toString()
            val compressed = gzip(metadataJson.toByteArray(Charsets.UTF_8))
            val encodedPayload = Base64.encodeToString(compressed, Base64.NO_WRAP)

            val body = JSONObject().apply {
                put("projectId", projectId)
                put("deviceId", metadata?.deviceId)
                put("version", metadata?.appVersion)
                put("isPhysicalDevice", metadata?.isPhysicalDevice)
                put("p", encodedPayload)
            }

            val connection = (URL(baseUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 50_000
                readTimeout = 50_000
                doOutput = true
                authHeaders(signature, timestamp, includeTunnel = true).forEach { (key, value) ->
                    setRequestProperty(key, value)
                }
            }

            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

            val code = connection.responseCode
            val responseBody = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() } ?: ""

            if (code == 200) {
                val serverSignature = connection.getHeaderField(DevGuardConstants.RESP_SIG)
                    ?: return StatusFetchResult(failure = StatusFetchFailure.SIGNATURE_MISMATCH)

                if (!NativeBridge.verifyResponse(responseBody, serverSignature)) {
                    val data = JSONObject(responseBody)
                    val beta = data.optJSONObject("betaFeatures")
                    if (beta?.optBoolean("bypassSignature") != true) {
                        return StatusFetchResult(failure = StatusFetchFailure.SIGNATURE_MISMATCH)
                    }
                }

                val json = JSONObject(responseBody).toMap()
                return StatusFetchResult(response = GuardResponse.fromJson(json))
            }

            StatusFetchResult(failure = StatusFetchFailure.NETWORK_ERROR)
        } catch (_: java.net.SocketTimeoutException) {
            StatusFetchResult(failure = StatusFetchFailure.TIMEOUT)
        } catch (_: Exception) {
            StatusFetchResult(failure = StatusFetchFailure.NETWORK_ERROR)
        }
    }

    fun verifyAndUnlock(projectId: String, hashedKey: String): Boolean {
        if (!StatusUrlResolver.isAllowed(baseUrl)) return false
        return try {
            val base = URL(baseUrl)
            val unlockPath = if (base.path.endsWith("/")) "${base.path}unlock" else "${base.path}/unlock"
            val unlockUrl = URL(base.protocol, base.host, base.port, unlockPath)

            val timestamp = System.currentTimeMillis()
            val signature = NativeBridge.generateSignature(projectId, timestamp)

            val connection = (unlockUrl.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 30_000
                readTimeout = 30_000
                doOutput = true
                authHeaders(signature, timestamp).forEach { (key, value) ->
                    setRequestProperty(key, value)
                }
            }

            val body = JSONObject().apply {
                put("projectId", projectId)
                put("providedKey", hashedKey)
            }
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

            if (connection.responseCode != 200) return false
            val serverSignature = connection.getHeaderField(DevGuardConstants.RESP_SIG) ?: return false
            val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
            if (!NativeBridge.verifyResponse(responseBody, serverSignature)) return false
            responseBody.trim() == "unlocked"
        } catch (_: Exception) {
            false
        }
    }

    private fun gzip(input: ByteArray): ByteArray {
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).use { it.write(input) }
        return bos.toByteArray()
    }

    private fun JSONObject.toMap(): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        keys().forEach { key ->
            map[key] = when (val value = get(key)) {
                is JSONObject -> value.toMap()
                else -> value
            }
        }
        return map
    }
}

internal object GuardEnforcement {
    fun apply(response: GuardResponse, metadata: DeviceMetadata, isCompromised: Boolean = false): GuardResponse {
        if (isCompromised) {
            val code = NativeBridge.evaluatePolicy(false, metadata.isPhysicalDevice, true)
            if (code == PolicyLock.COMPROMISED) {
                return response.copyWith(
                    status = LicenseStatus.LOCKED,
                    title = "Security Alert",
                    message = "This device appears to be compromised. Access is restricted for your protection.",
                )
            }
        }
        val code = NativeBridge.evaluatePolicy(
            blockEmulators = response.blockEmulators,
            isPhysical = metadata.isPhysicalDevice,
            isCompromised = false,
        )
        return if (code == PolicyLock.EMULATOR) {
            response.copyWith(
                status = LicenseStatus.LOCKED,
                title = "Emulator Detected",
                message = "This application cannot run on emulators or simulators.",
            )
        } else {
            response
        }
    }
}

internal class SyncPolicyService(private val storage: SecureStorageService) {
    private var lastLifecycleSync: Long = 0

    fun shouldSync(
        cachedResponse: GuardResponse?,
        forceLogs: Boolean,
        trigger: String?,
    ): Boolean {
        if (forceLogs) return true

        if (trigger == "foreground" || trigger == "background" || trigger == "appLaunch") {
            val lifecycleSync = cachedResponse?.lifecycleSync
            val enabled = when {
                lifecycleSync != null -> when (trigger) {
                    "foreground" -> lifecycleSync["onForeground"] == true
                    "background" -> lifecycleSync["onBackground"] == true
                    else -> lifecycleSync["onAppLaunch"] == true
                }
                else -> trigger != "background"
            }
            if (!enabled) return false

            val now = System.currentTimeMillis()
            if (now - lastLifecycleSync < 60_000) return false
            lastLifecycleSync = now
            return true
        }

        if (trigger == "heartbeat") {
            return evaluateSyncPolicy(cachedResponse?.syncPolicy ?: "immediate")
        }

        if (cachedResponse?.lifecycleSync != null) return true
        return evaluateSyncPolicy(cachedResponse?.syncPolicy ?: "immediate")
    }

    private fun evaluateSyncPolicy(policy: String): Boolean {
        if (policy == "immediate") return true
        if (policy == "onDemand") return false

        val lastSync = storage.read(DevGuardConstants.LAST_SYNC_KEY)?.toLongOrNull() ?: 0L
        val now = System.currentTimeMillis()
        return when (policy) {
            "daily" -> now - lastSync > 86_400_000
            "weekly" -> now - lastSync > 604_800_000
            else -> true
        }
    }

    fun recordSuccessfulSync() {
        storage.write(DevGuardConstants.LAST_SYNC_KEY, System.currentTimeMillis().toString())
    }
}

internal class RemoteWipeService(
    private val cacheService: CacheService,
    private val usageLogger: UsageLogger,
    private val storage: SecureStorageService,
    private val tokenService: DeviceTokenService,
) {
    fun execute(revokeToken: Boolean = false) {
        cacheService.clear()
        usageLogger.clearLogs()
        storage.deleteAllDeviceUserKeys()
        if (revokeToken) tokenService.clearToken()
    }
}
