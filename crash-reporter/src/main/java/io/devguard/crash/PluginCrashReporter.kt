package io.devguard.crash

import io.devguard.core.NativeBridge
import io.devguard.core.StatusUrlResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

typealias PluginCrashMetadataProvider = suspend () -> Map<String, Any?>?

/**
 * Fire-and-forget plugin crash telemetry to the DevGuard API.
 * Publishable separately as `io.devguard:android-crash-reporter`.
 */
object PluginCrashReporter {
    private const val HDR_SIG = "X-DevGuard-Signature"
    private const val HDR_TS = "X-DevGuard-Timestamp"
    private const val HDR_API_KEY = "X-DevGuard-Api-Key"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var projectId: String? = null
    private var secret: String? = null
    private var baseUrl: String = NativeBridge.defaultStatusUrl()
    private var metadataProvider: PluginCrashMetadataProvider? = null
    private var uncaughtHandlerInstalled = false
    private var previousUncaughtHandler: Thread.UncaughtExceptionHandler? = null

    fun configure(
        projectId: String,
        secret: String? = null,
        baseUrl: String? = null,
        metadataProvider: PluginCrashMetadataProvider? = null,
        installUncaughtHandler: Boolean = true,
    ) {
        this.projectId = projectId
        this.secret = secret
        this.baseUrl = StatusUrlResolver.resolve(baseUrl)
        this.metadataProvider = metadataProvider
        if (installUncaughtHandler && !uncaughtHandlerInstalled) {
            registerUncaughtExceptionHandler()
        }
    }

    fun report(
        error: Throwable,
        stackTrace: String? = error.stackTraceToString(),
        context: String? = null,
        isFatal: Boolean = false,
        crashType: String = "sdk_internal",
    ) {
        val pid = projectId ?: return
        scope.launch {
            try {
                if (!StatusUrlResolver.isAllowed(baseUrl)) return@launch
                val metadata = metadataProvider?.invoke() ?: emptyMap()
                val deviceId = metadata["deviceId"]?.toString()
                if (deviceId.isNullOrBlank()) return@launch

                val timestamp = System.currentTimeMillis()
                val signature = NativeBridge.generateSignature(pid, timestamp)

                val body = JSONObject().apply {
                    put("projectId", pid)
                    put("deviceId", deviceId)
                    put("errorMessage", error.message ?: error.toString())
                    put("stackTrace", stackTrace)
                    put("errorName", error.javaClass.simpleName)
                    put("crashType", crashType)
                    put("isFatal", isFatal)
                    put("sdkRuntime", metadata["sdkRuntime"] ?: SdkIdentity.SDK_RUNTIME)
                    put("sdkVersion", metadata["sdkVersion"] ?: SdkIdentity.SDK_VERSION)
                    put("hostPlatform", metadata["hostPlatform"] ?: "android")
                    put("hostPlatformVersion", metadata["hostPlatformVersion"])
                    put("appVersion", metadata["version"])
                    put("occurredAt", Instant.now().toString())
                    if (context != null) {
                        put("clientMeta", JSONObject().put("context", context))
                    }
                }

                val connection = (URL(telemetryUrl()).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 8_000
                    readTimeout = 8_000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty(HDR_SIG, signature)
                    setRequestProperty(HDR_TS, timestamp.toString())
                    setRequestProperty(HDR_API_KEY, secret ?: "")
                }
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                connection.responseCode
            } catch (_: Exception) {
                // Never block app flows on crash telemetry.
            }
        }
    }

    private fun registerUncaughtExceptionHandler() {
        uncaughtHandlerInstalled = true
        previousUncaughtHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            report(error, isFatal = true, crashType = "native_crash", context = "uncaught:${thread.name}")
            previousUncaughtHandler?.uncaughtException(thread, error)
        }
    }

    private fun telemetryUrl(): String {
        val base = baseUrl.trimEnd('/')
        return if (base.endsWith("/devguard")) {
            base.replace(Regex("/devguard$"), "/api/v1/telemetry/plugin-crash")
        } else {
            "$base/api/v1/telemetry/plugin-crash"
        }
    }
}
