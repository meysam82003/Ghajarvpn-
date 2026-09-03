package com.ghajarvpn.downloads

import java.util.UUID

enum class DownloadState { QUEUED, PROBING, DOWNLOADING, PAUSED, COMPLETED, FAILED, CANCELED }

data class DownloadTask(
    val id: String = UUID.randomUUID().toString(),
    val url: String,
    val fileName: String,
    val mimeType: String = "application/octet-stream",
    val headerToken: String = "",
    val state: DownloadState = DownloadState.QUEUED,
    val downloadedBytes: Long = 0,
    val totalBytes: Long = -1,
    val speedBytesPerSecond: Long = 0,
    val requestedConnections: Int = 0,
    val activeConnections: Int = 1,
    val priority: Int = 0,
    val retries: Int = 0,
    val wifiOnly: Boolean = false,
    val expectedSha256: String = "",
    val proxyPort: Int = 0,
    val outputUri: String = "",
    val error: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class EnqueueRequest(
    val url: String,
    val fileName: String,
    val mimeType: String,
    val headers: Map<String, String>,
    val requestedConnections: Int = 0,
    val priority: Int = 0,
    val wifiOnly: Boolean = false,
    val expectedSha256: String = "",
    val proxyPort: Int = 0
)
