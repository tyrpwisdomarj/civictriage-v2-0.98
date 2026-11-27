package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.KeralaEmergencyHelplines
import com.example.data.SavedReceiptItem
import com.example.ui.theme.CivicAmber
import com.example.ui.theme.CivicCyan
import com.example.ui.theme.CivicEmerald
import com.example.ui.theme.LocalCivicGlass

@Composable
fun LiquidSplashOverlay(
    visible: Boolean,
    loadProgress: Int,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = (loadProgress.coerceIn(8, 100)) / 100f,
        animationSpec = spring(stiffness = 380f, dampingRatio = 0.85f),
        label = "splash_progress"
    )
    val glass = LocalCivicGlass.current

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(animationSpec = spring(stiffness = 380f, dampingRatio = 0.9f)),
        modifier = modifier
    ) {
        LiquidAmbientBackground {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                LiquidGlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 460.dp)
                        .testTag("splash_card"),
                    cornerRadius = 32.dp,
                    contentPadding = PaddingValues(24.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(136.dp)
                                .clip(RoundedCornerShape(22.dp))
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.img_civic_hero_emblem),
                                contentDescription = stringResource(R.string.cd_hero_art),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                Color.Transparent,
                                                Color(0xB80A0D14)
                                            )
                                        )
                                    )
                            )
                            Image(
                                painter = painterResource(id = R.drawable.ic_civic_triage_mark),
                                contentDescription = stringResource(R.string.cd_app_emblem),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .size(72.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .border(
                                        1.5.dp,
                                        Color.White.copy(alpha = 0.65f),
                                        RoundedCornerShape(20.dp)
                                    )
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = stringResource(R.string.app_tagline),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(22.dp))

                        // Periwinkle-to-Cyan frosted progress bar
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(CircleShape)
                                .background(glass.subtleSurface)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(animatedProgress)
                                    .height(6.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.horizontalGradient(
                                            colors = listOf(glass.accentPill, CivicCyan)
                                        )
                                    )
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.splash_status_connecting),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "$loadProgress%",
                                style = MaterialTheme.typography.labelSmall,
                                color = glass.accentPill
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun OfflineFallbackScreen(
    savedReceiptCount: Int,
    onRetryConnection: () -> Unit,
    onDialEmergency: (String) -> Unit,
    onOpenReceipts: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val glass = LocalCivicGlass.current
    LiquidAmbientBackground(modifier = modifier.testTag("offline_fallback_screen")) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                LiquidGlassCard(cornerRadius = 28.dp) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(148.dp)
                                .clip(RoundedCornerShape(20.dp))
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.img_civic_hero_emblem),
                                contentDescription = stringResource(R.string.cd_hero_art),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color(0x880A0D14))
                            )
                            Row(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(14.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0xCC121724))
                                    .border(1.dp, CivicAmber.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.WifiOff,
                                    contentDescription = null,
                                    tint = CivicAmber,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "OFFLINE · AUTO-RELOAD ARMED",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = stringResource(R.string.offline_title),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.offline_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        LiquidPrimaryButton(
                            text = stringResource(R.string.action_retry_connection),
                            onClick = onRetryConnection,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("offline_retry_button"),
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = null,
                                    tint = glass.primaryActionText,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable(onClick = onOpenReceipts)
                                    .testTag("offline_receipts_button"),
                                shape = RoundedCornerShape(16.dp),
                                color = glass.subtleSurface,
                                border = BorderStroke(1.dp, glass.glassBorder)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Description,
                                        contentDescription = null,
                                        tint = glass.accentPill,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Receipts ($savedReceiptCount)",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable(onClick = onOpenSettings)
                                    .testTag("offline_settings_button"),
                                shape = RoundedCornerShape(16.dp),
                                color = glass.subtleSurface,
                                border = BorderStroke(1.dp, glass.glassBorder)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Tune,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Portal URL",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }

                // 24x7 Kerala Emergency Helplines always work 100% offline via native dialer
                LiquidGlassCard(cornerRadius = 28.dp) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.action_emergency_helplines),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Tap any helpline to open the phone dialer immediately — works without internet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        KeralaEmergencyHelplines.all.forEach { helpline ->
                            EmergencyHelplineRowCard(
                                helpline = helpline,
                                onDial = onDialEmergency
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun OfflineConnectivityBanner(
    visible: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .testTag("offline_connectivity_banner"),
            shape = RoundedCornerShape(18.dp),
            color = Color(0xEB1E2438),
            border = BorderStroke(1.dp, CivicAmber.copy(alpha = 0.6f)),
            shadowElevation = 6.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.CloudOff,
                    contentDescription = null,
                    tint = CivicAmber,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.offline_banner_text),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                TextButton(
                    onClick = onRetry,
                    modifier = Modifier.testTag("offline_banner_retry")
                ) {
                    Text(
                        text = "Retry",
                        style = MaterialTheme.typography.labelLarge,
                        color = CivicCyan
                    )
                }
            }
        }
    }
}

@Composable
fun DownloadSuccessBanner(
    receipt: SavedReceiptItem?,
    onOpen: (SavedReceiptItem) -> Unit,
    onShare: (SavedReceiptItem) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val glass = LocalCivicGlass.current
    AnimatedVisibility(
        visible = receipt != null,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = modifier
    ) {
        if (receipt != null) {
            LiquidGlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .testTag("download_success_banner"),
                cornerRadius = 24.dp,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(CivicEmerald.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Description,
                            contentDescription = null,
                            tint = CivicEmerald,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.receipt_saved_prefix),
                            style = MaterialTheme.typography.labelSmall,
                            color = CivicEmerald
                        )
                        Text(
                            text = receipt.fileName,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    TextButton(
                        onClick = { onOpen(receipt) },
                        modifier = Modifier.testTag("download_banner_open")
                    ) {
                        Text(
                            text = stringResource(R.string.action_open),
                            color = glass.accentPill
                        )
                    }

                    IconButton(
                        onClick = { onShare(receipt) },
                        modifier = Modifier.testTag("download_banner_share")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Share,
                            contentDescription = stringResource(R.string.action_share),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("download_banner_close")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.action_close),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
