package org.opentopo.app.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "opentopo_prefs")

/**
 * Persists user connection settings across app restarts using DataStore.
 */
class UserPreferences(private val context: Context) {

    // ── GNSS connection ──
    private val KEY_CONNECTION_TYPE = intPreferencesKey("connection_type")
    private val KEY_BT_DEVICE_ADDRESS = stringPreferencesKey("bt_device_address")
    private val KEY_BT_DEVICE_NAME = stringPreferencesKey("bt_device_name")

    // ── NTRIP ──
    private val KEY_NTRIP_PRESET_INDEX = intPreferencesKey("ntrip_preset_index")
    private val KEY_NTRIP_HOST = stringPreferencesKey("ntrip_host")
    private val KEY_NTRIP_PORT = stringPreferencesKey("ntrip_port")
    private val KEY_NTRIP_USERNAME = stringPreferencesKey("ntrip_username")
    private val KEY_NTRIP_PASSWORD = stringPreferencesKey("ntrip_password")
    private val KEY_NTRIP_MOUNTPOINT = stringPreferencesKey("ntrip_mountpoint")

    // ── Survey ──
    private val KEY_ANTENNA_HEIGHT = stringPreferencesKey("antenna_height")
    private val KEY_AVERAGING_SECONDS = intPreferencesKey("averaging_seconds")
    private val KEY_MIN_ACCURACY_M = stringPreferencesKey("min_accuracy_m")
    private val KEY_REQUIRE_RTK_FIX = booleanPreferencesKey("require_rtk_fix")
    private val KEY_BAUD_RATE = intPreferencesKey("baud_rate")

    // ── NTRIP extras ──
    private val KEY_GGA_INTERVAL = intPreferencesKey("gga_interval_seconds")

    // ── Display ──
    private val KEY_COORD_FORMAT = intPreferencesKey("coord_format") // 0=EGSA87, 1=WGS84 decimal, 2=WGS84 DMS
    private val KEY_GLOVE_MODE = booleanPreferencesKey("glove_mode")

    // ── Height/geoid source ──
    // Brazilian build currently prefers the receiver-reported NMEA GGA geoid separation.
    // A native hgeoHNOR2020 grid can be added in a later phase.
    private val KEY_PREFER_RECEIVER_GEOID = booleanPreferencesKey("prefer_receiver_geoid")

    // ── Flows ──

    val connectionType: Flow<Int> = context.dataStore.data.map { it[KEY_CONNECTION_TYPE] ?: 0 }
    val btDeviceAddress: Flow<String> = context.dataStore.data.map { it[KEY_BT_DEVICE_ADDRESS] ?: "" }
    val btDeviceName: Flow<String> = context.dataStore.data.map { it[KEY_BT_DEVICE_NAME] ?: "" }
    val ntripPresetIndex: Flow<Int> = context.dataStore.data.map { it[KEY_NTRIP_PRESET_INDEX] ?: 0 }
    val ntripHost: Flow<String> = context.dataStore.data.map { it[KEY_NTRIP_HOST] ?: "" }
    val ntripPort: Flow<String> = context.dataStore.data.map { it[KEY_NTRIP_PORT] ?: "2101" }
    val ntripUsername: Flow<String> = context.dataStore.data.map { it[KEY_NTRIP_USERNAME] ?: "" }
    val ntripPassword: Flow<String> = context.dataStore.data.map { it[KEY_NTRIP_PASSWORD] ?: "" }
    val ntripMountpoint: Flow<String> = context.dataStore.data.map { it[KEY_NTRIP_MOUNTPOINT] ?: "" }
    val antennaHeight: Flow<String> = context.dataStore.data.map { it[KEY_ANTENNA_HEIGHT] ?: "1.80" }
    val averagingSeconds: Flow<Int> = context.dataStore.data.map { it[KEY_AVERAGING_SECONDS] ?: 5 }
    val minAccuracyM: Flow<String> = context.dataStore.data.map { it[KEY_MIN_ACCURACY_M] ?: "0.05" }
    val requireRtkFix: Flow<Boolean> = context.dataStore.data.map { it[KEY_REQUIRE_RTK_FIX] ?: false }
    val baudRate: Flow<Int> = context.dataStore.data.map { it[KEY_BAUD_RATE] ?: 115200 }
    val ggaIntervalSeconds: Flow<Int> = context.dataStore.data.map { it[KEY_GGA_INTERVAL] ?: 10 }
    val coordFormat: Flow<Int> = context.dataStore.data.map { it[KEY_COORD_FORMAT] ?: 0 }
    val gloveMode: Flow<Boolean> = context.dataStore.data.map { it[KEY_GLOVE_MODE] ?: false }
    val preferReceiverGeoid: Flow<Boolean> = context.dataStore.data.map { it[KEY_PREFER_RECEIVER_GEOID] ?: true }

    // ── Setters ──

    suspend fun setConnectionType(value: Int) {
        context.dataStore.edit { it[KEY_CONNECTION_TYPE] = value }
    }

    suspend fun setBtDevice(address: String, name: String) {
        context.dataStore.edit {
            it[KEY_BT_DEVICE_ADDRESS] = address
            it[KEY_BT_DEVICE_NAME] = name
        }
    }

    suspend fun setNtripConfig(
        presetIndex: Int,
        host: String,
        port: String,
        username: String,
        password: String,
        mountpoint: String,
    ) {
        context.dataStore.edit {
            it[KEY_NTRIP_PRESET_INDEX] = presetIndex
            it[KEY_NTRIP_HOST] = host
            it[KEY_NTRIP_PORT] = port
            it[KEY_NTRIP_USERNAME] = username
            it[KEY_NTRIP_PASSWORD] = password
            it[KEY_NTRIP_MOUNTPOINT] = mountpoint
        }
    }

    // ── One-shot read helpers used by NtripProfileRepository.seedIfEmpty() ──
    suspend fun ntripHostOnce(): String = ntripHost.first()
    suspend fun ntripPortOnce(): String = ntripPort.first()
    suspend fun ntripUsernameOnce(): String = ntripUsername.first()
    suspend fun ntripPasswordOnce(): String = ntripPassword.first()
    suspend fun ntripMountpointOnce(): String = ntripMountpoint.first()

    suspend fun setAntennaHeight(value: String) {
        context.dataStore.edit { it[KEY_ANTENNA_HEIGHT] = value }
    }

    suspend fun setAveragingSeconds(value: Int) {
        context.dataStore.edit { it[KEY_AVERAGING_SECONDS] = value }
    }

    suspend fun setMinAccuracyM(value: String) {
        context.dataStore.edit { it[KEY_MIN_ACCURACY_M] = value }
    }

    suspend fun setRequireRtkFix(value: Boolean) {
        context.dataStore.edit { it[KEY_REQUIRE_RTK_FIX] = value }
    }

    suspend fun setBaudRate(value: Int) {
        context.dataStore.edit { it[KEY_BAUD_RATE] = value }
    }

    suspend fun setGgaIntervalSeconds(value: Int) {
        context.dataStore.edit { it[KEY_GGA_INTERVAL] = value }
    }

    suspend fun setCoordFormat(value: Int) {
        context.dataStore.edit { it[KEY_COORD_FORMAT] = value }
    }

    suspend fun setGloveMode(value: Boolean) {
        context.dataStore.edit { it[KEY_GLOVE_MODE] = value }
    }

    suspend fun setPreferReceiverGeoid(value: Boolean) {
        context.dataStore.edit { it[KEY_PREFER_RECEIVER_GEOID] = value }
    }
}
