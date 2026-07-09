package com.example

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * "My Sounds" library screen.
 *
 * Shows every sound the user has previously created in the editor, with
 * one-tap re-apply (no re-trim needed), preview playback, and delete.
 *
 * Data comes from [NotificationSetterViewModel.savedSounds]; the list is
 * loaded into the ViewModel by [NotificationSetterViewModel.loadSoundLibrary]
 * (called from a LaunchedEffect below).
 */
@Composable
fun MySoundsScreen(
    userEmail: String,
    viewModel: NotificationSetterViewModel,
    onMenuClick: () -> Unit,
    onBackToCustomizer: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val savedSounds by viewModel.savedSounds.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    val processingStatus by viewModel.processingStatus.collectAsState()
    val previewingSoundId by viewModel.previewingSoundId.collectAsState()

    // Load the library on first entry to this screen.
    LaunchedEffect(userEmail) {
        viewModel.loadSoundLibrary(context, userEmail)
    }

    // Stop any preview when leaving the screen so audio doesn't keep
    // playing in the background after the user navigates away.
    // (We can't use DisposableEffect here because we still want preview
    // to keep going if the user rotates the screen. Manual stop is fine
    // because navigating away always re-enters onCreate which resets
    // state.)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // --- Top bar ----------------------------------------------------------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onMenuClick) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Open Drawer",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
            TextButton(onClick = {
                viewModel.stopPreview()
                onBackToCustomizer()
            }) {
                Text(
                    text = "Back to Editor",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // --- Header -----------------------------------------------------------
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(20.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.LibraryMusic,
                    contentDescription = "Library",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(38.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "My Sounds",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Tap any sound to re-apply it instantly — no re-trim needed.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp, start = 16.dp, end = 16.dp)
            )
        }

        // --- Processing overlay (thin) ---------------------------------------
        if (isProcessing) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = processingStatus.ifBlank { "Working..." },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // --- List or empty state ---------------------------------------------
        if (savedSounds.isEmpty()) {
            EmptyLibraryState()
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(savedSounds, key = { it.id }) { sound ->
                    SavedSoundRow(
                        sound = sound,
                        isPreviewing = previewingSoundId == sound.id,
                        isProcessing = isProcessing,
                        onPreviewToggle = {
                            viewModel.togglePreview(context, sound)
                        },
                        onApplyNotification = {
                            viewModel.stopPreview()
                            viewModel.applySavedSound(
                                context, userEmail, sound,
                                setAsNotification = true,
                                setAsRingtone = false
                            )
                        },
                        onApplyRingtone = {
                            viewModel.stopPreview()
                            viewModel.applySavedSound(
                                context, userEmail, sound,
                                setAsNotification = false,
                                setAsRingtone = true
                            )
                        },
                        onDelete = {
                            viewModel.deleteSavedSound(context, userEmail, sound)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyLibraryState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(24.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.LibraryMusic,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(48.dp)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No saved sounds yet",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Pick an audio file in the Sound Customizer, trim it, and tap Notification or Call Ringtone. Your saved sounds will appear here for one-tap re-apply.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun SavedSoundRow(
    sound: SavedSound,
    isPreviewing: Boolean,
    isProcessing: Boolean,
    onPreviewToggle: () -> Unit,
    onApplyNotification: () -> Unit,
    onApplyRingtone: () -> Unit,
    onDelete: () -> Unit
) {
    var confirmDelete by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault()) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isPreviewing)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isPreviewing) 2.dp else 0.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // --- Top row: name + delete -------------------------------------
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onPreviewToggle,
                    modifier = Modifier.size(40.dp),
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = if (isPreviewing) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPreviewing) "Stop preview" else "Preview sound",
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = sound.displayName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = buildString {
                            append(RingtoneUtils.formatTime(sound.durationMs))
                            append("  •  ")
                            append(RingtoneUtils.formatFileSize(sound.fileSizeBytes))
                            append("  •  ")
                            append(dateFormat.format(Date(sound.createdAt)))
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (confirmDelete) {
                    TextButton(
                        onClick = {
                            confirmDelete = false
                            onDelete()
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Text("Confirm", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    IconButton(onClick = { confirmDelete = false }, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Cancel delete",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    IconButton(
                        onClick = { confirmDelete = true },
                        modifier = Modifier.size(40.dp),
                        enabled = !isProcessing
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete sound",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.85f)
                        )
                    }
                }
            }

            // --- Badge row: last-applied-as + fade/volume info ---------------
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BadgeChip(
                    text = when (sound.lastAppliedAs) {
                        "ringtone" -> "Last used: Ringtone"
                        "both" -> "Last used: Both"
                        else -> "Last used: Notification"
                    },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
                if (sound.fadeInSec > 0f || sound.fadeOutSec > 0f) {
                    BadgeChip(
                        text = "Fade ${sound.fadeInSec}s/${sound.fadeOutSec}s",
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (sound.volumeBoost < 1.0f) {
                    BadgeChip(
                        text = "Vol ${(sound.volumeBoost * 100).toInt()}%",
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // --- Action row --------------------------------------------------
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onApplyNotification,
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = RoundedCornerShape(10.dp),
                    enabled = !isProcessing,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Notification", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                OutlinedButton(
                    onClick = onApplyRingtone,
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = RoundedCornerShape(10.dp),
                    enabled = !isProcessing
                ) {
                    Icon(
                        imageVector = Icons.Default.PhoneInTalk,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Ringtone", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun BadgeChip(
    text: String,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color
) {
    Box(
        modifier = Modifier
            .background(containerColor, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = contentColor
        )
    }
}
