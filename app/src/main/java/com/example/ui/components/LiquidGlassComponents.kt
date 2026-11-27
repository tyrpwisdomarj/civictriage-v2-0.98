package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.EmergencyHelpline
import com.example.ui.theme.LocalCivicGlass

@Composable
fun LiquidAmbientBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val glass = LocalCivicGlass.current
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(glass.canvasStart, glass.canvasMid, glass.canvasEnd),
                    start = Offset.Zero,
                    end = Offset(1200f, 1800f)
                )
            )
            .drawBehind {
                // Soft ambient floating light orbs matching Liquid Glass system
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(glass.orbPrimary, Color.Transparent),
                        center = Offset(size.width * 0.16f, size.height * 0.14f),
                        radius = size.minDimension * 0.58f
                    ),
                    radius = size.minDimension * 0.58f,
                    center = Offset(size.width * 0.16f, size.height * 0.14f)
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(glass.orbSecondary, Color.Transparent),
                        center = Offset(size.width * 0.88f, size.height * 0.24f),
                        radius = size.minDimension * 0.50f
                    ),
                    radius = size.minDimension * 0.50f,
                    center = Offset(size.width * 0.88f, size.height * 0.24f)
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(glass.orbTertiary, Color.Transparent),
                        center = Offset(size.width * 0.55f, size.height * 0.85f),
                        radius = size.minDimension * 0.62f
                    ),
                    radius = size.minDimension * 0.62f,
                    center = Offset(size.width * 0.55f, size.height * 0.85f)
                )
            },
        content = content
    )
}

@Composable
fun LiquidGlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 28.dp,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    content: @Composable BoxScope.() -> Unit
) {
    val glass = LocalCivicGlass.current
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(glass.glassTop, glass.glassBottom)
                )
            )
            .border(
                border = BorderStroke(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            glass.glassBorder,
                            glass.glassBorder.copy(alpha = 0.25f)
                        )
                    )
                ),
                shape = shape
            )
            .padding(contentPadding),
        content = content
    )
}

@Composable
fun LiquidPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: @Composable (() -> Unit)? = null
) {
    val glass = LocalCivicGlass.current
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(shape)
            .background(
                Brush.horizontalGradient(
                    colors = listOf(glass.primaryActionBg, glass.primaryActionBgEnd)
                )
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (leadingIcon != null) {
                leadingIcon()
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = glass.primaryActionText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun LiquidSegmentedThemeToggle(
    isDarkTheme: Boolean,
    onSelectTheme: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val glass = LocalCivicGlass.current
    val pillShape = RoundedCornerShape(22.dp)

    val lightBg by animateColorAsState(
        targetValue = if (!isDarkTheme) glass.accentPill else Color.Transparent,
        animationSpec = spring(stiffness = 380f, dampingRatio = Spring.DampingRatioNoBouncy),
        label = "light_pill_bg"
    )
    val darkBg by animateColorAsState(
        targetValue = if (isDarkTheme) glass.accentPill else Color.Transparent,
        animationSpec = spring(stiffness = 380f, dampingRatio = Spring.DampingRatioNoBouncy),
        label = "dark_pill_bg"
    )

    Row(
        modifier = modifier
            .clip(pillShape)
            .background(glass.subtleSurface)
            .border(1.dp, glass.glassBorder, pillShape)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .minimumInteractiveComponentSize()
                .clip(RoundedCornerShape(18.dp))
                .background(lightBg)
                .clickable { onSelectTheme(false) }
                .testTag("theme_toggle_light")
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.LightMode,
                contentDescription = stringResource(R.string.theme_light_label),
                tint = if (!isDarkTheme) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.theme_light_label),
                style = MaterialTheme.typography.labelLarge,
                color = if (!isDarkTheme) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(
            modifier = Modifier
                .weight(1f)
                .minimumInteractiveComponentSize()
                .clip(RoundedCornerShape(18.dp))
                .background(darkBg)
                .clickable { onSelectTheme(true) }
                .testTag("theme_toggle_dark")
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.DarkMode,
                contentDescription = stringResource(R.string.theme_dark_label),
                tint = if (isDarkTheme) Color(0xFF0A0D14) else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.theme_dark_label),
                style = MaterialTheme.typography.labelLarge,
                color = if (isDarkTheme) Color(0xFF0A0D14) else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun EmergencyHelplineRowCard(
    helpline: EmergencyHelpline,
    onDial: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val glass = LocalCivicGlass.current
    val shape = RoundedCornerShape(20.dp)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .clip(shape)
            .clickable { onDial(helpline.number) }
            .testTag("emergency_dial_${helpline.number}"),
        shape = shape,
        color = glass.subtleSurface,
        border = BorderStroke(1.dp, glass.glassBorder.copy(alpha = 0.65f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(helpline.badgeColor.copy(alpha = 0.16f))
                    .border(1.dp, helpline.badgeColor.copy(alpha = 0.45f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Call,
                    contentDescription = "${stringResource(R.string.cd_emergency_call)} ${helpline.number}",
                    tint = helpline.badgeColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = helpline.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = helpline.department,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(helpline.badgeColor)
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            ) {
                Text(
                    text = helpline.number,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
            }
        }
    }
}
