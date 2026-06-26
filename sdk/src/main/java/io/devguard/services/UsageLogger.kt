package io.devguard.services

import org.json.JSONArray
import org.json.JSONObject

internal class UsageLogger(private val storage: SecureStorageService) {
    private val maxEvents = 100

    fun logEvent(type: String, data: Map<String, Any?> = emptyMap()) {
        val logs = getLogs().toMutableList()
        logs.add(
            mapOf(
                "type" to type,
                "timestamp" to System.currentTimeMillis(),
                "data" to data,
            ),
        )
        while (logs.size > maxEvents) {
            logs.removeAt(0)
        }
        storage.write(
            io.devguard.core.DevGuardConstants.USAGE_LOGS_KEY,
            JSONArray(logs.map { JSONObject(it) }).toString(),
        )
    }

    fun getLogs(): List<Map<String, Any?>> {
        val raw = storage.read(io.devguard.core.DevGuardConstants.USAGE_LOGS_KEY) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                obj.toMap()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun clearLogs() {
        storage.delete(io.devguard.core.DevGuardConstants.USAGE_LOGS_KEY)
    }

    private fun JSONObject.toMap(): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        keys().forEach { key -> map[key] = get(key) }
        return map
    }
}
