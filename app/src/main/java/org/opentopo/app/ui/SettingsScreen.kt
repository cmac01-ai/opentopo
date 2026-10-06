package org.opentopo.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.GpsFixed
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.Terrain
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import org.opentopo.app.ui.theme.CoordinateFont

/**
 * Full-screen Settings overlay (Screen 2 of the More / Settings / About handoff).
 *
 * Grouped category list in the OpenTopo v2.0 design: user-identity hero on top,
 * five sections (Coordinates, Survey, Display, Storage & sync, Danger zone) each
 * rendered as a `surfaceContainerLow` card with `outlineVariant` dividers between
 * rows. Stateless — all toggles + row taps are hoisted to the caller.
 */
@Composable
fun SettingsScreen(
    userName: String = "",
    userOrg: String = "",
    coordSystem: String = "SIRGAS2000 / UTM",
    numberFormatLabel: String = "thousands · space · 3 decimals",
    heightReference: String = "NMEA GGA · receptor",
    pointPrefix: String = "P###",
    averagingLabel: String = "10 epochs · 2 cm σH gate",
    themeLabel: String = "System",
    mapLayersLabel: String = "OpenStreetMap · pontos de campo",
    unitsLabel: String = "metric",
    cloudSyncLabel: String = "Somente neste dispositivo",
    exportDefaultsLabel: String = "CSV · GeoJSON · DXF",
    cacheLabel: String = "Cache local do mapa",
    amoledEnabled: Boolean = false,
    requireRtkFix: Boolean = true,
    onAmoledChange: (Boolean) -> Unit = {},
    onRequireRtkChange: (Boolean) -> Unit = {},
    onCoordSystemClick: () -> Unit = {},
    onNumberFormatClick: () -> Unit = {},
    onHeightReferenceClick: () -> Unit = {},
    onEpochAveragingClick: () -> Unit = {},
    onPointPrefixClick: () -> Unit = {},
    onThemeClick: () -> Unit = {},
    onMapLayersClick: () -> Unit = {},
    onUnitsClick: () -> Unit = {},
    onCloudSyncClick: () -> Unit = {},
    onExportDefaultsClick: () -> Unit = {},
    onClearCacheClick: () -> Unit = {},
    onResetSettingsClick: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(18.dp))

        // ─── App-bar row ───
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                onClick = onBack,
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = "MORE · APP",
                    fontFamily = CoordinateFont,
                    fontSize = 11.sp,
                    letterSpacing = 0.06.em,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Settings",
                    fontSize = 24.sp,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.01).em,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        // ─── Profile hero ───
        Spacer(Modifier.height(14.dp))
        ProfileHero(
            userName = userName,
            userOrg = userOrg,
            onClick = onProfileClick,
        )

        // ─── COORDINATES ───
        SectionOverline("Coordinates")
        SectionCard {
            SettingsRow(
                icon = Icons.Outlined.GpsFixed,
                title = "Coordinate system",
                sub = "SIRGAS2000 · UTM 18–25S / 18–22N",
                onClick = onCoordSystemClick,
                trailing = {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Mono(coordSystem)
                        Chev()
                    }
                },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SettingsRow(
                icon = Icons.Outlined.GridOn,
                title = "Number format",
                sub = numberFormatLabel,
                onClick = onNumberFormatClick,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SettingsRow(
                icon = Icons.Outlined.Terrain,
                title = "Height reference",
                sub = heightReference,
                onClick = onHeightReferenceClick,
                trailing = {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Mono("H")
                        Chev()
                    }
                },
            )
        }

        // ─── SURVEY ───
        SectionOverline("Survey")
        SectionCard {
            SettingsRow(
                icon = Icons.Outlined.Schedule,
                title = "Epoch averaging",
                sub = averagingLabel,
                onClick = onEpochAveragingClick,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SettingsRow(
                icon = Icons.Outlined.FormatListNumbered,
                title = "Point prefix",
                sub = "$pointPrefix · auto-increment on",
                onClick = onPointPrefixClick,
                trailing = { Mono(pointPrefix) },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SettingsRow(
                icon = Icons.Outlined.WbSunny,
                title = "Require RTK Fix to record",
                sub = null,
                onClick = {},
                trailing = {
                    Switch(
                        checked = requireRtkFix,
                        onCheckedChange = onRequireRtkChange,
                    )
                },
            )
        }

        // ─── DISPLAY ───
        SectionOverline("Display")
        SectionCard {
            SettingsRow(
                icon = Icons.Outlined.LightMode,
                title = "Theme",
                sub = themeLabel,
                onClick = onThemeClick,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SettingsRow(
                icon = Icons.Outlined.DarkMode,
                title = "AMOLED true black",
                sub = "Saves battery in the field",
                onClick = {},
                trailing = {
                    Switch(
                        checked = amoledEnabled,
                        onCheckedChange = onAmoledChange,
                    )
                },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SettingsRow(
                icon = Icons.Outlined.Layers,
                title = "Map layers",
                sub = mapLayersLabel,
                onClick = onMapLayersClick,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SettingsRow(
                icon = Icons.Outlined.Straighten,
                title = "Units",
                sub = unitsLabel,
                onClick = onUnitsClick,
                trailing = {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Mono("m · m²")
                        Chev()
                    }
                },
            )
        }

        // ─── STORAGE & SYNC ───
        SectionOverline("Storage & sync")
        SectionCard {
            SettingsRow(
                icon = Icons.Outlined.Cloud,
                title = "Cloud sync",
                sub = cloudSyncLabel,
                onClick = onCloudSyncClick,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SettingsRow(
                icon = Icons.Outlined.Download,
                title = "Export defaults",
                sub = exportDefaultsLabel,
                onClick = onExportDefaultsClick,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SettingsRow(
                icon = Icons.Outlined.Delete,
                title = "Clear cached tiles",
                sub = cacheLabel,
                tone = Color(0xFFFFD9D2),
                onClick = onClearCacheClick,
            )
        }

        // ─── DANGER ZONE ───
        SectionOverline("Danger zone")
        SectionCard {
            SettingsRow(
                icon = Icons.Outlined.ErrorOutline,
                title = "Reset all settings",
                sub = "Receiver, NTRIP profiles kept",
                tone = Color(0xFFFFD9D2),
                onClick = onResetSettingsClick,
            )
        }

        Spacer(Modifier.height(32.dp))
    }
}

// ─────────────────────────── profile hero ───────────────────────────

@Composable
private fun ProfileHero(
    userName: String,
    userOrg: String,
    onClick: () -> Unit,
) {
    val displayName = if (userName.isBlank()) "Usuário de campo" else userName
    val displayOrg = if (userOrg.isBlank()) "Brasil · SIRGAS2000" else userOrg
    val initials = computeInitials(userName)

    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF1C6E5A)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = initials,
                    color = Color.White,
                    fontFamily = CoordinateFont,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.W700,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = displayName,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.W700,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = displayOrg,
                    fontFamily = CoordinateFont,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

private fun computeInitials(userName: String): String {
    if (userName.isBlank()) return "BR"
    val tokens = userName.trim().split(Regex("\\s+"))
    val chars = tokens.take(2).mapNotNull { it.firstOrNull() }
    return chars.joinToString("").uppercase().ifBlank { "BR" }
}

// ─────────────────────────── section helpers ───────────────────────────

@Composable
private fun SectionOverline(text: String) {
    Spacer(Modifier.height(14.dp))
    Text(
        text = text.uppercase(),
        modifier = Modifier.padding(start = 4.dp),
        fontSize = 10.sp,
        fontWeight = FontWeight.W700,
        letterSpacing = 0.1.em,
        color = MaterialTheme.colorScheme.primary,
    )
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun SectionCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(content = content)
    }
}

// ─────────────────────────── row composables ───────────────────────────

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    sub: String? = null,
    onClick: () -> Unit = {},
    tone: Color? = null,
    trailing: @Composable RowScope.() -> Unit = { Chev() },
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(tone ?: MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.005).em,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (sub != null) {
                Text(
                    text = sub,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing()
    }
}

@Composable
private fun Chev() {
    Icon(
        imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun Mono(value: String) {
    Text(
        text = value,
        fontFamily = CoordinateFont,
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
