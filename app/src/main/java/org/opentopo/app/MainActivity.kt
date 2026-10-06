package org.opentopo.app

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.app.PictureInPictureParams
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.opentopo.app.db.AppDatabase
import org.opentopo.app.gnss.BluetoothGnssService
import org.opentopo.app.gnss.GnssState
import org.opentopo.app.gnss.InternalGnssService
import org.opentopo.app.gnss.UsbGnssService
import org.opentopo.app.ntrip.NtripClient
import org.opentopo.app.ntrip.NtripProfileRepository
import org.opentopo.app.survey.Stakeout
import org.opentopo.app.survey.SurveyManager
import org.opentopo.app.ui.MainMapScreen
import org.opentopo.app.ui.theme.OpenTopoTheme

class MainActivity : ComponentActivity() {

    companion object {
        private const val ACTION_USB_PERMISSION = "org.opentopo.app.USB_PERMISSION"
    }

    private val gnssState = GnssState()
    private lateinit var bluetoothService: BluetoothGnssService
    private lateinit var usbService: UsbGnssService
    private lateinit var internalService: InternalGnssService
    private lateinit var ntripClient: NtripClient
    private lateinit var ntripProfileRepo: NtripProfileRepository
    private lateinit var db: AppDatabase
    lateinit var prefs: org.opentopo.app.prefs.UserPreferences
        private set
    private var surveyManager: SurveyManager? = null
    private var stakeout: Stakeout? = null
    private lateinit var coordinateSystemService: org.opentopo.app.coordinates.CoordinateSystemService

    /** True when the activity is in picture-in-picture mode. */
    private val _isInPipMode = MutableStateFlow(false)
    val isInPipMode: StateFlow<Boolean> = _isInPipMode

    /** Incremented on USB attach/detach to trigger recomposition of device list. */
    private val _usbDeviceVersion = MutableStateFlow(0)
    val usbDeviceVersion: StateFlow<Int> = _usbDeviceVersion

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* UI reacts via state */ }

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    _usbDeviceVersion.value++
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    _usbDeviceVersion.value++
                }
                ACTION_USB_PERMISSION -> {
                    _usbDeviceVersion.value++
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        db = AppDatabase.getInstance(this)
        prefs = org.opentopo.app.prefs.UserPreferences(this)
        bluetoothService = BluetoothGnssService(this, gnssState)
        usbService = UsbGnssService(this, gnssState)
        internalService = InternalGnssService(this, gnssState)

        // Mutual exclusion: only one transport may own the GNSS feed at a
        // time. Whoever is about to connect first tears down the other two —
        // otherwise an idle transport (e.g. internal GPS still registered as
        // an NMEA listener) keeps writing into the shared GnssState and the
        // satellite/accuracy stats flicker.
        bluetoothService.onBeforeConnect = {
            usbService.disconnect()
            internalService.disconnect()
        }
        usbService.onBeforeConnect = {
            bluetoothService.disconnect()
            internalService.disconnect()
        }
        internalService.onBeforeConnect = {
            bluetoothService.disconnect()
            usbService.disconnect()
        }

        // NTRIP RTCM data flows to whichever transport is connected
        ntripClient = NtripClient { rtcmData ->
            bluetoothService.write(rtcmData)
            usbService.write(rtcmData)
            // Feed position and raw GGA to NTRIP for VRS
            val pos = gnssState.position.value
            ntripClient.lastRawGga = gnssState.lastRawGga
            ntripClient.updatePosition(
                pos.latitude, pos.longitude, pos.altitude ?: 0.0,
                pos.fixQuality, pos.numSatellites, pos.hdop ?: 1.0,
            )
        }

        // NTRIP profile repository — owns saved profiles and ensures the
        // Brazilian IBGE RBMC-IP template is available.
        ntripProfileRepo = NtripProfileRepository(this, db, ntripClient, prefs)
        lifecycleScope.launch { ntripProfileRepo.seedIfEmpty() }

        // Brazilian build: SIRGAS2000 / UTM does not require correction grids.
        coordinateSystemService = org.opentopo.app.coordinates.CoordinateSystemService(null)
        surveyManager = SurveyManager(db, gnssState, coordinateSystemService)
        stakeout = Stakeout(gnssState, coordinateSystemService)

        // Feed GNSS position to NtripClient continuously for GGA generation
        CoroutineScope(Dispatchers.Default).launch {
            gnssState.position.collect { pos ->
                ntripClient.lastRawGga = gnssState.lastRawGga
                ntripClient.updatePosition(
                    pos.latitude, pos.longitude, pos.altitude ?: 0.0,
                    pos.fixQuality, pos.numSatellites, pos.hdop ?: 1.0,
                )
            }
        }

        // Apply saved settings to services reactively
        CoroutineScope(Dispatchers.Default).launch {
            launch {
                prefs.averagingSeconds.collect { seconds ->
                    surveyManager?.averagingSeconds = seconds
                }
            }
            launch {
                prefs.minAccuracyM.collect { value ->
                    surveyManager?.minAccuracyM = value.toDoubleOrNull() ?: 0.05
                }
            }
            launch {
                prefs.requireRtkFix.collect { required ->
                    surveyManager?.requireRtkFix = required
                }
            }
            launch {
                prefs.ggaIntervalSeconds.collect { seconds ->
                    ntripClient.ggaIntervalMs = seconds * 1000L
                }
            }
        }

        requestPermissionsIfNeeded()
        registerUsbReceiver()

        setContent {
            val gloveMode by prefs.gloveMode.collectAsState(initial = false)
            OpenTopoTheme(gloveMode = gloveMode) {
                MainMapScreen(
                    gnssState = gnssState,
                    bluetoothService = bluetoothService,
                    usbService = usbService,
                    internalService = internalService,
                    ntripClient = ntripClient,
                    ntripProfileRepo = ntripProfileRepo,
                    db = db,
                    surveyManager = surveyManager,
                    stakeout = stakeout,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        // Handle USB device attached via intent (app launched by USB plug)
        handleUsbIntent(intent)

        // Auto-reconnect from saved settings
        lifecycleScope.launch {
            // Small delay to let USB devices enumerate
            kotlinx.coroutines.delay(2000)

            // Auto-connect USB if previously used
            val savedConnectionType = prefs.connectionType.first()
            if (savedConnectionType == 1) { // USB
                val drivers = usbService.getAvailableDevices()
                if (drivers.isNotEmpty()) {
                    val savedBaud = prefs.baudRate.first()
                    usbService.connect(drivers.first(), savedBaud)
                }
            }
            // Internal GPS (connectionType == 2) does NOT auto-connect —
            // requires explicit user action due to permission dialog

            // NTRIP auto-connect is now owned by NtripProfileRepository — it
            // observes the active profile Flow and calls connect/disconnect
            // on the transport whenever the user switches profiles.
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // Glove mode: Volume Up = Record Point, Volume Down = Undo
        val isGlove = kotlinx.coroutines.runBlocking {
            prefs.gloveMode.first()
        }
        if (isGlove) {
            when (keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> {
                    surveyManager?.startRecording()
                    return true
                }
                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    surveyManager?.cancelRecording()
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleUsbIntent(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        ntripClient.disconnect()
        bluetoothService.disconnect()
        usbService.destroy()
        internalService.disconnect()
        try {
            unregisterReceiver(usbReceiver)
        } catch (_: IllegalArgumentException) {
        }
    }

    private fun handleUsbIntent(intent: Intent) {
        if (intent.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) {
            _usbDeviceVersion.value++
        }
    }

    private fun registerUsbReceiver() {
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            addAction(ACTION_USB_PERMISSION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(usbReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(usbReceiver, filter)
        }
    }

    /** Enter picture-in-picture mode for compact stakeout display. */
    fun enterPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(1, 1))
                .build()
            enterPictureInPictureMode(params)
        }
    }

    @Suppress("DEPRECATION")
    @Deprecated("Deprecated in API 33", ReplaceWith("onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)"))
    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode)
        _isInPipMode.value = isInPictureInPictureMode
    }

    /** Force a refresh of the USB device list. */
    fun refreshUsbDevices() {
        _usbDeviceVersion.value++
    }

    /** Request USB permission for a specific device. */
    fun requestUsbPermission(device: UsbDevice) {
        val usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
        if (!usbManager.hasPermission(device)) {
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                PendingIntent.FLAG_MUTABLE
            else
                0
            val intent = Intent(ACTION_USB_PERMISSION).apply {
                setPackage(packageName)
            }
            val permIntent = PendingIntent.getBroadcast(
                this, 0, intent, flags
            )
            usbManager.requestPermission(device, permIntent)
        }
    }

    private fun requestPermissionsIfNeeded() {
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED
            ) needed.add(Manifest.permission.BLUETOOTH_CONNECT)
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                != PackageManager.PERMISSION_GRANTED
            ) needed.add(Manifest.permission.BLUETOOTH_SCAN)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) needed.add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }
}
