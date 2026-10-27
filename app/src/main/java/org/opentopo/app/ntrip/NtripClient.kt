package org.opentopo.app.ntrip

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.net.SocketTimeoutException
import javax.net.ssl.SSLSocketFactory
import java.util.Base64
import kotlin.math.abs
import kotlin.math.floor

/**
 * NTRIP v1/v2 client using raw sockets for bidirectional communication.
 *
 * Connects to an NTRIP caster, sends GGA for VRS position,
 * receives RTCM3 corrections, and forwards them via [onRtcmData].
 */
class NtripClient(
    private val onRtcmData: (ByteArray) -> Unit,
) {
    companion object {
        private const val TAG = "NtripClient"
        private const val READ_BUFFER_SIZE = 4096
        private const val RECONNECT_BASE_DELAY_MS = 2000L
        private const val RECONNECT_MAX_DELAY_MS = 60000L
        private const val USER_AGENT = "NTRIP OpenTopo/0.1"
        private const val GGA_INTERVAL_MS = 10_000L
    }

    private val scope = CoroutineScope(Dispatchers.IO)
    private var connectionJob: Job? = null
    private var ggaJob: Job? = null
    private var socket: Socket? = null
    private var socketOutput: OutputStream? = null

    private val _state = MutableStateFlow(NtripState())
    val state: StateFlow<NtripState> = _state.asStateFlow()

    private var currentConfig: NtripConfig? = null

    /** GGA forwarding interval in milliseconds (configurable from settings). */
    @Volatile var ggaIntervalMs: Long = GGA_INTERVAL_MS

    /** Last raw GGA from the GNSS receiver. */
    @Volatile var lastRawGga: String? = null

    /** Current position for synthetic GGA generation. */
    @Volatile var currentLat: Double = 0.0
    @Volatile var currentLon: Double = 0.0
    @Volatile var currentAlt: Double = 0.0
    @Volatile var currentFixQuality: Int = 0
    @Volatile var currentNumSats: Int = 0
    @Volatile var currentHdop: Double = 1.0

    /** Update position from GnssState for GGA generation. */
    fun updatePosition(lat: Double, lon: Double, alt: Double, fixQuality: Int, numSats: Int, hdop: Double) {
        currentLat = lat
        currentLon = lon
        currentAlt = alt
        currentFixQuality = fixQuality
        currentNumSats = numSats
        currentHdop = hdop
    }

    /** Fetch the sourcetable from a caster. */
    fun fetchSourcetable(config: NtripConfig, callback: (Result<List<NtripMountpoint>>) -> Unit) {
        scope.launch {
            try {
                val result = doFetchSourcetable(config)
                callback(Result.success(result))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "fetchSourcetable failed", e)
                callback(Result.failure(e))
            }
        }
    }

    /** Connect to a caster/mountpoint. */
    fun connect(config: NtripConfig) {
        disconnect()
        currentConfig = config
        _state.value = NtripState(status = NtripStatus.CONNECTING)
        connectionJob = scope.launch { connectLoop(config) }
    }

    /** Disconnect from the caster. */
    fun disconnect() {
        ggaJob?.cancel()
        ggaJob = null
        connectionJob?.cancel()
        connectionJob = null
        closeConnection()
        currentConfig = null
        _state.value = NtripState(status = NtripStatus.DISCONNECTED)
    }

    // ── Connection loop with auto-reconnect ──

    private suspend fun connectLoop(config: NtripConfig) {
        var reconnectDelay = RECONNECT_BASE_DELAY_MS

        while (currentCoroutineContext().isActive) {
            try {
                doConnect(config)
                reconnectDelay = RECONNECT_BASE_DELAY_MS
                if (config.sendGga) startGgaForwarding()
                readRtcmStream()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "connection error: ${e.message}")
                _state.value = _state.value.copy(
                    status = NtripStatus.RECONNECTING,
                    error = e.message,
                )
            }

            closeConnection()
            ggaJob?.cancel()

            if (!currentCoroutineContext().isActive) break

            delay(reconnectDelay)
            reconnectDelay = (reconnectDelay * 2).coerceAtMost(RECONNECT_MAX_DELAY_MS)
        }
    }

    private fun doConnect(config: NtripConfig) {
        Log.d(TAG, "connecting to ${config.host}:${config.port}/${config.mountpoint}")

        val sock = createSocket(config, 60_000)
        socket = sock

        val output = sock.getOutputStream()
        socketOutput = output

        // Send NTRIP v1 request (HTTP/1.0 for max compatibility)
        val request = buildString {
            append("GET /${config.mountpoint} HTTP/1.0\r\n")
            append("User-Agent: $USER_AGENT\r\n")
            if (config.username.isNotBlank()) {
                val credentials = "${config.username}:${config.password}"
                val encoded = Base64.getEncoder().encodeToString(credentials.toByteArray())
                append("Authorization: Basic $encoded\r\n")
            }
            append("\r\n")
        }
        output.write(request.toByteArray())
        output.flush()

        // Read response — handle both ICY 200 OK (v1) and HTTP/1.1 200 OK (v2)
        val input = sock.getInputStream()
        val responseLine = readLine(input)
        Log.d(TAG, "response: $responseLine")

        if (responseLine == null) {
            throw IOException("No response from NTRIP caster")
        }
        if (responseLine.startsWith("SOURCETABLE")) {
            throw IOException("Mountpoint '${config.mountpoint}' not found")
        }
        if (!responseLine.contains("200")) {
            throw IOException("NTRIP: $responseLine")
        }

        // For NTRIP v2 (HTTP/1.1), skip remaining headers until blank line.
        // For NTRIP v1 (ICY 200 OK), there are no additional headers —
        // data starts immediately after the response line.
        if (responseLine.startsWith("HTTP/")) {
            while (true) {
                val header = readLine(input) ?: break
                if (header.isBlank()) break
            }
        }
        // For ICY responses, data starts immediately — do NOT read more lines.

        _state.value = _state.value.copy(
            status = NtripStatus.CONNECTED,
            mountpoint = config.mountpoint,
            error = null,
        )

        // Send initial GGA only when enabled for this profile.
        if (config.sendGga) sendGga(output)
    }

    /** Build and send a GGA sentence. Uses raw GGA if available, otherwise generates one. */
    private fun sendGga(output: OutputStream) {
        val gga = lastRawGga ?: buildSyntheticGga()
        if (gga != null) {
            try {
                output.write("$gga\r\n".toByteArray())
                output.flush()
                Log.d(TAG, "sent GGA: ${gga.take(40)}...")
            } catch (e: IOException) {
                Log.w(TAG, "failed to send GGA: ${e.message}")
            }
        } else {
            Log.w(TAG, "no GGA available — VRS caster may not send data")
        }
    }

    /** Generate a synthetic GGA from current position if we have a fix. */
    private fun buildSyntheticGga(): String? {
        if (currentFixQuality == 0 || (currentLat == 0.0 && currentLon == 0.0)) return null

        val time = java.text.SimpleDateFormat("HHmmss.00", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }.format(java.util.Date())

        val latDeg = floor(abs(currentLat))
        val latMin = (abs(currentLat) - latDeg) * 60.0
        val latStr = "%02.0f%09.6f".format(latDeg, latMin)
        val latHem = if (currentLat >= 0) "N" else "S"

        val lonDeg = floor(abs(currentLon))
        val lonMin = (abs(currentLon) - lonDeg) * 60.0
        val lonStr = "%03.0f%09.6f".format(lonDeg, lonMin)
        val lonHem = if (currentLon >= 0) "E" else "W"

        val body = "GPGGA,$time,$latStr,$latHem,$lonStr,$lonHem,$currentFixQuality,$currentNumSats,${"%.1f".format(currentHdop)},${"%.1f".format(currentAlt)},M,0.0,M,,"
        var checksum = 0
        for (c in body) checksum = checksum xor c.code
        return "\$$body*${"%02X".format(checksum)}"
    }

    private fun readLine(input: InputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val b = input.read()
            if (b == -1) return if (sb.isEmpty()) null else sb.toString()
            val c = b.toChar()
            if (c == '\n') return sb.toString().trimEnd('\r')
            sb.append(c)
        }
    }

    private suspend fun readRtcmStream() {
        val sock = socket ?: return
        val input = BufferedInputStream(sock.getInputStream(), READ_BUFFER_SIZE)
        val buffer = ByteArray(READ_BUFFER_SIZE)
        var totalBytes = 0L
        val startTime = System.currentTimeMillis()

        while (currentCoroutineContext().isActive) {
            val bytesRead = input.read(buffer)
            if (bytesRead == -1) break
            if (bytesRead > 0) {
                totalBytes += bytesRead
                val elapsed = (System.currentTimeMillis() - startTime) / 1000.0
                val dataRate = if (elapsed > 0) (totalBytes / elapsed).toLong() else 0L

                onRtcmData(buffer.copyOf(bytesRead))

                _state.value = _state.value.copy(
                    bytesReceived = totalBytes,
                    dataRateBps = dataRate,
                    lastDataTime = System.currentTimeMillis(),
                )
            }
        }
    }

    private fun startGgaForwarding() {
        ggaJob?.cancel()
        ggaJob = scope.launch {
            while (isActive) {
                delay(ggaIntervalMs)
                val output = socketOutput ?: continue
                sendGga(output)
            }
        }
    }

    private fun closeConnection() {
        try { socketOutput?.close() } catch (_: IOException) {}
        try { socket?.close() } catch (_: IOException) {}
        socketOutput = null
        socket = null
    }

    // ── Sourcetable (simple HTTP GET, no bidirectional needed) ──

    private fun doFetchSourcetable(config: NtripConfig): List<NtripMountpoint> {
        val sock = createSocket(config, 10_000)
        try {
            val output = sock.getOutputStream()
            val request = buildString {
                append("GET / HTTP/1.0\r\n")
                append("User-Agent: $USER_AGENT\r\n")
                append("Ntrip-Version: Ntrip/2.0\r\n")
                if (config.username.isNotBlank()) {
                    val credentials = "${config.username}:${config.password}"
                    val encoded = Base64.getEncoder().encodeToString(credentials.toByteArray())
                    append("Authorization: Basic $encoded\r\n")
                }
                append("\r\n")
            }
            output.write(request.toByteArray())
            output.flush()

            val input = sock.getInputStream()
            val statusLine = readLine(input)
                ?: throw IOException("No response from NTRIP caster")

            val httpOk = statusLine.startsWith("HTTP/") && statusLine.contains("200")
            val tableOk = statusLine.startsWith("SOURCETABLE") && statusLine.contains("200")
            val icyOk = statusLine.startsWith("ICY ") && statusLine.contains("200")
            if (!(httpOk || tableOk || icyOk)) {
                throw IOException("NTRIP sourcetable: $statusLine")
            }

            if (statusLine.startsWith("HTTP/")) {
                while (true) {
                    val header = readLine(input) ?: break
                    if (header.isBlank()) break
                }
            }

            val body = StringBuilder()
            try {
                while (true) {
                    val line = readLine(input) ?: break
                    if (line.startsWith("STR;")) {
                        body.append(line).append('\n')
                    }
                    if (line.startsWith("ENDSOURCETABLE")) break
                }
            } catch (_: SocketTimeoutException) {
                // NTRIP v1 casters may keep the connection open after the table.
            }
            return parseSourcetable(body.toString())
        } finally {
            try { sock.close() } catch (_: IOException) {}
        }
    }

    private fun createSocket(config: NtripConfig, timeoutMs: Int): Socket {
        val sock = if (config.useTls) {
            SSLSocketFactory.getDefault().createSocket(config.host, config.port) as Socket
        } else {
            Socket(config.host, config.port)
        }
        sock.soTimeout = timeoutMs
        return sock
    }

    private fun parseSourcetable(text: String): List<NtripMountpoint> {
        return text.lineSequence()
            .filter { it.startsWith("STR;") }
            .mapNotNull { line ->
                val fields = line.split(";")
                if (fields.size < 19) return@mapNotNull null
                NtripMountpoint(
                    name = fields[1],
                    identifier = fields[2],
                    format = fields[3],
                    formatDetails = fields[4],
                    carrier = fields[5].toIntOrNull() ?: 0,
                    navSystem = fields[6],
                    network = fields[7],
                    country = fields[8],
                    latitude = fields[9].toDoubleOrNull() ?: 0.0,
                    longitude = fields[10].toDoubleOrNull() ?: 0.0,
                    nmea = fields[11] == "1",
                    solution = fields[12] == "1",
                    generator = fields[13],
                    compression = fields[14],
                    authentication = fields[15],
                    fee = fields[16] == "Y",
                    bitrate = fields[17].toIntOrNull() ?: 0,
                )
            }
            .toList()
    }
}

// ── State ──

data class NtripState(
    val status: NtripStatus = NtripStatus.DISCONNECTED,
    val mountpoint: String = "",
    val bytesReceived: Long = 0,
    val dataRateBps: Long = 0,
    val lastDataTime: Long = 0,
    val error: String? = null,
) {
    val ageOfCorrectionSeconds: Long
        get() = if (lastDataTime > 0) {
            (System.currentTimeMillis() - lastDataTime) / 1000
        } else {
            -1
        }

    val dataRateFormatted: String
        get() = when {
            dataRateBps > 1024 -> "%.1f KB/s".format(dataRateBps / 1024.0)
            else -> "$dataRateBps B/s"
        }
}

enum class NtripStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
}
