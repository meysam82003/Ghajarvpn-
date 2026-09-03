package com.ghajarvpn.downloads

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class DownloadRepository private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("ghajar_download_tasks", Context.MODE_PRIVATE)
    private val vault = DownloadHeaderVault(context)
    private val lock = Any()

    fun enqueue(request: EnqueueRequest): DownloadTask? = synchronized(lock) {
        if (!DownloadPlanning.safeHttpUrl(request.url)) return null
        val current = readAll()
        current.firstOrNull { it.url == request.url && it.state in ACTIVE_STATES }?.let { return it }
        val existingNames = current.mapTo(mutableSetOf()) { it.fileName }
        val taskId = java.util.UUID.randomUUID().toString()
        val safeName = DownloadNames.collisionFree(DownloadNames.sanitize(request.fileName), existingNames)
        val headerToken = vault.put(taskId, request.headers)
        val task = DownloadTask(
            id = taskId, url = request.url, fileName = safeName, mimeType = request.mimeType.ifBlank { "application/octet-stream" },
            headerToken = headerToken, requestedConnections = request.requestedConnections.coerceIn(0, 8),
            priority = request.priority.coerceIn(-10, 10), wifiOnly = request.wifiOnly,
            expectedSha256 = request.expectedSha256.trim().lowercase().takeIf { it.matches(Regex("[0-9a-f]{64}")) }.orEmpty()
        )
        writeAll(current + task)
        task
    }

    fun all(): List<DownloadTask> = synchronized(lock) { readAll().sortedWith(compareByDescending<DownloadTask> { it.priority }.thenByDescending { it.createdAt }) }
    fun get(id: String): DownloadTask? = synchronized(lock) { readAll().firstOrNull { it.id == id } }
    internal fun headers(task: DownloadTask): Map<String, String> = vault.get(task.headerToken)
    internal fun clearHeaders(task: DownloadTask) = vault.remove(task.headerToken)

    fun update(id: String, transform: (DownloadTask) -> DownloadTask): DownloadTask? = synchronized(lock) {
        val values = readAll().toMutableList()
        val index = values.indexOfFirst { it.id == id }
        if (index < 0) return null
        values[index] = transform(values[index]).copy(updatedAt = System.currentTimeMillis())
        writeAll(values)
        values[index]
    }

    fun remove(id: String): Boolean = synchronized(lock) {
        val values = readAll().toMutableList()
        val task = values.firstOrNull { it.id == id } ?: return false
        values.remove(task); writeAll(values); vault.remove(task.headerToken); true
    }

    private fun readAll(): List<DownloadTask> = runCatching {
        val array = JSONArray(prefs.getString(KEY_TASKS, "[]"))
        List(array.length()) { index -> array.getJSONObject(index).toTask() }
    }.getOrDefault(emptyList())

    private fun writeAll(tasks: List<DownloadTask>) {
        val array = JSONArray(); tasks.forEach { array.put(it.toJson()) }
        prefs.edit().putString(KEY_TASKS, array.toString()).commit()
    }

    private fun DownloadTask.toJson() = JSONObject().apply {
        put("id", id); put("url", url); put("fileName", fileName); put("mimeType", mimeType); put("headerToken", headerToken)
        put("state", state.name); put("downloadedBytes", downloadedBytes); put("totalBytes", totalBytes); put("speed", speedBytesPerSecond)
        put("requestedConnections", requestedConnections); put("activeConnections", activeConnections); put("priority", priority); put("retries", retries)
        put("wifiOnly", wifiOnly); put("expectedSha256", expectedSha256); put("outputUri", outputUri); put("error", error)
        put("createdAt", createdAt); put("updatedAt", updatedAt)
    }

    private fun JSONObject.toTask() = DownloadTask(
        id = getString("id"), url = getString("url"), fileName = getString("fileName"), mimeType = optString("mimeType", "application/octet-stream"),
        headerToken = optString("headerToken"), state = runCatching { DownloadState.valueOf(optString("state")) }.getOrDefault(DownloadState.FAILED),
        downloadedBytes = optLong("downloadedBytes"), totalBytes = optLong("totalBytes", -1), speedBytesPerSecond = optLong("speed"),
        requestedConnections = optInt("requestedConnections"), activeConnections = optInt("activeConnections", 1), priority = optInt("priority"),
        retries = optInt("retries"), wifiOnly = optBoolean("wifiOnly"), expectedSha256 = optString("expectedSha256"),
        outputUri = optString("outputUri"), error = optString("error"), createdAt = optLong("createdAt"), updatedAt = optLong("updatedAt")
    )

    companion object {
        private const val KEY_TASKS = "tasks_v1"
        private val ACTIVE_STATES = setOf(DownloadState.QUEUED, DownloadState.PROBING, DownloadState.DOWNLOADING, DownloadState.PAUSED)
        @Volatile private var instance: DownloadRepository? = null
        fun get(context: Context): DownloadRepository = instance ?: synchronized(this) {
            instance ?: DownloadRepository(context.applicationContext).also { instance = it }
        }
    }
}
