package com.netra.smartcane

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONObject
import java.io.BufferedInputStream
import java.net.HttpURLConnection
import java.net.URL

class PiFrameTransfer(
    private val piIp: String,
    private val piPort: Int = 8000
) {
    companion object {
        const val TOTAL_FRAMES = 6
        const val STATUS_TIMEOUT_MS = 30000L
        const val STATUS_POLL_INTERVAL_MS = 250L
    }

    fun formatPiBaseUrl(): String {
        var address = piIp.trim()
        if (address.startsWith("http://")) address = address.removePrefix("http://")
        if (address.startsWith("https://")) address = address.removePrefix("https://")
        return if (address.contains(":")) "http://$address" else "http://$address:$piPort"
    }

    fun fetchPiStatus(): String {
        val url = URL("${formatPiBaseUrl()}/status")
        val connection = url.openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.useCaches = false
            connection.setRequestProperty("Cache-Control", "no-cache")
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return "unknown"
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(response)
            json.optString("status", "unknown")
        } catch (e: Exception) {
            "unknown"
        } finally {
            connection.disconnect()
        }
    }

    fun downloadFrame(frameIndex: Int): Bitmap? {
        if (frameIndex !in 0 until TOTAL_FRAMES) return null
        val frameName = String.format("frame_%02d.jpg", frameIndex)
        val url = URL("${formatPiBaseUrl()}/$frameName?t=${System.currentTimeMillis()}")
        val connection = url.openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 15000
            connection.useCaches = false
            connection.setRequestProperty("Cache-Control", "no-cache, no-store")
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            BufferedInputStream(connection.inputStream).use { input ->
                BitmapFactory.decodeStream(input)
            }
        } catch (e: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    fun downloadAllFrames(): List<Bitmap> {
        val frames = mutableListOf<Bitmap>()
        for (i in 0 until TOTAL_FRAMES) {
            val bitmap = downloadFrame(i)
            if (bitmap != null) frames.add(bitmap)
        }
        return frames
    }
}
