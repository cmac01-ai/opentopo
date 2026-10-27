package org.opentopo.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import org.opentopo.app.ui.theme.CoordinateFont

/**
 * Identity + legal + credits screen for OpenTopo v2.0.
 *
 * Layout: full-bleed deep-pine gradient header, "up to date" badge overlapping
 * the header edge, 3-cell stats strip, link list, credits strip, footer.
 *
 * The header intentionally extends under the status bar — status-bar safe area
 * is applied to the header's inner padding, not the root. The footer respects
 * the navigation-bar inset via [Modifier.navigationBarsPadding].
 */
sealed interface AboutUpdateStatus {
    data object UpToDate : AboutUpdateStatus
    data class UpdateAvailable(val version: String) : AboutUpdateStatus
    data object Checking : AboutUpdateStatus
}

@Composable
fun AboutScreen(
    versionName: String = "v2.0.0",
    buildNumber: String = "2087",
    license: String = "AGPLv3",
    updateStatus: AboutUpdateStatus = AboutUpdateStatus.UpToDate,
    statsGys: String = "SIRGAS",
    statsContributors: String = "18–25",
    statsYears: String = "AGPL",
    onBack: () -> Unit,
    onWhatsNewClick: () -> Unit = {},
    onDocsClick: () -> Unit = {},
    onSourceCodeClick: () -> Unit = {},
    onLicencesClick: () -> Unit = {},
    onPrivacyClick: () -> Unit = {},
    onContactClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val headerFg = Color(0xFFEAFFF6)
    val mintGlow = Color(0xFFA5F2D9)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .verticalScroll(rememberScrollState()),
    ) {
        // 1. Deep-pine header — full-bleed, extends under the status bar.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colorStops = arrayOf(
                            0.0f to Color(0xFF06332A),
                            0.60f to Color(0xFF0A4B3C),
                            1.0f to Color(0xFF156854),
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
                    ),
                ),
        ) {
            // Soft radial mint highlight on the top-right
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 60.dp, y = (-40).dp)
                    .size(220.dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(mintGlow.copy(alpha = 0.25f), Color.Transparent),
                            radius = 330f,
                        ),
                    ),
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 24.dp),
            ) {
                // Back button
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(Color(0x1AFFFFFF), RoundedCornerShape(19.dp))
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Back",
                        tint = headerFg,
                    )
                }

                Spacer(Modifier.height(18.dp))

                // Kicker "ABOUT"
                Text(
                    text = "ABOUT",
                    fontFamily = CoordinateFont,
                    fontSize = 10.sp,
                    letterSpacing = 0.12.em,
                    fontWeight = FontWeight.Bold,
                    color = headerFg.copy(alpha = 0.75f),
                )

                Spacer(Modifier.height(8.dp))

                // Brand row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Mark — text fallback (no bundled SVG logo drawable).
                    Surface(
                        color = Color(0xFF06332A),
                        shape = RoundedCornerShape(20.dp),
                        shadowElevation = 8.dp,
                        border = BorderStroke(1.dp, mintGlow.copy(alpha = 0.3f)),
                        modifier = Modifier.size(72.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "OT",
                                fontFamily = CoordinateFont,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = mintGlow,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }

                    Column {
                        Text(
                            text = "OpenTopo",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.01).em,
                            lineHeight = 24.sp,
                            color = headerFg,
                        )
                        Text(
                            text = "Survey, simply.",
                            fontSize = 13.sp,
                            color = headerFg.copy(alpha = 0.85f),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "$versionName · build $buildNumber · $license",
                            fontFamily = CoordinateFont,
                            fontSize = 11.sp,
                            color = headerFg.copy(alpha = 0.7f),
                        )
                    }
                }
            }
        }

        // 2. "Up to date" badge — overlaps the header's bottom edge.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .offset(y = (-20).dp),
            horizontalArrangement = Arrangement.End,
        ) {
            when (updateStatus) {
                AboutUpdateStatus.UpToDate -> UpdateBadge(
                    dotColor = Color(0xFF1C6E5A),
                    bgColor = Color(0xFFA5F2D9),
                    textColor = Color(0xFF00493D),
                    label = "You're up to date",
                )
                is AboutUpdateStatus.UpdateAvailable -> UpdateBadge(
                    dotColor = Color(0xFFB0A300),
                    bgColor = Color(0xFFFDF0B3),
                    textColor = Color(0xFF4A3F00),
                    label = "Update available (${updateStatus.version})",
                )
                AboutUpdateStatus.Checking -> { /* render nothing */ }
            }
        }

        // Everything below sits inside a 16dp horizontal gutter.
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(14.dp))

            // 3. Stats strip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatCell(value = statsGys, label = "REFERENCIAL", modifier = Modifier.weight(1f))
                StatCell(value = statsContributors, label = "FUSOS SUL", modifier = Modifier.weight(1f))
                StatCell(value = statsYears, label = "OPEN SOURCE", modifier = Modifier.weight(1f))
            }

            Spacer(Modifier.height(14.dp))

            // 4. Link list
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column {
                    LinkRow(
                        title = "What's new",
                        sub = "v2.0 changelog · Material 3 Expressive",
                        onClick = onWhatsNewClick,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    LinkRow(
                        title = "Documentation",
                        sub = "GitHub · documentação do projeto",
                        onClick = onDocsClick,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    LinkRow(
                        title = "Source code",
                        sub = "github.com/cmac01-ai/opentopo",
                        onClick = onSourceCodeClick,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    LinkRow(
                        title = "Open-source licences",
                        sub = "127 libraries · AGPLv3",
                        onClick = onLicencesClick,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    LinkRow(
                        title = "Privacy & data",
                        sub = "Everything stays on device by default",
                        onClick = onPrivacyClick,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    LinkRow(
                        title = "Contact the team",
                        sub = "Issues do fork no GitHub",
                        onClick = onContactClick,
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // 5. Credits strip
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "ADAPTAÇÃO PARA USO NO BRASIL",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.1.em,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                    Text(
                        text = "SIRGAS2000 / UTM · RBMC-IP do IBGE · OpenStreetMap · GNSS RTK por NMEA/RTCM.",
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }

            // 6. Footer — 24dp top margin + nav-bar inset applied to the Text.
            Spacer(Modifier.height(24.dp))
            Text(
                text = "\u00A9 2026 OpenTopo · $license",
                fontFamily = CoordinateFont,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .navigationBarsPadding(),
            )
        }
    }
}

@Composable
private fun UpdateBadge(
    dotColor: Color,
    bgColor: Color,
    textColor: Color,
    label: String,
) {
    Surface(
        color = bgColor,
        shape = RoundedCornerShape(999.dp),
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, top = 6.dp, end = 12.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(dotColor, CircleShape),
            )
            Text(
                text = label,
                color = textColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun StatCell(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(18.dp),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = value,
                fontFamily = CoordinateFont,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.01).em,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = label,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 0.06.em,
            )
        }
    }
}

@Composable
private fun LinkRow(
    title: String,
    sub: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.005).em,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = sub,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
