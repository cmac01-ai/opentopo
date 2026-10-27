package org.opentopo.app.ui.components.ntrip

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import org.opentopo.app.ntrip.NtripConnectionState
import org.opentopo.app.ntrip.NtripProfile
import org.opentopo.app.ui.theme.CoordinateFont

/**
 * Compact single-row surface that represents the active NTRIP profile plus
 * its live connection state. Replaces the legacy NtripCard on the Connect
 * screen: one tap target, visuals change with state.
 *
 * Render contract:
 *  - `profile == null` OR `state is Empty` → dashed-outline empty row.
 *  - Otherwise → filled surfaceContainerLow row with provider badge, status
 *    strip (overline + pulsing/static dot + mono detail), title, and mono
 *    subtitle. Trailing slot is a chevron, spinner, or "Reconnect" pill
 *    depending on the state.
 */
@Composable
fun NtripActiveProfileRow(
    profile: NtripProfile?,
    state: NtripConnectionState,
    correctionAgeSeconds: Double? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onReconnectClick: (() -> Unit)? = null,
) {
    val isEmpty = profile == null || state is NtripConnectionState.Empty

    val shape = RoundedCornerShape(20.dp)
    val outlineColor = MaterialTheme.colorScheme.outline
    val surfaceLow = MaterialTheme.colorScheme.surfaceContainerLow

    // Background: dashed outline for Empty, filled surfaceContainerLow otherwise.
    val bgModifier = if (isEmpty) {
        Modifier.drawBehind {
            val stroke = 1.dp.toPx()
            val cr = 20.dp.toPx()
            drawRoundRect(
                color = outlineColor,
                topLeft = Offset(stroke / 2f, stroke / 2f),
                size = Size(size.width - stroke, size.height - stroke),
                cornerRadius = CornerRadius(cr, cr),
                style = Stroke(
                    width = stroke,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f)),
                ),
            )
        }
    } else {
        Modifier
            .clip(shape)
            .background(surfaceLow, shape)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .then(bgModifier)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ── Left badge ──
        ProviderBadge(profile = profile, state = state, isEmpty = isEmpty)

        // ── Middle column ──
        Column(
            modifier = Modifier
                .weight(1f)
                .defaultMinSize(minWidth = 1.dp),
        ) {
            if (!isEmpty) {
                StatusStrip(state = state, correctionAgeSeconds = correctionAgeSeconds)
                Spacer(Modifier.height(2.dp))
            }
            Text(
                text = if (isEmpty) "Set up NTRIP corrections" else profile.displayName,
                style = TextStyle(
                    fontSize = 15.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.W600,
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            SubtitleLine(profile = profile, state = state, isEmpty = isEmpty)
        }

        // ── Right trailing ──
        TrailingSlot(
            state = state,
            isEmpty = isEmpty,
            onReconnectClick = onReconnectClick ?: onClick,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Provider badge
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ProviderBadge(
    profile: NtripProfile?,
    state: NtripConnectionState,
    isEmpty: Boolean,
) {
    val (bg, fg) = resolveBadgeColors(profile, state, isEmpty)
    val code = when {
        isEmpty || profile == null -> "NT"
        else -> profile.code.ifBlank { "NT" }
    }
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = code.take(2).uppercase(),
            style = TextStyle(
                fontFamily = CoordinateFont,
                fontSize = 14.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.W700,
            ),
            color = fg,
        )
    }
}

@Composable
private fun resolveBadgeColors(
    profile: NtripProfile?,
    state: NtripConnectionState,
    isEmpty: Boolean,
): Pair<Color, Color> {
    if (isEmpty || profile == null) {
        return MaterialTheme.colorScheme.surfaceContainerHigh to
            MaterialTheme.colorScheme.onSurfaceVariant
    }
    val profileBg = Color(profile.tintColor)
    val profileFg = Color(profile.badgeFgColor)
    return when (state) {
        is NtripConnectionState.Live -> profileBg to profileFg
        NtripConnectionState.Connecting -> Color(0xFFFDF0B3) to Color(0xFF4A3F00)
        is NtripConnectionState.Stale -> Color(0xFFFFD9D2) to Color(0xFF6C1C10)
        NtripConnectionState.Disconnected -> profileBg.copy(alpha = 0.4f) to profileFg
        NtripConnectionState.Empty -> MaterialTheme.colorScheme.surfaceContainerHigh to
            MaterialTheme.colorScheme.onSurfaceVariant
        is NtripConnectionState.Error -> Color(0xFFFFD9D2) to Color(0xFF6C1C10)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Status strip (overline + dot + detail)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StatusStrip(
    state: NtripConnectionState,
    correctionAgeSeconds: Double?,
) {
    val overline = overlineFor(state) ?: return
    val overlineStyle = TextStyle(
        fontSize = 10.sp,
        lineHeight = 12.sp,
        fontWeight = FontWeight.W700,
        letterSpacing = 0.1.em,
    )
    Row(
        modifier = Modifier.height(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = overline,
            style = overlineStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        StatusDot(state = state)
        StatusDetail(state = state, correctionAgeSeconds = correctionAgeSeconds)
    }
}

private fun overlineFor(state: NtripConnectionState): String? = when (state) {
    is NtripConnectionState.Live -> "NTRIP \u00B7 ACTIVE"
    NtripConnectionState.Connecting -> "CONNECTING\u2026"
    is NtripConnectionState.Stale -> "STREAM STALE"
    NtripConnectionState.Disconnected -> "NTRIP \u00B7 IDLE"
    NtripConnectionState.Empty -> null
    is NtripConnectionState.Error -> "NTRIP \u00B7 ERROR"
}

@Composable
private fun StatusDot(state: NtripConnectionState) {
    when (state) {
        is NtripConnectionState.Live -> PulsingLiveDot(color = Color(0xFF1C6E5A))
        is NtripConnectionState.Stale -> SolidDot(color = Color(0xFF9B3A2E))
        is NtripConnectionState.Error -> SolidDot(color = Color(0xFF9B3A2E))
        else -> { /* no dot */ }
    }
}

@Composable
private fun SolidDot(color: Color) {
    Box(
        modifier = Modifier
            .size(6.dp)
            .clip(RoundedCornerShape(50))
            .background(color),
    )
}

@Composable
private fun PulsingLiveDot(color: Color) {
    val transition = rememberInfiniteTransition(label = "ntripLivePulse")
    val scale by transition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ntripLivePulseScale",
    )
    val alpha by transition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ntripLivePulseAlpha",
    )
    Box(
        modifier = Modifier.size(6.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Pulsing ring behind
        Box(
            modifier = Modifier
                .size(6.dp)
                .drawBehind {
                    val r = (size.minDimension / 2f) * scale
                    drawCircle(
                        color = color.copy(alpha = alpha),
                        radius = r,
                        center = Offset(size.width / 2f, size.height / 2f),
                    )
                },
        )
        // Solid core
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(RoundedCornerShape(50))
                .background(color),
        )
    }
}

@Composable
private fun StatusDetail(
    state: NtripConnectionState,
    correctionAgeSeconds: Double?,
) {
    val detailStyle = TextStyle(
        fontFamily = CoordinateFont,
        fontSize = 10.sp,
        lineHeight = 12.sp,
        fontWeight = FontWeight.W700,
    )
    when (state) {
        is NtripConnectionState.Live -> Text(
            text = "Age %.1f s".format(correctionAgeSeconds ?: state.ageSeconds),
            style = detailStyle,
            color = Color(0xFF00493D),
        )
        is NtripConnectionState.Stale -> Text(
            text = "Age %.1f s".format(correctionAgeSeconds ?: state.ageSeconds),
            style = detailStyle,
            color = Color(0xFF9B3A2E),
        )
        NtripConnectionState.Connecting -> Text(
            text = "sourcetable",
            style = detailStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        else -> { /* no detail */ }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Subtitle line
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SubtitleLine(
    profile: NtripProfile?,
    state: NtripConnectionState,
    isEmpty: Boolean,
) {
    val monoSmall = TextStyle(
        fontFamily = CoordinateFont,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        fontWeight = FontWeight.W400,
    )
    when {
        isEmpty -> Text(
            text = "Optional \u2014 needed for RTK fix",
            style = monoSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        state is NtripConnectionState.Stale -> Text(
            text = "age %.0f s".format(state.ageSeconds),
            style = monoSmall,
            color = MaterialTheme.colorScheme.error,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        state is NtripConnectionState.Connecting -> Text(
            text = "sourcetable",
            style = monoSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        state is NtripConnectionState.Live -> Text(
            text = "${profile?.mountpoint.orEmpty()} \u00B7 ${state.bitrate} B/s",
            style = monoSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        state is NtripConnectionState.Error -> Text(
            text = state.reason,
            style = monoSmall,
            color = MaterialTheme.colorScheme.error,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        else -> Text(
            text = "${profile?.mountpoint.orEmpty()} \u00B7 idle",
            style = monoSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Trailing slot (chevron / spinner / reconnect pill)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TrailingSlot(
    state: NtripConnectionState,
    isEmpty: Boolean,
    onReconnectClick: () -> Unit,
) {
    Box(
        modifier = Modifier.size(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        when {
            isEmpty -> Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            state is NtripConnectionState.Connecting -> CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = Color(0xFFB0A300),
            )
            state is NtripConnectionState.Stale -> ReconnectPill(onClick = onReconnectClick)
            state is NtripConnectionState.Error -> ReconnectPill(onClick = onReconnectClick)
            else -> Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun ReconnectPill(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF9B3A2E),
            contentColor = Color.White,
        ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = 12.dp,
            vertical = 6.dp,
        ),
        modifier = Modifier
            .defaultMinSize(minWidth = 1.dp, minHeight = 1.dp)
            .width(96.dp),
    ) {
        Text(
            text = "Reconnect",
            style = TextStyle(
                fontSize = 11.sp,
                lineHeight = 13.sp,
                fontWeight = FontWeight.W700,
            ),
        )
    }
}
