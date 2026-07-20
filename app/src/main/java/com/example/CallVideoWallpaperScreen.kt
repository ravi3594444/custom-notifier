package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.VideoCall
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
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * "Call Video Wallpaper" screen.
 *
 * Lets the user pick a video file from their device, manage their saved
 * video library, and pick which video plays the next time an incoming call
 * arrives. Also surfaces the runtime permission requests the feature needs
 * (READ_PHONE_STATE, ANSWER_PHONE_CALLS, READ_CALL_LOG) and lets the user
 * grant the SYSTEM_ALERT_WINDOW-equivalent full-screen-intent permission
 * on Android 14+.
 *
 * Data flows:
 *   - List of saved videos: NotificationSetterViewModel.savedVideos
 *   - Active video id: VideoLibraryManager.getActiveVideoId
 *   - Picking a file launches OpenDocument with the `video` MIME type; the
 *     result Uri is passed to VideoLibraryManager.importFromUri which copies the
 *     file into the app's saved_videos/ directory.
 *
 * The actual "video plays on incoming call" logic lives in
 * CallVideoReceiver + CallVideoActivity; this screen is just the picker /
 * library UI.
 */
@Composable
fun CallVideoWallpaperScreen(
    userEmail: String,
    viewModel: NotificationSetterViewModel,
    onMenuClick: () -> Unit,
    onBackToCustomizer: () -> Unit
) {
    val context = LocalContext.current

    val savedVideos by viewModel.savedVideos.collectAsState()
    val activeVideoId by viewModel.activeVideoId.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    val processingStatus by viewModel.processingStatus.collectAsState()
    val previewingVideoId by viewModel.previewingVideoId.collectAsState()

    // --- Permission state ---------------------------------------------
    // We need READ_PHONE_STATE always; ANSWER_PHONE_CALLS on API 26+;
    // READ_CALL_LOG on API 28+ (defensive — see CallVideoController).
    val requiredPermissions = remember {
        buildList {
            add(Manifest.permission.READ_PHONE_STATE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                add(Manifest.permission.ANSWER_PHONE_CALLS)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                add(Manifest.permission.READ_CALL_LOG)
            }
        }.toTypedArray()
    }
    var hasAllPermissions by remember {
        mutableStateOf(
            requiredPermissions.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasAllPermissions = results.values.all { it }
    }

    LaunchedEffect(Unit) {
        if (!hasAllPermissions) {
            permissionLauncher.launch(requiredPermissions)
        }
    }

    // --- File picker ---------------------------------------------------
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.importVideoFromUri(context, userEmail, it)
        }
    }

    // --- Load library on entry ----------------------------------------
    LaunchedEffect(userEmail) {
        viewModel.loadVideoLibrary(context, userEmail)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // --- Top bar ----------------------------------------------------
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
                viewModel.stopVideoPreview()
                onBackToCustomizer()
            }) {
                Text(
                    text = "Back to Editor",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // --- Header -----------------------------------------------------
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
                    imageVector = Icons.Default.VideoCall,
                    contentDescription = "Call Video",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(38.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Call Video Wallpaper",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Pick a video. It will play full-screen with Accept / Dismiss buttons when a call comes in.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp, start = 16.dp, end = 16.dp)
            )
        }

        // --- Permission warning (if missing) ---------------------------
        if (!hasAllPermissions) {
            PermissionWarningCard(
                onRequest = { permissionLauncher.launch(requiredPermissions) }
            )
        }

        // --- Upload button ---------------------------------------------
        Button(
            onClick = {
                videoPickerLauncher.launch(arrayOf("video/*"))
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .height(50.dp),
            shape = RoundedCornerShape(12.dp),
            enabled = !isProcessing,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(
                imageVector = Icons.Default.VideoCall,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Pick a Video", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }

        // --- Processing overlay (thin) ---------------------------------
        if (isProcessing) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
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

        // --- List or empty state --------------------------------------
        if (savedVideos.isEmpty()) {
            EmptyVideoLibraryState()
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(savedVideos, key = { it.id }) { video ->
                    SavedVideoRow(
                        video = video,
                        isActive = activeVideoId == video.id,
                        isPreviewing = previewingVideoId == video.id,
                        isProcessing = isProcessing,
                        onPreviewToggle = {
                            viewModel.toggleVideoPreview(context, video)
                        },
                        onTestCall = {
                            viewModel.stopVideoPreview()
                            val intent = android.content.Intent(context, CallVideoActivity::class.java).apply {
                                putExtra(CallVideoActivity.EXTRA_VIDEO_PATH, video.localFilePath)
                                putExtra(CallVideoActivity.EXTRA_VIDEO_DISPLAY_NAME, video.displayName)
                                putExtra(CallVideoActivity.EXTRA_VIDEO_MIME_TYPE, video.mimeType)
                                putExtra(CallVideoActivity.EXTRA_IS_PREVIEW_MODE, true)
                            }
                            context.startActivity(intent)
                        },
                        onSetActive = {
                            viewModel.stopVideoPreview()
                            viewModel.setActiveVideo(context, userEmail, video)
                        },
                        onClearActive = {
                            viewModel.clearActiveVideo(context, userEmail)
                        },
                        onDelete = {
                            viewModel.deleteSavedVideo(context, userEmail, video)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionWarningCard(onRequest: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Phone permissions required",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "To play your video when a call arrives and let the Accept / Dismiss buttons actually control the call, this app needs READ_PHONE_STATE, ANSWER_PHONE_CALLS, and READ_CALL_LOG permissions.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onRequest,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Grant permissions", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun EmptyVideoLibraryState() {
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
                imageVector = Icons.Default.VideoLibrary,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(48.dp)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No call videos yet",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Tap \"Pick a Video\" above to choose an MP4 (or other video file). It will be saved to your library and you can set it as your call video wallpaper.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun SavedVideoRow(
    video: SavedVideo,
    isActive: Boolean,
    isPreviewing: Boolean,
    isProcessing: Boolean,
    onPreviewToggle: () -> Unit,
    onTestCall: () -> Unit,
    onSetActive: () -> Unit,
    onClearActive: () -> Unit,
    onDelete: () -> Unit
) {
    var confirmDelete by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault()) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isActive -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                isPreviewing -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isActive) 2.dp else 0.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // --- Top row: preview icon + name + delete -------------------
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
                        contentDescription = if (isPreviewing) "Stop preview" else "Preview video",
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = video.displayName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = buildString {
                            append(RingtoneUtils.formatTime(video.durationMs))
                            append("  •  ")
                            append(RingtoneUtils.formatFileSize(video.fileSizeBytes))
                            val dims = listOfNotNull(video.width, video.height)
                            if (dims.size == 2) {
                                append("  •  ")
                                append("${video.width}×${video.height}")
                            }
                            append("  •  ")
                            append(dateFormat.format(Date(video.createdAt)))
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
                            contentDescription = "Delete video",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.85f)
                        )
                    }
                }
            }

            // --- Active badge -------------------------------------------
            Spacer(modifier = Modifier.height(6.dp))
            if (isActive) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Active — will play on the next call",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // --- Action row ---------------------------------------------
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onTestCall,
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = RoundedCornerShape(10.dp),
                    enabled = !isProcessing
                ) {
                    Text("Test Call", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                
                if (isActive) {
                    OutlinedButton(
                        onClick = onClearActive,
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(10.dp),
                        enabled = !isProcessing,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Clear Active", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    Button(
                        onClick = onSetActive,
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(10.dp),
                        enabled = !isProcessing,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhoneInTalk,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Set as Call Video", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
