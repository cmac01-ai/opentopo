package org.opentopo.app.ntrip

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * A saved NTRIP caster profile.
 *
 * Profiles behave like Wi-Fi or VPN profiles: one is active, the rest are
 * saved for quick switching. The authoritative state lives in Room; passwords
 * live in plaintext alongside the rest of the record (same protection class as
 * other survey data — encrypted storage is deferred).
 */
@Entity(tableName = "ntrip_profile")
data class NtripProfile(
    @PrimaryKey val id: String,
    /** User-visible name, e.g. "IBGE · RBMC-IP". */
    val displayName: String,
    /**
     * Two-letter badge code, auto-derived from displayName initials but editable.
     * Used by the provider badge on the Connect row and hero card (mono, 700).
     */
    val code: String,
    /**
     * Packed ARGB tint for the provider badge. Defaults come from the
     * [NtripBadgePalette] seeded by code initials.
     */
    val tintColor: Int,
    /**
     * On-badge foreground color (ARGB) — paired with [tintColor] to guarantee
     * contrast. Chosen from the same palette row as the tint.
     */
    val badgeFgColor: Int,
    val host: String,
    val port: Int = 2101,
    val useTls: Boolean = false,
    val username: String = "",
    val password: String = "",
    val mountpoint: String = "",
    val sendGga: Boolean = true,
    @ColumnInfo(name = "rtcm_preference")
    val rtcmPreference: RtcmVersion = RtcmVersion.ANY,
    val isActive: Boolean = false,
    val lastUsedAt: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
) {
    /** Returns an [NtripConfig] suitable for passing to [NtripClient.connect]. */
    fun toConfig(): NtripConfig = NtripConfig(
        name = displayName,
        host = host,
        port = port,
        useTls = useTls,
        mountpoint = mountpoint,
        username = username,
        password = password,
        sendGga = sendGga,
    )
}

/** Preferred RTCM protocol version advertised in caster handshake. */
enum class RtcmVersion { V32, V33, ANY }

@Dao
interface NtripProfileDao {
    @Query("SELECT * FROM ntrip_profile ORDER BY isActive DESC, lastUsedAt DESC, createdAt DESC")
    fun observeAll(): Flow<List<NtripProfile>>

    @Query("SELECT * FROM ntrip_profile WHERE isActive = 1 LIMIT 1")
    fun observeActive(): Flow<NtripProfile?>

    @Query("SELECT * FROM ntrip_profile WHERE isActive = 1 LIMIT 1")
    suspend fun getActive(): NtripProfile?

    @Query("SELECT * FROM ntrip_profile WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): NtripProfile?

    @Query("SELECT * FROM ntrip_profile WHERE host = :host LIMIT 1")
    suspend fun getByHost(host: String): NtripProfile?

    @Query("DELETE FROM ntrip_profile WHERE host = :host")
    suspend fun deleteByHost(host: String)

    @Query("SELECT COUNT(*) FROM ntrip_profile")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: NtripProfile)

    @Query("DELETE FROM ntrip_profile WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE ntrip_profile SET isActive = 0")
    suspend fun clearActive()

    @Query("UPDATE ntrip_profile SET isActive = 1, lastUsedAt = :now WHERE id = :id")
    suspend fun markActive(id: String, now: Long)

    /** Atomic swap: clear all then mark the requested id active. */
    @Transaction
    suspend fun setActive(id: String, now: Long = System.currentTimeMillis()) {
        clearActive()
        markActive(id, now)
    }
}

/**
 * Default badge palette per displayName initial bucket.
 *
 * The handoff specifies five named swatches; we cycle through them based on a
 * stable hash of the two-letter code so repeated profiles land on the same
 * tint by default. The user can override via the Edit screen.
 */
object NtripBadgePalette {
    private val swatches: List<Pair<Int, Int>> = listOf(
        // (bg, fg) — ARGB packed
        0xFFA5F2D9.toInt() to 0xFF00493D.toInt(),  // Teal
        0xFFD6E3FF.toInt() to 0xFF002F66.toInt(),  // Blue
        0xFFFFDBC9.toInt() to 0xFF3A1100.toInt(),  // Orange
        0xFFDCE5E0.toInt() to 0xFF1F2E2A.toInt(),  // Stone
        0xFFFDF0B3.toInt() to 0xFF4A3F00.toInt(),  // Amber
    )

    fun forCode(code: String): Pair<Int, Int> {
        val key = code.uppercase().take(2)
        val idx = if (key.isEmpty()) 0 else (key.hashCode() and 0x7fffffff) % swatches.size
        return swatches[idx]
    }

    fun deriveCode(displayName: String): String {
        // Take first letter of first 2 whitespace-separated tokens; fallback to first 2 chars.
        val tokens = displayName.trim().split(Regex("[\\s\\·\\-_/]+"))
            .filter { it.isNotBlank() }
        return when {
            tokens.size >= 2 -> (tokens[0].first().toString() + tokens[1].first().toString()).uppercase()
            tokens.size == 1 && tokens[0].length >= 2 -> tokens[0].take(2).uppercase()
            tokens.size == 1 -> tokens[0].take(1).uppercase().padEnd(2, tokens[0].first())
            else -> "NT"
        }
    }
}
