package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.KeralaEmergencyHelplines
import com.example.data.SavedReceiptItem
import com.example.ui.UpiFallbackState
import com.example.ui.theme.CivicCrimson
import com.example.ui.theme.CivicEmerald
import com.example.ui.theme.LocalCivicGlass
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiquidModalSheet(
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val glass = LocalCivicGlass.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
        containerColor = if (glass.isDark) Color(0xF5121724) else Color(0xF5F5F4F0),
        dragHandle = {
            // Tactile iOS bottom-sheet grabber
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 6.dp)
                    .size(width = 42.dp, height = 5.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
            )
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            content()
        }
    }
}

@Composable
fun ShellHubBottomSheet(
    liveUrl: String,
    isDarkTheme: Boolean,
    isOnline: Boolean,
    savedReceiptsCount: Int,
    onToggleTheme: (Boolean) -> Unit,
    onSyncLatest: () -> Unit,
    onOpenEmergency: () -> Unit,
    onOpenReceipts: () -> Unit,
    onOpenPortalSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    val glass = LocalCivicGlass.current
    LiquidModalSheet(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("shell_hub_sheet"),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (isOnline) "Live-Synced Hybrid Shell · Auto-Update Active" else "Offline Cache Active",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isOnline) CivicEmerald else MaterialTheme.colorScheme.error
                    )
                }
            }

            LiquidSegmentedThemeToggle(
                isDarkTheme = isDarkTheme,
                onSelectTheme = onToggleTheme,
                modifier = Modifier.fillMaxWidth()
            )

            SheetActionTile(
                icon = Icons.Rounded.Refresh,
                iconTint = glass.accentPill,
                title = stringResource(R.string.action_sync_now),
                subtitle = "Fetch the latest web deploy & PWA assets immediately",
                testTag = "hub_action_sync",
                onClick = onSyncLatest
            )

            SheetActionTile(
                icon = Icons.Rounded.Call,
                iconTint = CivicCrimson,
                title = stringResource(R.string.action_emergency_helplines),
                subtitle = "112 Police · 1930 Cyber · 1912 KSEB · 1916 KWA · 108 Ambulance",
                testTag = "hub_action_emergency",
                onClick = onOpenEmergency
            )

            SheetActionTile(
                icon = Icons.Rounded.FolderOpen,
                iconTint = CivicEmerald,
                title = "${stringResource(R.string.action_saved_receipts)} ($savedReceiptsCount)",
                subtitle = "Offline OP Slips, Admission Receipts, Job Receipts & E-Tickets",
                testTag = "hub_action_receipts",
                onClick = onOpenReceipts
            )

            SheetActionTile(
                icon = Icons.Rounded.Link,
                iconTint = MaterialTheme.colorScheme.onSurface,
                title = stringResource(R.string.action_portal_settings),
                subtitle = liveUrl.removePrefix("https://"),
                testTag = "hub_action_url_settings",
                onClick = onOpenPortalSettings
            )

            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}

@Composable
fun MediaPickerBottomSheet(
    allowDocuments: Boolean,
    onPickCamera: () -> Unit,
    onPickPhotoLibrary: () -> Unit,
    onPickDocument: () -> Unit,
    onDismiss: () -> Unit
) {
    val glass = LocalCivicGlass.current
    LiquidModalSheet(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("media_picker_sheet"),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.media_picker_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.media_picker_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            SheetActionTile(
                icon = Icons.Rounded.CameraAlt,
                iconTint = glass.accentPill,
                title = stringResource(R.string.media_picker_camera),
                subtitle = "Snap live civic hazard or before/after repair photo",
                testTag = "media_pick_camera",
                onClick = onPickCamera
            )

            SheetActionTile(
                icon = Icons.Rounded.PhotoLibrary,
                iconTint = CivicEmerald,
                title = stringResource(R.string.media_picker_gallery),
                subtitle = "Private zero-permission Android Photo Picker",
                testTag = "media_pick_gallery",
                onClick = onPickPhotoLibrary
            )

            if (allowDocuments) {
                SheetActionTile(
                    icon = Icons.Rounded.UploadFile,
                    iconTint = MaterialTheme.colorScheme.secondary,
                    title = stringResource(R.string.media_picker_document),
                    subtitle = "Attach PDF or evidence file from device storage",
                    testTag = "media_pick_document",
                    onClick = onPickDocument
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}

@Composable
fun EmergencyHelplinesBottomSheet(
    onDial: (String) -> Unit,
    onDismiss: () -> Unit
) {
    LiquidModalSheet(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("emergency_helplines_sheet"),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(R.string.action_emergency_helplines),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Tap any desk below to launch the native phone dialer immediately.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(KeralaEmergencyHelplines.all, key = { it.number }) { helpline ->
                    EmergencyHelplineRowCard(
                        helpline = helpline,
                        onDial = onDial
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}

@Composable
fun SavedReceiptsBottomSheet(
    receipts: List<SavedReceiptItem>,
    onOpenReceipt: (SavedReceiptItem) -> Unit,
    onShareReceipt: (SavedReceiptItem) -> Unit,
    onDeleteReceipt: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val glass = LocalCivicGlass.current
    val dateFmt = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()) }

    LiquidModalSheet(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("saved_receipts_sheet"),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.action_saved_receipts),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (receipts.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Description,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = stringResource(R.string.receipts_empty_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.receipts_empty_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(receipts, key = { it.fileName }) { item ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .clickable { onOpenReceipt(item) }
                                .testTag("receipt_item_${item.fileName}"),
                            shape = RoundedCornerShape(18.dp),
                            color = glass.subtleSurface,
                            border = BorderStroke(1.dp, glass.glassBorder.copy(alpha = 0.6f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Description,
                                    contentDescription = null,
                                    tint = glass.accentPill,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.fileName,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${(item.sizeBytes / 1024).coerceAtLeast(1)} KB · ${dateFmt.format(Date(item.lastModifiedMillis))}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(
                                    onClick = { onShareReceipt(item) },
                                    modifier = Modifier.testTag("receipt_share_${item.fileName}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Share,
                                        contentDescription = stringResource(R.string.action_share),
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                IconButton(
                                    onClick = { onDeleteReceipt(item.fileName) },
                                    modifier = Modifier.testTag("receipt_delete_${item.fileName}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.DeleteOutline,
                                        contentDescription = "Delete receipt",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}

@Composable
fun PortalSettingsBottomSheet(
    currentUrl: String,
    hasValidationError: Boolean,
    onSaveUrl: (String) -> Unit,
    onResetDefault: () -> Unit,
    onDismiss: () -> Unit
) {
    var draftUrl by remember(currentUrl) { mutableStateOf(currentUrl) }

    LiquidModalSheet(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("portal_settings_sheet"),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.action_portal_settings),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            OutlinedTextField(
                value = draftUrl,
                onValueChange = { draftUrl = it },
                label = { Text(stringResource(R.string.live_url_label)) },
                supportingText = {
                    Text(
                        text = if (hasValidationError) {
                            stringResource(R.string.live_url_invalid_error)
                        } else {
                            stringResource(R.string.live_url_helper)
                        }
                    )
                },
                isError = hasValidationError,
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("portal_url_input")
            )

            LiquidPrimaryButton(
                text = stringResource(R.string.live_url_save),
                onClick = { onSaveUrl(draftUrl) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("portal_url_save_button")
            )

            OutlinedButton(
                onClick = onResetDefault,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .testTag("portal_url_reset_button")
            ) {
                Text(stringResource(R.string.live_url_reset))
            }

            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}

@Composable
fun UpiFallbackDialog(
    state: UpiFallbackState,
    onCopyVpa: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.upi_fallback_title),
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.upi_fallback_body),
                    style = MaterialTheme.typography.bodyMedium
                )
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = state.merchantVpa,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Icon(
                            imageVector = Icons.Rounded.ContentCopy,
                            contentDescription = stringResource(R.string.action_copy_vpa),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCopyVpa(state.merchantVpa) },
                modifier = Modifier.testTag("upi_copy_vpa_button")
            ) {
                Text(stringResource(R.string.action_copy_vpa))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("upi_fallback_close_button")
            ) {
                Text(stringResource(R.string.action_close))
            }
        }
    )
}

@Composable
private fun SheetActionTile(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    testTag: String,
    onClick: () -> Unit
) {
    val glass = LocalCivicGlass.current
    val shape = RoundedCornerShape(20.dp)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .clip(shape)
            .clickable(onClick = onClick)
            .testTag(testTag),
        shape = shape,
        color = glass.subtleSurface,
        border = BorderStroke(1.dp, glass.glassBorder.copy(alpha = 0.6f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(iconTint.copy(alpha = 0.14f))
                    .border(1.dp, iconTint.copy(alpha = 0.35f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = iconTint,
                    modifier = Modifier.size(21.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
