package com.netra.smartcane

import android.graphics.Bitmap
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var tvIpAddress: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvLastSignal: TextView
    private lateinit var tvLogs: TextView

    private var serverSocket: ServerSocket? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val SERVER_PORT = 8080

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvIpAddress = findViewById(R.id.tvIpAddress)
        tvStatus = findViewById(R.id.tvStatus)
        tvLastSignal = findViewById(R.id.tvLastSignal)
        tvLogs = findViewById(R.id.tvLogs)

        tvIpAddress.text = "Phone IP: ${getWifiIpAddress()}"
        startServer()
    }

    private fun startServer() {
        scope.launch {
            try {
                serverSocket = ServerSocket(SERVER_PORT)
                withContext(Dispatchers.Main) {
                    tvStatus.text = "Pipeline Server: RUNNING on port $SERVER_PORT"
                    addLog("Pipeline ready for Cane triggers.")
                }

                while (isActive) {
                    val socket = serverSocket?.accept() ?: break
                    launch { handleIncomingSocket(socket) }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    tvStatus.text = "Server Error: ${e.message}"
                }
            }
        }
    }

    private suspend fun handleIncomingSocket(socket: Socket) {
        val piIp = socket.inetAddress.hostAddress ?: "192.168.43.1"
        var command = "UNKNOWN"

        try {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val output: OutputStream = socket.getOutputStream()

            var line: String?
            var contentLength = 0

            // Read HTTP Header
            while (reader.readLine().also { line = it } != null) {
                if (line!!.isEmpty()) break
                if (line!!.lowercase(Locale.ROOT).startsWith("content-length:")) {
                    contentLength = line!!.substring(15).trim().toIntOrNull() ?: 0
                }
            }

            // Read JSON Body
            if (contentLength > 0) {
                val bodyChars = CharArray(contentLength)
                reader.read(bodyChars, 0, contentLength)
                val body = String(bodyChars)
                val json = JSONObject(body)
                command = json.optString("command", "UNKNOWN")
            }

            // Reply 200 OK immediately so the Pi can close its connection
            val response = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: 20\r\n\r\n{\"status\":\"received\"}"
            output.write(response.toByteArray(Charsets.UTF_8))
            output.flush()

            withContext(Dispatchers.Main) {
                tvLastSignal.text = "Last Signal: $command"
                addLog("Command '$command' from $piIp")
            }

            // Route to your functional modules
            dispatchPipeline(command, piIp)

        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                addLog("Socket Read Error: ${e.message}")
            }
        } finally {
            socket.close()
        }
    }

    private suspend fun dispatchPipeline(command: String, piIp: String) {
        when (command) {
            "SOS" -> {
                withContext(Dispatchers.Main) {
                    addLog("[ACTION] Triggering SOS Module...")
                    // >>> CALL YOUR EXISTING SOS FUNCTION HERE <<<
                    executeSosAction()
                }
            }
            "OBJECT", "OCR" -> {
                withContext(Dispatchers.Main) {
                    addLog("[$command] Fetching 6 burst frames from Pi...")
                }

                // Pull frames over LAN from Pi port 8000
                val transfer = PiFrameTransfer(piIp = piIp, piPort = 8000)

                // Wait until camera capture is finished on Pi
                var status = transfer.fetchPiStatus()
                val startTime = System.currentTimeMillis()
                while (status != "done" && (System.currentTimeMillis() - startTime) < PiFrameTransfer.STATUS_TIMEOUT_MS) {
                    if (status == "error") {
                        withContext(Dispatchers.Main) { addLog("[ERROR] Pi camera failed.") }
                        return
                    }
                    delay(PiFrameTransfer.STATUS_POLL_INTERVAL_MS)
                    status = transfer.fetchPiStatus()
                }

                val frames: List<Bitmap> = transfer.downloadAllFrames()
                withContext(Dispatchers.Main) {
                    addLog("[$command] Downloaded ${frames.size} frames.")
                    if (command == "OBJECT") {
                        // >>> CALL YOUR EXISTING OBJECT DETECTION FUNCTION HERE <<<
                        executeObjectDetection(frames)
                    } else {
                        // >>> CALL YOUR EXISTING OCR FUNCTION HERE <<<
                        executeOcr(frames)
                    }
                }
            }
        }
    }

    // =========================================================================
    // HOOKS: Plug your existing modules into these three functions
    // =========================================================================
    private fun executeSosAction() {
        // Hook to your existing SOS calling/SMS method
        addLog("-> Executed: SOS Function")
    }

    private fun executeObjectDetection(frames: List<Bitmap>) {
        // Hook to your existing Object Detection / YOLO model
        // Pass 'frames' to your model and send result to your TTS function
        addLog("-> Executed: Object Detection on ${frames.size} frames")
    }

    private fun executeOcr(frames: List<Bitmap>) {
        // Hook to your existing OCR model
        // Pass 'frames' to your text recognizer and send result to your TTS function
        addLog("-> Executed: OCR on ${frames.size} frames")
    }

    private fun addLog(msg: String) {
        val time = java.text.SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(java.util.Date())
        tvLogs.append("$time - $msg\n")
    }

    private fun getWifiIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            for (intf in interfaces) {
                val addrs = intf.inetAddresses
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                        return addr.hostAddress ?: "127.0.0.1"
                    }
                }
            }
        } catch (e: Exception) {}
        return "127.0.0.1"
    }

    override fun onDestroy() {
        scope.cancel()
        serverSocket?.close()
        super.onDestroy()
    }
}
