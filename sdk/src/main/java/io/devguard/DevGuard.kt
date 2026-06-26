package io.devguard

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.content.Context
import android.os.Build
import io.devguard.core.DevGuardConstants
import io.devguard.core.NativeBridge
import io.devguard.core.PolicyLock
import io.devguard.core.StatusUrlResolver
import io.devguard.crash.PluginCrashReporter
import io.devguard.models.FailSafe
import io.devguard.models.GuardResponse
import io.devguard.models.LicenseStatus
import io.devguard.services.CacheService
import io.devguard.services.DeviceTokenService
import io.devguard.services.GuardEnforcement
import io.devguard.services.HardwareService
import io.devguard.services.RemoteWipeService
import io.devguard.services.RestClient
import io.devguard.services.SecureStorageService
import io.devguard.services.SyncPolicyService
import io.devguard.services.UsageLogger
import io.devguard.ui.DevGuardShield
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

object DevGuard {
    enum class FailSafe {
        OPEN, CLOSED,
    }

    private var application: Application? = null
    private var projectId: String? = null
    private var secret: String? = null
    private var statusUrl: String = NativeBridge.defaultStatusUrl()
    private var failSafe: FailSafe = FailSafe.OPEN

    private lateinit var storage: SecureStorageService
    private lateinit var cacheService: CacheService
    private lateinit var tokenService: DeviceTokenService
    private lateinit var usageLogger: UsageLogger
    private lateinit var syncPolicy: SyncPolicyService
    private var restClient: RestClient? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var heartbeatJob: Job? = null
    private var heartbeatPaused = false
    private var lifecycleCallbacksRegistered = false
    @Volatile private var previewModeLocked = false

    private val _currentResponse = MutableStateFlow<GuardResponse?>(null)
    val currentResponse: StateFlow<GuardResponse?> = _currentResponse.asStateFlow()

    private val _status = MutableStateFlow(LicenseStatus.PENDING)
    val status: StateFlow<LicenseStatus> = _status.asStateFlow()

    val isLocked: Boolean
        get() = _status.value == LicenseStatus.LOCKED || _status.value == LicenseStatus.EXPIRED

    @JvmStatic
    fun init(
        context: Application,
        projectId: String,
        secret: String,
        failSafe: FailSafe = FailSafe.OPEN,
        statusUrl: String = NativeBridge.defaultStatusUrl(),
    ) {
        this.application = context
        this.projectId = projectId
        this.secret = secret
        this.failSafe = failSafe
        val effectiveUrl = StatusUrlResolver.resolve(statusUrl)
        this.statusUrl = effectiveUrl

        storage = SecureStorageService(context)
        cacheService = CacheService(storage, projectId)
        tokenService = DeviceTokenService(storage)
        usageLogger = UsageLogger(storage)
        syncPolicy = SyncPolicyService(storage)
        restClient = RestClient(effectiveUrl, secret)

        PluginCrashReporter.configure(
            projectId = projectId,
            secret = secret,
            baseUrl = effectiveUrl,
            metadataProvider = {
                val app = application ?: return@configure null
                val hardware = HardwareService(app, storage, tokenService, usageLogger, _currentResponse.value)
                hardware.collect().toPayloadMap()
            },
        )

        registerLifecycleCallbacks(context)
        scope.launch { backgroundInit() }
    }

    @JvmStatic
    fun attachShield(activity: Activity) {
        DevGuardShield.attach(activity)
    }

    /** Example/screenshot helper — injects a mock license response without calling the API. */
    @JvmStatic
    fun setPreviewResponse(response: GuardResponse) {
        previewModeLocked = true
        updateFromResponse(response)
    }

    /** Call before [init] when capturing README screenshots so sync does not overwrite mock UI. */
    @JvmStatic
    fun lockPreviewMode() {
        previewModeLocked = true
    }

    @JvmStatic
    fun setDeviceUser(
        username: String? = null,
        email: String? = null,
        phone: String? = null,
        customData: Map<String, Any?>? = null,
    ) {
        username?.let { storage.write(DevGuardConstants.USERNAME_KEY, it) }
            ?: storage.delete(DevGuardConstants.USERNAME_KEY)
        email?.let { storage.write(DevGuardConstants.EMAIL_KEY, it) }
            ?: storage.delete(DevGuardConstants.EMAIL_KEY)
        phone?.let { storage.write(DevGuardConstants.PHONE_KEY, it) }
            ?: storage.delete(DevGuardConstants.PHONE_KEY)
        customData?.let { storage.write(DevGuardConstants.CUSTOM_DATA_KEY, JSONObject(it).toString()) }
            ?: storage.delete(DevGuardConstants.CUSTOM_DATA_KEY)

        scope.launch { syncStatus(forceLogs = true) }
    }

    @JvmStatic
    suspend fun unlock(key: String): Boolean {
        val pid = projectId ?: return false
        val hashed = NativeBridge.hashSha256Hex(key)
        val ok = withContext(Dispatchers.IO) {
            restClient?.verifyAndUnlock(pid, hashed) == true
        }
        if (ok) syncStatus(force = true)
        return ok
    }

    @JvmStatic
    fun verify() {
        scope.launch { syncStatus(force = true) }
    }

    internal fun updateFromResponse(response: GuardResponse?) {
        val previous = _currentResponse.value
        _currentResponse.value = response
        _status.value = response?.status ?: LicenseStatus.PENDING
        handleBetaFeatures(previous, response)
        DevGuardShield.refreshAll()
    }

    private fun handleBetaFeatures(previous: GuardResponse?, response: GuardResponse?) {
        if (response == null) return
        if (response.status == LicenseStatus.WARNING &&
            response.betaFeatures["vibrateOnWarning"] == true &&
            previous?.status != LicenseStatus.WARNING
        ) {
            vibrateWarning()
        }
        val wipeNonce = response.betaFeatures["wipeNonce"]
        val nonce = when (wipeNonce) {
            is Number -> wipeNonce.toInt()
            is String -> wipeNonce.toIntOrNull()
            else -> response.wipeNonce
        }
        if (nonce != null && nonce > 0) {
            scope.launch { executeRemoteWipe(nonce) }
        }
    }

    private fun vibrateWarning() {
        val app = application ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = app.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator.vibrate(VibrationEffect.createOneShot(400, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val v = app.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v.vibrate(VibrationEffect.createOneShot(400, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    v.vibrate(400)
                }
            }
        } catch (_: Exception) {
        }
    }

    internal suspend fun executeRemoteWipe(nonce: Int) {
        val last = cacheService.getLastWipeNonce()
        if (last != null && nonce <= last) return
        RemoteWipeService(cacheService, usageLogger, storage, tokenService).execute()
        cacheService.setLastWipeNonce(nonce)
    }

    private suspend fun backgroundInit() {
        if (previewModeLocked) return
        val app = application ?: return
        val pid = projectId ?: return

        val hardware = HardwareService(app, storage, tokenService, usageLogger, _currentResponse.value)

        if (hardware.isDeviceCompromised()) {
            val code = NativeBridge.evaluatePolicy(false, true, true)
            if (code == PolicyLock.COMPROMISED) {
                updateFromResponse(
                    GuardResponse(
                        status = LicenseStatus.LOCKED,
                        title = "Security Alert",
                        message = "This device appears to be compromised. Access is restricted.",
                    ),
                )
                return
            }
        }

        val cached = withContext(Dispatchers.IO) { cacheService.getResponse() }
        if (cached != null) {
            updateFromResponse(cached)
        } else if (failSafe == FailSafe.CLOSED) {
            updateFromResponse(
                GuardResponse(
                    status = LicenseStatus.PENDING,
                    message = "Connecting to security server...",
                ),
            )
        } else {
            updateFromResponse(GuardResponse(status = LicenseStatus.ACTIVE))
        }

        usageLogger.logEvent("app_open")
        syncStatus(trigger = "appLaunch")
        startHeartbeat()
    }

    private suspend fun syncStatus(
        force: Boolean = false,
        forceLogs: Boolean = false,
        trigger: String? = null,
    ) {
        if (previewModeLocked) return
        val app = application ?: return
        val pid = projectId ?: return
        val client = restClient ?: return

        if (!force && !syncPolicy.shouldSync(_currentResponse.value, forceLogs, trigger)) {
            return
        }

        val hardware = HardwareService(app, storage, tokenService, usageLogger, _currentResponse.value)
        val metadata = withContext(Dispatchers.IO) { hardware.collect(forceLogs) }
        val compromised = hardware.isDeviceCompromised()

        val result = withContext(Dispatchers.IO) { client.fetchStatus(pid, metadata) }

        if (result.isSignatureMismatch) {
            PluginCrashReporter.report(
                error = IllegalStateException("Server response signature mismatch"),
                context = "syncStatus",
                crashType = "sdk_internal",
            )
            updateFromResponse(
                GuardResponse(
                    status = LicenseStatus.LOCKED,
                    title = "Security Alert",
                    message = "Server response could not be verified. Access restricted.",
                ),
            )
            return
        }

        val fresh = result.response ?: return

        handleRemoteCommand(fresh.remoteCommand)
        fresh.deviceToken?.let { tokenService.saveToken(it) }

        val enforced = GuardEnforcement.apply(fresh, metadata, compromised)
        updateFromResponse(enforced)
        withContext(Dispatchers.IO) { cacheService.saveResponse(enforced) }
        syncPolicy.recordSuccessfulSync()

        if (metadata.usageLogs?.isNotEmpty() == true) {
            usageLogger.clearLogs()
        }

        restartHeartbeatIfNeeded(enforced)
    }

    private suspend fun handleRemoteCommand(command: String?) {
        if (command.isNullOrBlank() || command == "none") return
        when (command) {
            "syncLogs" -> syncStatus(force = true, forceLogs = true)
            "clearLogs" -> usageLogger.clearLogs()
            "wipeCache" -> cacheService.clear()
            "revokeToken" -> tokenService.clearToken()
        }
    }

    private fun startHeartbeat() {
        if (heartbeatPaused) return
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (true) {
                val response = _currentResponse.value
                val fallbackMinutes = response?.lifecycleSync?.let {
                    ((it["fallbackIntervalHours"] as? Number)?.toInt() ?: 24) * 60
                } ?: (response?.pingInterval ?: 5)
                val jitter = response?.lifecycleSync?.let {
                    (it["jitterMaxMinutes"] as? Number)?.toInt() ?: 0
                } ?: 0
                val jitterOffset = if (jitter > 0) (System.currentTimeMillis() % jitter).toInt() else 0
                val intervalMinutes = (fallbackMinutes + jitterOffset).coerceAtLeast(1)
                delay(intervalMinutes * 60_000L)
                if (!heartbeatPaused) syncStatus(trigger = "heartbeat")
            }
        }
    }

    internal fun setHeartbeatPaused(paused: Boolean) {
        heartbeatPaused = paused
        if (paused) {
            heartbeatJob?.cancel()
            heartbeatJob = null
        } else if (heartbeatJob == null) {
            startHeartbeat()
        }
    }

    private fun restartHeartbeatIfNeeded(newResponse: GuardResponse) {
        val old = _currentResponse.value
        val changed = old?.pingInterval != newResponse.pingInterval ||
            old?.lifecycleSync != newResponse.lifecycleSync
        if (changed) startHeartbeat()
    }

    private fun registerLifecycleCallbacks(app: Application) {
        if (lifecycleCallbacksRegistered) return
        lifecycleCallbacksRegistered = true
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            private var activityCount = 0

            override fun onActivityResumed(activity: Activity) {
                activityCount++
                if (activityCount == 1) {
                    setHeartbeatPaused(false)
                    scope.launch { syncStatus(trigger = "foreground") }
                }
            }

            override fun onActivityPaused(activity: Activity) {
                activityCount--
                if (activityCount == 0) {
                    setHeartbeatPaused(true)
                    scope.launch { syncStatus(trigger = "background") }
                }
            }

            override fun onActivityCreated(a: Activity, b: Bundle?) {}
            override fun onActivityStarted(a: Activity) {}
            override fun onActivityStopped(a: Activity) {}
            override fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}
            override fun onActivityDestroyed(a: Activity) {
                DevGuardShield.detach(a)
            }
        })
    }

    private fun sha256(input: String): String = NativeBridge.hashSha256Hex(input)
}
