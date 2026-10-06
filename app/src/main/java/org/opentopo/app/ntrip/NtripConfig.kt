package org.opentopo.app.ntrip

/**
 * NTRIP caster connection configuration.
 */
data class NtripConfig(
    val name: String,
    val host: String,
    val port: Int = 2101,
    val useTls: Boolean = false,
    val mountpoint: String = "",
    val username: String = "",
    val password: String = "",
    val sendGga: Boolean = true,
    val ggaIntervalSeconds: Int = 10,
) {
    val url: String get() = "${if (useTls) "https" else "http"}://$host:$port/$mountpoint"

    companion object {
        val PRESETS = listOf(
            NtripConfig(
                name = "IBGE · RBMC-IP",
                host = "170.84.40.52",
                port = 2101,
            ),
        )
    }
}

/**
 * An NTRIP sourcetable entry (mountpoint).
 */
data class NtripMountpoint(
    val name: String,
    val identifier: String = "",
    val format: String = "",
    val formatDetails: String = "",
    val carrier: Int = 0,
    val navSystem: String = "",
    val network: String = "",
    val country: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val nmea: Boolean = false,
    val solution: Boolean = false,
    val generator: String = "",
    val compression: String = "",
    val authentication: String = "",
    val fee: Boolean = false,
    val bitrate: Int = 0,
)
