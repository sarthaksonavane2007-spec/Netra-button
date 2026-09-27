package com.netra.smartcane

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.*
import java.util.Locale

class CaneBackgroundService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var serverSocket: ServerSocket? = null
    private var udpSocket: DatagramSocket? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private val HTTP_PORT = 8080
    private val UDP_PORT = 8888
    private val CHANNEL_ID = "netra_cane_channel"

    override fun onCreate() {
        super.onCreate()
        acquireWakeLock()
        startForegroundNotification()
        startUdpDiscoveryResponder()
        startHttpServer()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Netra::CaneWakeLock")
        wakeLock?.acquire(24 * 60 * 60 * 1000L) // Safe 24h timeout
    }

    private fun startForegroundNotification() {
        val channelName = "Netra Cane Service"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel(CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(chan)
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Netra Cane Active")
            .setContentText("Listening for cane triggers in background...")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .build()

        startForeground(1001, notification)
    }

    // 1. AUTO-DISCOVERY: Responds to Pi's broadcast so no IP is hardcoded
    private fun startUdpDiscoveryResponder() {
        scope.launch {
            try {
                udpSocket = DatagramSocket(UDP_PORT)
                val buffer = ByteArray(1024)
                while (isActive) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    udpSocket?.receive(packet)
                    val msg = String(packet.data, 0, packet.length).trim()
                    if (msg == "NETRA_DISCOVERY_PING") {
                        val response = "NETRA_PHONE_ACK".toByteArray()
                        val replyPacket = DatagramPacket(response, response.size, packet.address, packet.port)
                        udpSocket?.send(replyPacket)
                    }
                }
            } catch (e: Exception) {
                // Socket closed or network change
            }
        }
    }

    // 2. HTTP RECEIVER: Listens for triggers from the Pi
    private fun startHttpServer() {
        scope.launch {
            try {
                serverSocket = ServerSocket(HTTP_PORT)
                while (isActive) {
                    val socket = serverSocket?.accept() ?: break
                    launch { handleClient(socket) }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private suspend fun handleClient(socket: Socket) {
        val piIp = socket.inetAddress.hostAddress ?: "192.168.43.1"
        var command = "UNKNOWN"

        try {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val output: OutputStream = socket.getOutputStream()

            var line: String?
            var contentLength = 0
            while (reader.readLine().also { line = it } != null) {
                if (line!!.isEmpty()) break
                if (line!!.lowercase(Locale.ROOT).startsWith("content-length:")) {
                    contentLength = line!!.substring(15).trim().toIntOrNull() ?: 0
                }
            }

            if (contentLength > 0) {
                val body = CharArray(contentLength)
                reader.read(body, 0, contentLength)
                val json = JSONObject(String(body))
                command = json.optString("command", "UNKNOWN")
            }

            val response = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: 20\r\n\r\n{\"status\":\"received\"}"
            output.write(response.toByteArray(Charsets.UTF_8))
            output.flush()

            // Wakes screen and launches MainActivity if desired
            launchMainActivity(command)

            // Route command to the appropriate pipeline
            routeTrigger(command, piIp)

        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            socket.close()
        }
    }

    private fun launchMainActivity(command: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("TRIGGER_COMMAND", command)
        }
        startActivity(intent)
    }

    private suspend fun routeTrigger(command: String, piIp: String) {
        when (command) {
            "SOS" -> {
                // Calls your emergency module directly from background
            }
            "OBJECT", "OCR" -> {
                val transfer = PiFrameTransfer(piIp = piIp, piPort = 8000)
                var status = transfer.fetchPiStatus()
                val start = System.currentTimeMillis()
                while (status != "done" && (System.currentTimeMillis() - start) < 30000L) {
                    delay(250)
                    status = transfer.fetchPiStatus()
                }
                val frames = transfer.downloadAllFrames()
                // Pass frames to your ML models and TTS
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        serverSocket?.close()
        udpSocket?.close()
        wakeLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
