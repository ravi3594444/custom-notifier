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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import java.io.File
import kotlin.math.roundToInt
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
                        userEmail = userEmail,
                        viewModel = viewModel,
                        onPreviewToggle = {
                            viewModel.toggleVideoPreview(context, video)
                        },
                        onTestCall = {
                            viewModel.stopVideoPreview()
                            val intent = android.content.Intent(context, CallVideoActivity::class.java).apply {
                                putExtra(CallVideoActivity.EXTRA_VIDEO_PATH, video.localFilePath)
                                putExtra(CallVideoActivity.EXTRA_VIDEO_DISPLAY_NAME, "Ravi")
                                putExtra(CallVideoActivity.EXTRA_VIDEO_MIME_TYPE, video.mimeType)
                                putExtra(CallVideoActivity.EXTRA_IS_PREVIEW_MODE, true)
                                if (video.trimStartMs != null) putExtra(CallVideoActivity.EXTRA_TRIM_START_MS, video.trimStartMs)
                                if (video.trimEndMs != null) putExtra(CallVideoActivity.EXTRA_TRIM_END_MS, video.trimEndMs)
                                if (video.customAudioPath != null) putExtra(CallVideoActivity.EXTRA_CUSTOM_AUDIO_PATH, video.customAudioPath)
                                if (video.videoScale != null) putExtra(CallVideoActivity.EXTRA_VIDEO_SCALE, video.videoScale)
                                if (video.namePositionY != null) putExtra(CallVideoActivity.EXTRA_NAME_POSITION_Y, video.namePositionY)
                                if (video.answerStyle != null) putExtra(CallVideoActivity.EXTRA_ANSWER_STYLE, video.answerStyle)
                                if (video.nameFontSize != null) putExtra(CallVideoActivity.EXTRA_NAME_FONT_SIZE, video.nameFontSize)
                                if (video.nameFontFamily != null) putExtra(CallVideoActivity.EXTRA_NAME_FONT_FAMILY, video.nameFontFamily)
                                if (video.nameTextColor != null) putExtra(CallVideoActivity.EXTRA_NAME_TEXT_COLOR, video.nameTextColor)
                                if (video.nameBgColor != null) putExtra(CallVideoActivity.EXTRA_NAME_BG_COLOR, video.nameBgColor)
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
    userEmail: String,
    viewModel: NotificationSetterViewModel,
    onPreviewToggle: () -> Unit,
    onTestCall: () -> Unit,
    onSetActive: () -> Unit,
    onClearActive: () -> Unit,
    onDelete: () -> Unit
) {
    var confirmDelete by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault()) }

    if (showEditDialog) {
        VideoEditDialog(
            video = video,
            userEmail = userEmail,
            viewModel = viewModel,
            onDismiss = { showEditDialog = false }
        )
    }

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
                    onClick = { showEditDialog = true },
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = RoundedCornerShape(10.dp),
                    enabled = !isProcessing
                ) {
                    Text("Edit", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }

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

@Composable
fun VideoEditDialog(
    video: SavedVideo,
    userEmail: String,
    viewModel: NotificationSetterViewModel,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    
    var startMs by remember { mutableStateOf(video.trimStartMs?.toString() ?: "0") }
    var endMs by remember { mutableStateOf(video.trimEndMs?.toString() ?: video.durationMs.toString()) }
    var customAudioUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var videoScale by remember { mutableStateOf(video.videoScale ?: 1.0f) }
    var namePositionY by remember { mutableStateOf(video.namePositionY ?: 0.1f) }
    var answerStyle by remember { mutableStateOf(video.answerStyle ?: "swipe") }
    var nameFontSize by remember { mutableStateOf(video.nameFontSize ?: 15f) }
    var nameFontFamily by remember { mutableStateOf(video.nameFontFamily ?: "sans-serif") }
    
    // Custom color states initialized from saved parameters or defaults
    var selectedTextColorHex by remember {
        mutableStateOf(
            video.nameTextColor?.let { String.format("#%06X", it and 0xFFFFFF) } ?: "#FFFFFF"
        )
    }
    
    val initialBgHex = remember {
        val c = video.nameBgColor ?: android.graphics.Color.parseColor("#80000000")
        if (video.nameBgColor == android.graphics.Color.TRANSPARENT) "transparent"
        else String.format("#%06X", c and 0xFFFFFF)
    }
    
    val initialAlpha = remember {
        val c = video.nameBgColor ?: android.graphics.Color.parseColor("#80000000")
        if (video.nameBgColor == android.graphics.Color.TRANSPARENT) 0f
        else ((c ushr 24) and 0xFF) / 255f
    }
    
    var bgBaseColorHex by remember { mutableStateOf(initialBgHex) }
    var bgAlpha by remember { mutableStateOf(initialAlpha) }
    var isFullPreview by remember { mutableStateOf(false) }
    var videoFilter by remember { mutableStateOf(video.videoFilter ?: "normal") }
    var trimRange by remember {
        val sVal = startMs.toFloatOrNull() ?: 0f
        val eVal = endMs.toFloatOrNull() ?: video.durationMs.toFloat()
        mutableStateOf(sVal..eVal)
    }
    
    val audioPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            customAudioUri = uri
        }
    }

    val callerFontFamily = remember(nameFontFamily) {
        when (nameFontFamily.lowercase()) {
            "serif" -> androidx.compose.ui.text.font.FontFamily.Serif
            "monospace" -> androidx.compose.ui.text.font.FontFamily.Monospace
            "cursive" -> androidx.compose.ui.text.font.FontFamily.Cursive
            else -> androidx.compose.ui.text.font.FontFamily.SansSerif
        }
    }

    // Dynamic ARGB text and background colors computed on state updates
    val parsedBgColor = remember(bgBaseColorHex, bgAlpha) {
        if (bgBaseColorHex == "transparent") {
            android.graphics.Color.TRANSPARENT
        } else {
            try {
                val baseColor = android.graphics.Color.parseColor(bgBaseColorHex)
                val alphaByte = (bgAlpha * 255f).roundToInt().coerceIn(0, 255)
                (alphaByte shl 24) or (baseColor and 0x00FFFFFF)
            } catch (e: Exception) {
                android.graphics.Color.TRANSPARENT
            }
        }
    }

    val parsedTextColor = remember(selectedTextColorHex) {
        try {
            android.graphics.Color.parseColor(selectedTextColorHex)
        } catch (e: Exception) {
            android.graphics.Color.WHITE
        }
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        androidx.compose.material3.Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (isFullPreview) {
                    // ==================== FULL SCREEN PREVIEW MODE ====================
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black)
                    ) {
                        // Full Screen Video Player Preview
                        androidx.compose.ui.viewinterop.AndroidView(
                            factory = { ctx ->
                                android.widget.VideoView(ctx).apply {
                                    // Set 32-bit high-quality color format to prevent color banding
                                    holder.setFormat(android.graphics.PixelFormat.RGBA_8888)
                                    setVideoURI(android.net.Uri.fromFile(File(video.localFilePath)))
                                    setOnPreparedListener { mp ->
                                        mp.isLooping = true
                                        // Set high-fidelity scaling mode to scale and crop perfectly instead of stretching
                                        try {
                                            mp.setVideoScalingMode(android.media.MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING)
                                        } catch (_: Exception) {}
                                        mp.setVolume(0f, 0f) // Mute preview so it doesn't disturb editing
                                        val sMs = startMs.toLongOrNull() ?: 0L
                                        if (sMs > 0) {
                                            mp.seekTo(sMs.toInt())
                                        }
                                        start()
                                    }
                                }
                            },
                            update = { view ->
                                val sMs = startMs.toLongOrNull() ?: 0L
                                val eMs = endMs.toLongOrNull() ?: 0L
                                
                                val lastStart = view.getTag(context.hashCode()) as? Long ?: -1L
                                if (lastStart != sMs) {
                                    view.setTag(context.hashCode(), sMs)
                                    try {
                                        view.seekTo(sMs.toInt())
                                    } catch (_: Exception) {}
                                }

                                if (eMs > 0 && eMs > sMs) {
                                    view.postDelayed(object : Runnable {
                                        override fun run() {
                                            try {
                                                if (view.isPlaying && view.currentPosition >= eMs) {
                                                    view.seekTo(sMs.coerceAtLeast(0).toInt())
                                                }
                                                view.postDelayed(this, 100)
                                            } catch (_: Exception) {}
                                        }
                                    }, 100)
                                }
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .scale(videoScale)
                        )

                        // Visual Filter Overlay for Full Screen
                        VideoFilterOverlay(filter = videoFilter)

                        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                            val fullHeight = maxHeight
                            // Draggable Caller Name Overlay (Full Screen)
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .offset {
                                        androidx.compose.ui.unit.IntOffset(
                                            0,
                                            (fullHeight.toPx() * namePositionY).roundToInt()
                                        )
                                    }
                                    .pointerInput(Unit) {
                                        detectVerticalDragGestures { change, dragAmount ->
                                            change.consume()
                                            val newY = namePositionY + (dragAmount / fullHeight.toPx())
                                            namePositionY = newY.coerceIn(0f, 0.85f)
                                        }
                                    }
                                    .background(Color(parsedBgColor), RoundedCornerShape(16.dp))
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = "Ravi",
                                    color = Color(parsedTextColor),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = nameFontSize.sp,
                                    fontFamily = callerFontFamily
                                )
                            }
                        }

                        // Bottom Controls Area
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .padding(bottom = 56.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Incoming Call",
                                color = Color.White,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            if (answerStyle == "buttons") {
                                Text(
                                    text = "Tap Accept to answer · Dismiss to reject",
                                    color = Color.White.copy(alpha = 0.8f),
                                    fontSize = 13.sp
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RoundCallButton(
                                        icon = Icons.Default.CallEnd,
                                        contentDescription = "Dismiss call",
                                        backgroundColor = Color(0xFFE53935),
                                        onClick = { }
                                    )
                                    RoundCallButton(
                                        icon = Icons.Default.Call,
                                        contentDescription = "Accept call",
                                        backgroundColor = Color(0xFF4CAF50),
                                        onClick = { }
                                    )
                                }
                            } else {
                                Text(
                                    text = "Swipe to answer",
                                    color = Color.White.copy(alpha = 0.8f),
                                    fontSize = 13.sp
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                SwipeToAnswerButton(onAccept = {}, onDismiss = {})
                            }
                        }

                        // Close/Exit Floating Button at Top Left
                        Button(
                            onClick = { isFullPreview = false },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Black.copy(alpha = 0.6f)),
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(16.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Clear, contentDescription = "Exit Full Screen", modifier = Modifier.size(16.dp))
                                Text("Exit Full Screen", fontSize = 12.sp)
                            }
                        }
                    }
                } else {
                    // ==================== EDITOR SPLIT-SCREEN MODE ====================
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Top Navigation / Save bar
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = onDismiss) { Text("Cancel") }
                            Text("Edit Wallpaper", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            TextButton(onClick = {
                                val parsedStart = startMs.toLongOrNull()
                                val parsedEnd = endMs.toLongOrNull()
                                
                                var finalAudioPath = video.customAudioPath
                                if (customAudioUri != null) {
                                    finalAudioPath = VideoLibraryManager.importAudioForVideo(context, customAudioUri!!)
                                }
                                
                                val updatedVideo = video.copy(
                                    trimStartMs = parsedStart,
                                    trimEndMs = parsedEnd,
                                    customAudioPath = finalAudioPath,
                                    videoScale = videoScale,
                                    namePositionY = namePositionY,
                                    answerStyle = answerStyle,
                                    nameFontSize = nameFontSize,
                                    nameFontFamily = nameFontFamily,
                                    nameTextColor = parsedTextColor,
                                    nameBgColor = parsedBgColor,
                                    videoFilter = videoFilter
                                )
                                viewModel.updateSavedVideo(context, userEmail, updatedVideo)
                                onDismiss()
                            }) {
                                Text("Save", fontWeight = FontWeight.Bold)
                            }
                        }

                        // High-Fidelity 9:16 Mockup Preview Frame
                        BoxWithConstraints(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1.3f)
                                .background(Color(0xFF0F0F14)) // Soft dark studio backdrop
                                .padding(12.dp)
                        ) {
                            val containerWidth = maxWidth
                            val containerHeight = maxHeight
                            
                            // Fit 9:16 phone card ratio precisely inside the allocated space
                            val calculatedWidth = remember(containerWidth, containerHeight) {
                                val widthBasedOnHeight = containerHeight * (9f / 16f)
                                if (widthBasedOnHeight <= containerWidth) {
                                    widthBasedOnHeight
                                } else {
                                    containerWidth
                                }
                            }
                            val calculatedHeight = remember(calculatedWidth) {
                                calculatedWidth * (16f / 9f)
                            }

                            Box(
                                modifier = Modifier
                                    .size(calculatedWidth, calculatedHeight)
                                    .align(Alignment.Center)
                                    .background(Color.Black)
                                    .border(2.dp, Color(0xFF3A3A3C), RoundedCornerShape(24.dp))
                                    .clip(RoundedCornerShape(24.dp))
                            ) {
                                // 1. Video content
                                androidx.compose.ui.viewinterop.AndroidView(
                                    factory = { ctx ->
                                        android.widget.VideoView(ctx).apply {
                                            // Set 32-bit high-quality color format to prevent color banding
                                            holder.setFormat(android.graphics.PixelFormat.RGBA_8888)
                                            setVideoURI(android.net.Uri.fromFile(File(video.localFilePath)))
                                            setOnPreparedListener { mp ->
                                                mp.isLooping = true
                                                // Set high-fidelity scaling mode to scale and crop perfectly instead of stretching
                                                try {
                                                    mp.setVideoScalingMode(android.media.MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING)
                                                } catch (_: Exception) {}
                                                mp.setVolume(0f, 0f) // Mute preview so it doesn't disturb editing
                                                val sMs = startMs.toLongOrNull() ?: 0L
                                                if (sMs > 0) {
                                                    mp.seekTo(sMs.toInt())
                                                }
                                                start()
                                            }
                                        }
                                    },
                                    update = { view ->
                                        val sMs = startMs.toLongOrNull() ?: 0L
                                        val eMs = endMs.toLongOrNull() ?: 0L
                                        
                                        val lastStart = view.getTag(context.hashCode()) as? Long ?: -1L
                                        if (lastStart != sMs) {
                                            view.setTag(context.hashCode(), sMs)
                                            try {
                                                view.seekTo(sMs.toInt())
                                            } catch (_: Exception) {}
                                        }

                                        if (eMs > 0 && eMs > sMs) {
                                            view.postDelayed(object : Runnable {
                                                override fun run() {
                                                    try {
                                                        if (view.isPlaying && view.currentPosition >= eMs) {
                                                            view.seekTo(sMs.coerceAtLeast(0).toInt())
                                                        }
                                                        view.postDelayed(this, 100)
                                                    } catch (_: Exception) {}
                                                }
                                            }, 100)
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .scale(videoScale)
                                )

                                // Visual Filter Overlay for Card Preview
                                VideoFilterOverlay(filter = videoFilter)

                                // 2. Interactive draggable caller name overlay
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopCenter)
                                        .offset {
                                            androidx.compose.ui.unit.IntOffset(
                                                0,
                                                (calculatedHeight.toPx() * namePositionY).roundToInt()
                                            )
                                        }
                                        .pointerInput(Unit) {
                                            detectVerticalDragGestures { change, dragAmount ->
                                                change.consume()
                                                val newY = namePositionY + (dragAmount / calculatedHeight.toPx())
                                                namePositionY = newY.coerceIn(0f, 0.85f)
                                            }
                                        }
                                        .background(Color(parsedBgColor), RoundedCornerShape(16.dp))
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = "Ravi",
                                        color = Color(parsedTextColor),
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = (nameFontSize * 0.85f).sp, // Scaled for mockup
                                        fontFamily = callerFontFamily,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                // 3. Incoming Call Header & Animated Buttons (Scaled for device mockup)
                                Column(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .fillMaxWidth()
                                        .padding(bottom = 16.dp)
                                        .scale(0.7f), // Professional scaling to fit device frame perfectly
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "Incoming Call",
                                        color = Color.White,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    
                                    if (answerStyle == "buttons") {
                                        Text(
                                            text = "Tap Accept · Dismiss",
                                            color = Color.White.copy(alpha = 0.8f),
                                            fontSize = 10.sp
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            RoundCallButton(
                                                icon = Icons.Default.CallEnd,
                                                contentDescription = "Dismiss call",
                                                backgroundColor = Color(0xFFE53935),
                                                onClick = { }
                                            )
                                            RoundCallButton(
                                                icon = Icons.Default.Call,
                                                contentDescription = "Accept call",
                                                backgroundColor = Color(0xFF4CAF50),
                                                onClick = { }
                                            )
                                        }
                                    } else {
                                        Text(
                                            text = "Swipe to answer",
                                            color = Color.White.copy(alpha = 0.8f),
                                            fontSize = 10.sp
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        SwipeToAnswerButton(onAccept = {}, onDismiss = {})
                                    }
                                }
                            }

                            // Full Screen mode launcher floating button
                            Button(
                                onClick = { isFullPreview = true },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Black.copy(alpha = 0.6f)),
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp)
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Text("Full Screen", fontSize = 11.sp)
                                }
                            }
                        }

                        // Controls Area
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .verticalScroll(androidx.compose.foundation.rememberScrollState())
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Name Font Configuration
                            Column {
                                Text("Caller Name Font:", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf("Sans-Serif", "Serif", "Monospace", "Cursive").forEach { font ->
                                        androidx.compose.material3.FilterChip(
                                            selected = nameFontFamily.equals(font, ignoreCase = true),
                                            onClick = { nameFontFamily = font.lowercase() },
                                            label = { Text(font) }
                                        )
                                    }
                                }
                            }

                            // Font size slider
                            Column {
                                Text("Caller Name Size: ${nameFontSize.toInt()}sp", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                androidx.compose.material3.Slider(
                                    value = nameFontSize,
                                    onValueChange = { nameFontSize = it },
                                    valueRange = 10f..40f
                                )
                            }

                            // Caller position slider
                            Column {
                                Text("Caller Name Position (or drag in preview):", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                androidx.compose.material3.Slider(
                                    value = namePositionY,
                                    onValueChange = { namePositionY = it },
                                    valueRange = 0.0f..0.8f
                                )
                            }

                            // Text color picker swatches (using beautiful, professional Google/Apple palette colors)
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Caller Name Text Color:", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    val textPresets = listOf(
                                        "#000000" to "Black",
                                        "#333333" to "Charcoal",
                                        "#FFFFFF" to "White",
                                        "#F5F5F7" to "Alabaster",
                                        "#E6C79C" to "Gold",
                                        "#E0F7FA" to "Ice",
                                        "#FFD1DC" to "Rose"
                                    )
                                    textPresets.forEach { (hex, label) ->
                                        val isSelected = selectedTextColorHex.equals(hex, ignoreCase = true)
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .background(Color(android.graphics.Color.parseColor(hex)), CircleShape)
                                                .border(
                                                    width = if (isSelected) 3.dp else 1.dp,
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.3f),
                                                    shape = CircleShape
                                                )
                                                .clickable { selectedTextColorHex = hex },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isSelected) {
                                                Icon(
                                                    imageVector = Icons.Default.CheckCircle,
                                                    contentDescription = "Selected",
                                                    tint = if (hex.equals("#000000", ignoreCase = true) || hex.equals("#333333", ignoreCase = true)) Color.White else Color.DarkGray,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Background color picker swatches (incorporates None/Transparent option to eliminate black background)
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Caller Name Background Color:", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    val bgPresets = listOf(
                                        "transparent" to "None",
                                        "#1C1C1E" to "Charcoal",
                                        "#000000" to "Black",
                                        "#2C3E50" to "Slate",
                                        "#1E3A2F" to "Emerald",
                                        "#1A1B2F" to "Navy",
                                        "#2D1F1F" to "Burgundy"
                                    )
                                    bgPresets.forEach { (hex, label) ->
                                        val isSelected = bgBaseColorHex.equals(hex, ignoreCase = true)
                                        val isNone = hex == "transparent"
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .background(
                                                    color = if (isNone) Color.Transparent else Color(android.graphics.Color.parseColor(hex)),
                                                    shape = CircleShape
                                                )
                                                .border(
                                                    width = if (isSelected) 3.dp else 1.dp,
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.3f),
                                                    shape = CircleShape
                                                )
                                                .clickable {
                                                    bgBaseColorHex = hex
                                                    if (isNone) bgAlpha = 0f else if (bgAlpha == 0f) bgAlpha = 0.5f
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isNone) {
                                                androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                                                    drawLine(
                                                        color = Color.Red,
                                                        start = androidx.compose.ui.geometry.Offset(0f, size.height),
                                                        end = androidx.compose.ui.geometry.Offset(size.width, 0f),
                                                        strokeWidth = 2.dp.toPx()
                                                    )
                                                }
                                            }
                                            if (isSelected) {
                                                Icon(
                                                    imageVector = Icons.Default.CheckCircle,
                                                    contentDescription = "Selected",
                                                    tint = if (isNone) Color.Red else Color.White,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Dynamic opacity slider, visible only when a background is active
                            if (bgBaseColorHex != "transparent") {
                                Column {
                                    Text("Background Opacity: ${(bgAlpha * 100).roundToInt()}%", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                    androidx.compose.material3.Slider(
                                        value = bgAlpha,
                                        onValueChange = { bgAlpha = it },
                                        valueRange = 0.0f..1.0f
                                    )
                                }
                            }
                            
                            // Answer call interface selector
                            Column {
                                Text("Answer Call Interface:", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    androidx.compose.material3.FilterChip(
                                        selected = answerStyle == "swipe",
                                        onClick = { answerStyle = "swipe" },
                                        label = { Text("Horizontal Swipe") }
                                    )
                                    androidx.compose.material3.FilterChip(
                                        selected = answerStyle == "buttons",
                                        onClick = { answerStyle = "buttons" },
                                        label = { Text("Two Buttons") }
                                    )
                                }
                            }

                            // Zoom slider
                            Column {
                                Text("Video Zoom: ${String.format(java.util.Locale.US, "%.1fx", videoScale)}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                androidx.compose.material3.Slider(
                                    value = videoScale,
                                    onValueChange = { videoScale = it },
                                    valueRange = 0.5f..3.0f
                                )
                            }
                            
                            // Visual Trimming Timeline RangeSlider with fine-tuning direct inputs
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Video Trimming Timeline",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "${String.format(java.util.Locale.US, "%.1f", trimRange.start / 1000f)}s - ${String.format(java.util.Locale.US, "%.1f", trimRange.endInclusive / 1000f)}s",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                androidx.compose.material3.RangeSlider(
                                    value = trimRange,
                                    onValueChange = { range ->
                                        trimRange = range
                                        startMs = range.start.roundToInt().toString()
                                        endMs = range.endInclusive.roundToInt().toString()
                                    },
                                    valueRange = 0f..video.durationMs.toFloat(),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    androidx.compose.material3.OutlinedTextField(
                                        value = startMs,
                                        onValueChange = {
                                            startMs = it
                                            val sVal = it.toFloatOrNull() ?: 0f
                                            trimRange = sVal.coerceIn(0f, trimRange.endInclusive)..trimRange.endInclusive
                                        },
                                        label = { Text("Start (ms)") },
                                        modifier = Modifier.weight(1f),
                                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                                    )
                                    androidx.compose.material3.OutlinedTextField(
                                        value = endMs,
                                        onValueChange = {
                                            endMs = it
                                            val eVal = it.toFloatOrNull() ?: video.durationMs.toFloat()
                                            trimRange = trimRange.start..eVal.coerceIn(trimRange.start, video.durationMs.toFloat())
                                        },
                                        label = { Text("End (ms)") },
                                        modifier = Modifier.weight(1f),
                                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Video Enhancer Filters selector
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("Video Quality Enhancer Filters", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                        .padding(8.dp)
                                        .horizontalScroll(androidx.compose.foundation.rememberScrollState())
                                ) {
                                    val filters = listOf(
                                        "normal" to "Original",
                                        "vivid" to "Vivid Pop",
                                        "warm" to "Cinematic",
                                        "cyberpunk" to "Cyberpunk",
                                        "hdr" to "HDR Contrast",
                                        "noir" to "Noir B&W"
                                    )
                                    filters.forEach { (filterKey, filterName) ->
                                        val isSelected = videoFilter.equals(filterKey, ignoreCase = true)
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                                                .border(
                                                    width = 1.dp,
                                                    color = if (isSelected) Color.Transparent else Color.Gray.copy(alpha = 0.4f),
                                                    shape = RoundedCornerShape(16.dp)
                                                )
                                                .clickable { videoFilter = filterKey }
                                                .padding(horizontal = 14.dp, vertical = 8.dp)
                                        ) {
                                            Text(
                                                text = filterName,
                                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                            }

                            // Custom Audio Picker
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Custom Audio:", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Button(onClick = { audioPicker.launch(arrayOf("audio/*")) }) {
                                    Text(if (customAudioUri != null || video.customAudioPath != null) "Change Audio" else "Select Audio")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoFilterOverlay(filter: String?, modifier: Modifier = Modifier) {
    if (filter == null || filter == "normal") return
    
    val brush = when (filter.lowercase()) {
        "vivid" -> {
            androidx.compose.ui.graphics.Brush.verticalGradient(
                colors = listOf(
                    Color(0xFFFF5722).copy(alpha = 0.08f),
                    Color(0xFFE040FB).copy(alpha = 0.05f),
                    Color(0xFF00E5FF).copy(alpha = 0.08f)
                )
            )
        }
        "warm" -> {
            androidx.compose.ui.graphics.Brush.verticalGradient(
                colors = listOf(
                    Color(0xFFFFB74D).copy(alpha = 0.16f),
                    Color(0xFFFF9800).copy(alpha = 0.10f),
                    Color(0xFFE65100).copy(alpha = 0.06f)
                )
            )
        }
        "cyberpunk" -> {
            androidx.compose.ui.graphics.Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF00F0FF).copy(alpha = 0.14f),
                    Color(0xFFFF007F).copy(alpha = 0.16f),
                    Color(0xFF7000FF).copy(alpha = 0.12f)
                )
            )
        }
        "hdr" -> {
            androidx.compose.ui.graphics.Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.06f),
                    Color.Transparent,
                    Color.Black.copy(alpha = 0.22f)
                )
            )
        }
        "noir" -> {
            androidx.compose.ui.graphics.Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF3E2723).copy(alpha = 0.18f),
                    Color(0xFF212121).copy(alpha = 0.22f),
                    Color(0xFF0D0D0D).copy(alpha = 0.28f)
                )
            )
        }
        else -> null
    }

    if (brush != null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(brush)
        )
    }
}
