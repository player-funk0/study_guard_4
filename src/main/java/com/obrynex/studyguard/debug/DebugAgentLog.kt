package com.obrynex.studyguard.debug

import com.obrynex.studyguard.BuildConfig
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Debug-session logger — posts NDJSON to the Cursor debug ingest server (debug builds only).
 */
object DebugAgentLog {
    private const val ENDPOINT =
        "http://10.0.2.2:7544/ingest/bd9c2dc9-d0d6-4b0f-b6d3-53912e89e41a"
    private const val SESSION_ID = "bb5748"
    private val executor = Executors.newSingleThreadExecutor()

    fun log(
        location: String,
        message: String,
        hypothesisId: String,
        data: Map<String, Any?> = emptyMap(),
        runId: String = "pre-fix"
    ) {
        if (!BuildConfig.DEBUG) return
        val payload = JSONObject().apply {
            put("sessionId", SESSION_ID)
            put("runId", runId)
            put("hypothesisId", hypothesisId)
            put("location", location)
            put("message", message)
            put("timestamp", System.currentTimeMillis())
            put("data", JSONObject(data))
        }
        executor.execute {
            runCatching {
                val conn = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("X-Debug-Session-Id", SESSION_ID)
                    doOutput = true
                    connectTimeout = 2_000
                    readTimeout = 2_000
                }
                conn.outputStream.use { it.write(payload.toString().toByteArray()) }
                conn.inputStream.use { it.readBytes() }
                conn.disconnect()
            }
        }
    }
}
