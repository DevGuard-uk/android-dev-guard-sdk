package io.devguard.core

object NativeBridge {
    init {
        System.loadLibrary("devguard_sdk")
    }

    external fun generateSignatureNative(projectId: String, timestamp: Long): String
    external fun verifyResponseNative(responseBody: String, signature: String): Boolean
    external fun secureSaveTokenNative(token: String): String
    external fun secureGetTokenNative(scrambled: String): String
    external fun hashSha256Native(input: String): String
    external fun xorTransformNative(input: ByteArray, key: ByteArray): ByteArray
    external fun evaluatePolicyNative(blockEmulators: Int, isPhysical: Int, isCompromised: Int): Int
    external fun getTotalRamMbNative(): Int
    external fun defaultStatusUrlNative(): String
    external fun isAllowedStatusUrlNative(url: String): Boolean

    fun generateSignature(projectId: String, timestamp: Long): String =
        generateSignatureNative(projectId, timestamp)

    fun verifyResponse(responseBody: String, signature: String): Boolean =
        verifyResponseNative(responseBody, signature)

    fun secureSaveToken(token: String): String = secureSaveTokenNative(token)

    fun secureGetToken(scrambled: String): String = secureGetTokenNative(scrambled)

    fun hashSha256Hex(input: String): String = hashSha256Native(input)

    fun xorTransform(bytes: ByteArray, key: ByteArray): ByteArray =
        xorTransformNative(bytes, key)

    fun evaluatePolicy(blockEmulators: Boolean, isPhysical: Boolean, isCompromised: Boolean): Int =
        evaluatePolicyNative(
            if (blockEmulators) 1 else 0,
            if (isPhysical) 1 else 0,
            if (isCompromised) 1 else 0,
        )

    fun getTotalRamMb(): Int = getTotalRamMbNative()

    fun defaultStatusUrl(): String = defaultStatusUrlNative()

    fun isAllowedStatusUrl(url: String): Boolean = isAllowedStatusUrlNative(url)
}

object PolicyLock {
    const val ALLOW = 0
    const val EMULATOR = 1
    const val COMPROMISED = 2
}
