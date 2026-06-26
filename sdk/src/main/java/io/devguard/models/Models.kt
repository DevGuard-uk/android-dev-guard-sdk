package io.devguard.models

enum class LicenseStatus {
    PENDING, ACTIVE, WARNING, LOCKED, EXPIRED;

    companion object {
        fun fromString(value: String?): LicenseStatus = when (value?.lowercase()) {
            "pending" -> PENDING
            "warning" -> WARNING
            "locked" -> LOCKED
            "expired" -> EXPIRED
            else -> ACTIVE
        }
    }
}

data class LockScreenBranding(
    val brandName: String? = null,
    val logoUrl: String? = null,
    val primaryColor: String? = null,
    val accentColor: String? = null,
    val websiteUrl: String? = null,
    val hidePoweredBy: Boolean = false,
) {
    companion object {
        fun fromJson(json: Map<String, Any?>?): LockScreenBranding? {
            if (json == null) return null
            return LockScreenBranding(
                brandName = json["brandName"] as? String,
                logoUrl = json["logoUrl"] as? String,
                primaryColor = json["primaryColor"] as? String,
                accentColor = json["accentColor"] as? String,
                websiteUrl = json["websiteUrl"] as? String,
                hidePoweredBy = json["hidePoweredBy"] == true,
            )
        }
    }
}

data class GuardResponse(
    val status: LicenseStatus,
    val title: String? = null,
    val message: String = "",
    val contactEmail: String = "",
    val contactPhone: String = "",
    val contactWhatsapp: String = "",
    val allowUnlock: Boolean = false,
    val betaFeatures: Map<String, Any?> = emptyMap(),
    val extraData: Map<String, Any?> = emptyMap(),
    val lifecycleSync: Map<String, Any?>? = null,
    val remoteCommand: String? = null,
    val deviceToken: String? = null,
    val deviceTracking: Boolean = false,
    val currentGeneration: Int = 1,
    val diagnosticPasscodeHash: String? = null,
    val pingInterval: Int = 5,
    val syncPolicy: String = "immediate",
    val blockEmulators: Boolean = false,
    val branding: LockScreenBranding? = null,
    val wipeNonce: Int? = null,
) {
    fun copyWith(
        status: LicenseStatus = this.status,
        title: String? = this.title,
        message: String = this.message,
    ): GuardResponse = copy(status = status, title = title, message = message)

    fun toJson(): Map<String, Any?> = mapOf(
        "status" to status.name.lowercase(),
        "title" to title,
        "message" to message,
        "contactEmail" to contactEmail,
        "contactPhone" to contactPhone,
        "contactWhatsapp" to contactWhatsapp,
        "allowUnlock" to allowUnlock,
        "betaFeatures" to betaFeatures,
        "extraData" to extraData,
        "lifecycleSync" to lifecycleSync,
        "remoteCommand" to remoteCommand,
        "deviceToken" to deviceToken,
        "deviceTracking" to deviceTracking,
        "currentGeneration" to currentGeneration,
        "diagnosticPasscodeHash" to diagnosticPasscodeHash,
        "pingInterval" to pingInterval,
        "syncPolicy" to syncPolicy,
        "blockEmulators" to blockEmulators,
        "branding" to branding?.let {
            mapOf(
                "brandName" to it.brandName,
                "logoUrl" to it.logoUrl,
                "primaryColor" to it.primaryColor,
                "accentColor" to it.accentColor,
                "websiteUrl" to it.websiteUrl,
                "hidePoweredBy" to it.hidePoweredBy,
            )
        },
        "wipeNonce" to wipeNonce,
    )

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromJson(json: Map<String, Any?>): GuardResponse {
            val ping = json["pingInterval"]
            val pingInt = when (ping) {
                is Number -> ping.toInt().coerceAtLeast(1)
                else -> 5
            }
            return GuardResponse(
                status = LicenseStatus.fromString(json["status"] as? String),
                title = json["title"] as? String,
                message = json["message"] as? String ?: "",
                contactEmail = json["contactEmail"] as? String ?: "",
                contactPhone = json["contactPhone"] as? String ?: "",
                contactWhatsapp = json["contactWhatsapp"] as? String ?: "",
                allowUnlock = json["allowUnlock"] == true,
                betaFeatures = (json["betaFeatures"] as? Map<String, Any?>) ?: emptyMap(),
                extraData = (json["extraData"] as? Map<String, Any?>) ?: emptyMap(),
                lifecycleSync = json["lifecycleSync"] as? Map<String, Any?>,
                remoteCommand = json["remoteCommand"] as? String,
                deviceToken = json["deviceToken"] as? String,
                deviceTracking = json["deviceTracking"] == true,
                currentGeneration = (json["currentGeneration"] as? Number)?.toInt() ?: 1,
                diagnosticPasscodeHash = json["diagnosticPasscodeHash"] as? String,
                pingInterval = pingInt,
                syncPolicy = json["syncPolicy"] as? String ?: "immediate",
                blockEmulators = json["blockEmulators"] == true,
                branding = LockScreenBranding.fromJson(json["branding"] as? Map<String, Any?>),
                wipeNonce = (json["wipeNonce"] as? Number)?.toInt(),
            )
        }
    }
}

data class DeviceMetadata(
    val deviceId: String,
    val deviceName: String? = null,
    val model: String? = null,
    val brand: String? = null,
    val os: String? = null,
    val appVersion: String? = null,
    val isPhysicalDevice: Boolean = true,
    val username: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val customData: Map<String, Any?>? = null,
    val battery: String? = null,
    val ram: String? = null,
    val storage: String? = null,
    val networkType: String? = null,
    val usageLogs: List<Map<String, Any?>>? = null,
    val deviceToken: String? = null,
    val fingerprint: String? = null,
    val sdkRuntime: String? = null,
    val sdkVersion: String? = null,
    val hostPlatform: String? = null,
    val hostPlatformVersion: String? = null,
) {
    fun toPayloadMap(): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>(
            "deviceId" to deviceId,
            "deviceName" to deviceName,
            "model" to model,
            "os" to os,
            "version" to appVersion,
            "isPhysicalDevice" to isPhysicalDevice,
        )
        brand?.let { map["brand"] = it }
        username?.let { map["username"] = it }
        email?.let { map["email"] = it }
        phone?.let { map["phone"] = it }
        customData?.let { map["customData"] = it }
        battery?.let { map["battery"] = it }
        ram?.let { map["ram"] = it }
        storage?.let { map["storage"] = it }
        networkType?.let { map["networkType"] = it }
        usageLogs?.let { map["usageLogs"] = it }
        deviceToken?.let { map["deviceToken"] = it }
        fingerprint?.let { map["fingerprint"] = it }
        sdkRuntime?.let { map["sdkRuntime"] = it }
        sdkVersion?.let { map["sdkVersion"] = it }
        hostPlatform?.let { map["hostPlatform"] = it }
        hostPlatformVersion?.let { map["hostPlatformVersion"] = it }
        return map
    }
}

enum class FailSafe {
    OPEN, CLOSED,
}

enum class StatusFetchFailure {
    NONE, NETWORK_ERROR, TIMEOUT, SIGNATURE_MISMATCH,
}

data class StatusFetchResult(
    val response: GuardResponse? = null,
    val failure: StatusFetchFailure = StatusFetchFailure.NONE,
) {
    val isSignatureMismatch: Boolean get() = failure == StatusFetchFailure.SIGNATURE_MISMATCH
}
