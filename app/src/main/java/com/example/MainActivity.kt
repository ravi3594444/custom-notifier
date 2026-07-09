package com.example

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.rememberDrawerState
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                val options = FirebaseOptions.Builder()
                    .setApiKey("AIzaSyFakeKeyPlaceholderForAppletInitializationOnly")
                    .setApplicationId("1:1234567890:android:fakeappletid")
                    .setProjectId("custom-notifier-dummy")
                    .build()
                FirebaseApp.initializeApp(this, options)
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Firebase initialization failed: ${e.message}", e)
        }
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val navController = rememberNavController()
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = "splash",
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable("splash") {
                            SplashScreen(onLoadingFinished = {
                                navController.navigate("login") {
                                    popUpTo("splash") { inclusive = true }
                                }
                            })
                        }
                        composable("login") {
                            LoginScreen(onLoginSuccess = { email ->
                                navController.navigate("home/$email") {
                                    popUpTo("login") { inclusive = true }
                                }
                            })
                        }
                        composable("home/{email}") { backStackEntry ->
                            val email = backStackEntry.arguments?.getString("email") ?: ""
                            NotificationSetterScreen(userEmail = email)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AudioEqualizerAnimation(isPlaying: Boolean) {
    Row(
        modifier = Modifier
            .height(40.dp)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val barCount = 12
        for (i in 0 until barCount) {
            var heightFactor by remember { mutableStateOf(0.2f + (i % 3) * 0.25f) }
            LaunchedEffect(isPlaying) {
                if (isPlaying) {
                    while (true) {
                        heightFactor = (15..100).random() / 100f
                        delay((80..150).random().toLong())
                    }
                } else {
                    heightFactor = 0.2f
                }
            }
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight(heightFactor)
                    .background(
                        color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(2.dp)
                    )
            )
        }
    }
}

@Composable
fun FileInputWidget(
    selectedFileName: String,
    selectedFileSize: String,
    onPickFileClick: () -> Unit,
    onClearClick: () -> Unit,
    isProcessing: Boolean
) {
    if (selectedFileName.isEmpty()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(16.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudUpload,
                        contentDescription = "Upload Icon",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Select Sound File",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Pick audio or video files from your device",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = onPickFileClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    enabled = !isProcessing
                ) {
                    Icon(
                        imageVector = Icons.Default.FileOpen,
                        contentDescription = "Browse files",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Browse Local Files", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    } else {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Success",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = selectedFileName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                        if (selectedFileSize.isNotEmpty()) {
                            Text(
                                text = selectedFileSize,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
                Row {
                    TextButton(
                        onClick = onPickFileClick,
                        enabled = !isProcessing
                    ) {
                        Text("Change", fontWeight = FontWeight.SemiBold)
                    }
                    IconButton(
                        onClick = onClearClick,
                        enabled = !isProcessing
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Clear selected file",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AudioPlayerCard(
    fileName: String,
    fileSize: String,
    isPlaying: Boolean,
    currentPlaybackPos: Float,
    trimRange: ClosedFloatingPointRange<Float>,
    onPlayPauseToggle: () -> Unit,
    onSeek: (Float) -> Unit,
    onRewind5s: () -> Unit,
    onForward5s: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = "Music Playing",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Sound Preview",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (fileSize.isNotBlank()) {
                    Text(
                        text = fileSize,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            AudioEqualizerAnimation(isPlaying = isPlaying)

            Spacer(modifier = Modifier.height(16.dp))

            Slider(
                value = currentPlaybackPos,
                onValueChange = onSeek,
                valueRange = trimRange.start..trimRange.endInclusive,
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                )
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatTime(currentPlaybackPos.toLong()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = formatTime(trimRange.endInclusive.toLong()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(
                    onClick = onRewind5s,
                    modifier = Modifier.size(48.dp),
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.FastRewind,
                        contentDescription = "Rewind 5 seconds",
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                FilledIconButton(
                    onClick = onPlayPauseToggle,
                    modifier = Modifier.size(64.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause Sound",
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                IconButton(
                    onClick = onForward5s,
                    modifier = Modifier.size(48.dp),
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.FastForward,
                        contentDescription = "Forward 5 seconds",
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ProcessingCard(
    isProcessing: Boolean,
    statusText: String
) {
    if (isProcessing) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = statusText.ifEmpty { "Processing your file..." },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Please keep the app open during processing",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
            }
        }
    }
}

fun getBase64FromFile(file: File): String? {
    return try {
        val bytes = file.readBytes()
        android.util.Base64.encodeToString(bytes, android.util.Base64.DEFAULT)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

fun getUriFromBase64(context: Context, base64Str: String, fileName: String): Uri? {
    return try {
        val bytes = android.util.Base64.decode(base64Str, android.util.Base64.DEFAULT)
        val file = File(context.cacheDir, fileName)
        file.writeBytes(bytes)
        Uri.fromFile(file)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

@Composable
fun NotificationSetterScreen(modifier: Modifier = Modifier, userEmail: String = "") {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isProcessing by remember { mutableStateOf(false) }
    var processingStatus by remember { mutableStateOf("") }

    var selectedMediaUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFileName by remember { mutableStateOf("") }
    var selectedFileSize by remember { mutableStateOf("") }
    var mediaDurationMs by remember { mutableStateOf(0L) }
    var trimRange by remember { mutableStateOf(0f..100f) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentPlaybackPos by remember { mutableStateOf(0f) }

    val mediaPlayer = remember { MediaPlayer() }

    var currentSystemNotificationSound by remember { mutableStateOf("Checking...") }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    var currentScreen by remember { mutableStateOf("customizer") }

    LaunchedEffect(Unit) {
        currentSystemNotificationSound = getCurrentNotificationSoundName(context)
    }

    LaunchedEffect(userEmail) {
        if (userEmail.isNotEmpty()) {
            isProcessing = true
            processingStatus = "Loading cloud sound preference..."
            try {
                val db = FirebaseFirestore.getInstance()
                db.collection("users").document(userEmail).get()
                    .addOnSuccessListener { document ->
                        if (document != null && document.exists()) {
                            val fileName = document.getString("selectedFileName") ?: ""
                            val fileSize = document.getString("selectedFileSize") ?: ""
                            val durationMs = document.getLong("mediaDurationMs") ?: 0L
                            val start = document.getDouble("trimRangeStart")?.toFloat() ?: 0f
                            val end = document.getDouble("trimRangeEnd")?.toFloat() ?: durationMs.toFloat()
                            val audioBase64 = document.getString("audioBase64")
                            
                            if (fileName.isNotEmpty() && audioBase64 != null) {
                                try {
                                    selectedFileName = fileName
                                    selectedFileSize = fileSize
                                    mediaDurationMs = durationMs
                                    trimRange = start..end
                                    
                                    val restoredUri = getUriFromBase64(context, audioBase64, fileName)
                                    if (restoredUri != null) {
                                        selectedMediaUri = restoredUri
                                        mediaPlayer.reset()
                                        mediaPlayer.setDataSource(context, restoredUri)
                                        mediaPlayer.prepare()
                                        currentPlaybackPos = start
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }
                        isProcessing = false
                    }
                    .addOnFailureListener {
                        isProcessing = false
                    }
            } catch (e: Exception) {
                isProcessing = false
                e.printStackTrace()
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer.release()
        }
    }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            mediaPlayer.start()
            while (isPlaying) {
                currentPlaybackPos = mediaPlayer.currentPosition.toFloat()
                if (currentPlaybackPos >= trimRange.endInclusive) {
                    mediaPlayer.pause()
                    mediaPlayer.seekTo(trimRange.start.toInt())
                    currentPlaybackPos = trimRange.start
                    isPlaying = false
                }
                delay(50)
            }
        } else {
            mediaPlayer.pause()
        }
    }

    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                isProcessing = true
                processingStatus = "Reading selected file..."
                
                try {
                    context.contentResolver.query(it, null, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val nameIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                            val sizeIndex = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
                            if (nameIndex != -1) {
                                selectedFileName = cursor.getString(nameIndex)
                            } else {
                                selectedFileName = "sound_clip.mp3"
                            }
                            if (sizeIndex != -1) {
                                selectedFileSize = formatFileSize(cursor.getLong(sizeIndex))
                            } else {
                                selectedFileSize = "Unknown Size"
                            }
                        }
                    }
                } catch (e: Exception) {
                    selectedFileName = "audio_file.mp3"
                    selectedFileSize = ""
                }

                val mimeType = context.contentResolver.getType(it) ?: ""
                val uriToUse = if (mimeType.startsWith("video/")) {
                    processingStatus = "Extracting high-quality audio track..."
                    val cacheFile = File(context.cacheDir, "extracted_audio_${System.currentTimeMillis()}.m4a")
                    val success = extractAudioFromVideo(context, it, cacheFile)
                    if (success) Uri.fromFile(cacheFile) else it
                } else {
                    it
                }

                try {
                    processingStatus = "Preparing preview player..."
                    mediaPlayer.reset()
                    mediaPlayer.setDataSource(context, uriToUse)
                    mediaPlayer.prepare()
                    val duration = mediaPlayer.duration.toLong()
                    mediaDurationMs = duration
                    trimRange = 0f..duration.toFloat()
                    currentPlaybackPos = 0f
                    selectedMediaUri = uriToUse
                } catch(e: Exception) {
                    Toast.makeText(context, "Failed to load media file", Toast.LENGTH_SHORT).show()
                    selectedFileName = ""
                    selectedFileSize = ""
                }
                isProcessing = false
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(300.dp)
            ) {
                Spacer(modifier = Modifier.height(16.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.app_logo_icon_1783527284606),
                        contentDescription = "App Logo",
                        modifier = Modifier
                            .size(80.dp)
                            .clip(RoundedCornerShape(20.dp)),
                        contentScale = ContentScale.Fit
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "NOTIFIER",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Customize Your Soundscapes",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                NavigationDrawerItem(
                    label = { Text("Sound Customizer", fontWeight = FontWeight.Bold) },
                    selected = currentScreen == "customizer",
                    onClick = {
                        currentScreen = "customizer"
                        scope.launch { drawerState.close() }
                    },
                    icon = { Icon(Icons.Default.Home, contentDescription = null) },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )

                NavigationDrawerItem(
                    label = { Text("Developer Version", fontWeight = FontWeight.Bold) },
                    selected = currentScreen == "developer",
                    onClick = {
                        currentScreen = "developer"
                        scope.launch { drawerState.close() }
                    },
                    icon = { Icon(Icons.Default.Person, contentDescription = null) },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )

                Spacer(modifier = Modifier.weight(1f))

                Text(
                    text = "Version 1.0.0\nDeveloped by Ravi",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp)
                )
            }
        }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (currentScreen == "customizer") {
                Column(
                    modifier = modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Top
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { scope.launch { drawerState.open() } },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Open Drawer",
                                modifier = Modifier.size(28.dp)
                            )
                        }
            TextButton(
                onClick = {
                    scope.launch(Dispatchers.IO) {
                        try {
                            val cacheFiles = context.cacheDir.listFiles()
                            cacheFiles?.forEach { file ->
                                if (file.isFile) {
                                    file.delete()
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        withContext(Dispatchers.Main) {
                            try {
                                isPlaying = false
                                if (mediaPlayer.isPlaying) {
                                    mediaPlayer.stop()
                                }
                                mediaPlayer.reset()
                            } catch (e: Exception) {
                                // ignore
                            }
                            selectedMediaUri = null
                            selectedFileName = ""
                            selectedFileSize = ""
                            mediaDurationMs = 0L
                            trimRange = 0f..100f
                            currentPlaybackPos = 0f
                            processingStatus = ""
                            isProcessing = false
                            currentSystemNotificationSound = getCurrentNotificationSoundName(context)
                            Toast.makeText(context, "Session cleared & temporary files removed", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
                enabled = !isProcessing,
                modifier = Modifier.testTag("clear_session_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Clear Session",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Clear Session",
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .size(100.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), RoundedCornerShape(28.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.NotificationsActive,
                contentDescription = "Notification Icon",
                modifier = Modifier.size(54.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Custom Notifier",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp
            ),
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Select any local sound or video file, trim the sweet spot, normalize the audio, and set it as your notification sound.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Card displaying the current active system notification sound
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
            ),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = "Notification Icon",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "System Notification Sound",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = {
                            currentSystemNotificationSound = getCurrentNotificationSoundName(context)
                            Toast.makeText(context, "Current sound refreshed", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh Current Sound",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(6.dp))
                
                Text(
                    text = currentSystemNotificationSound,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp
                    ),
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                
                Spacer(modifier = Modifier.height(10.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            if (!Settings.System.canWrite(context)) {
                                val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                context.startActivity(intent)
                                Toast.makeText(context, "Please allow Write Settings permission and try again.", Toast.LENGTH_LONG).show()
                            } else {
                                try {
                                    // 1. Delete custom ringtones from MediaStore to clean them up
                                    deleteCustomRingtonesFromSystem(context)
                                    
                                    // 2. Fetch concrete system default URIs
                                    val defaultNotificationUri = getSystemDefaultNotificationUri(context)
                                    val defaultRingtoneUri = getSystemDefaultRingtoneUri(context)
                                    
                                    // 3. Set actual defaults back to system-provided native sounds
                                    RingtoneManager.setActualDefaultRingtoneUri(
                                        context,
                                        RingtoneManager.TYPE_NOTIFICATION,
                                        defaultNotificationUri
                                    )
                                    try {
                                        RingtoneManager.setActualDefaultRingtoneUri(
                                            context,
                                            RingtoneManager.TYPE_RINGTONE,
                                            defaultRingtoneUri
                                        )
                                    } catch (e: Exception) {}
                                    
                                    currentSystemNotificationSound = getCurrentNotificationSoundName(context)
                                    
                                    // Clear Firestore preferences document if email is provided
                                    if (userEmail.isNotEmpty()) {
                                        try {
                                            val db = FirebaseFirestore.getInstance()
                                            db.collection("users").document(userEmail).delete()
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
                                    }
                                    
                                    // Reset active loaded media file states from dashboard
                                    isPlaying = false
                                    try {
                                        if (mediaPlayer.isPlaying) {
                                            mediaPlayer.stop()
                                        }
                                        mediaPlayer.reset()
                                    } catch (e: Exception) {}
                                    selectedMediaUri = null
                                    selectedFileName = ""
                                    selectedFileSize = ""
                                    mediaDurationMs = 0L
                                    trimRange = 0f..100f
                                    currentPlaybackPos = 0f
                                    
                                    Toast.makeText(context, "Notification sound reset to default!", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Failed to reset sound: ${e.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Reset Sound",
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Remove Custom / Reset",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    TextButton(
                        onClick = {
                            try {
                                val intent = Intent(Settings.ACTION_SOUND_SETTINGS)
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Sound settings not accessible directly.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Open Settings",
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "System Sound Settings",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        ProcessingCard(isProcessing = isProcessing, statusText = processingStatus)

        FileInputWidget(
            selectedFileName = selectedFileName,
            selectedFileSize = selectedFileSize,
            onPickFileClick = {
                if (!Settings.System.canWrite(context)) {
                    val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(intent)
                    Toast.makeText(context, "Please allow Write Settings permission and try again.", Toast.LENGTH_LONG).show()
                } else {
                    mediaPickerLauncher.launch(arrayOf("audio/*", "video/*"))
                }
            },
            onClearClick = {
                isPlaying = false
                selectedMediaUri = null
                selectedFileName = ""
                selectedFileSize = ""
                mediaDurationMs = 0L
                trimRange = 0f..100f
                currentPlaybackPos = 0f
            },
            isProcessing = isProcessing
        )

        if (selectedMediaUri != null) {
            Spacer(modifier = Modifier.height(16.dp))

            AudioPlayerCard(
                fileName = selectedFileName,
                fileSize = selectedFileSize,
                isPlaying = isPlaying,
                currentPlaybackPos = currentPlaybackPos,
                trimRange = trimRange,
                onPlayPauseToggle = { isPlaying = !isPlaying },
                onSeek = { pos ->
                    currentPlaybackPos = pos
                    mediaPlayer.seekTo(pos.toInt())
                },
                onRewind5s = {
                    val newPos = (currentPlaybackPos - 5000f).coerceAtLeast(trimRange.start)
                    currentPlaybackPos = newPos
                    mediaPlayer.seekTo(newPos.toInt())
                },
                onForward5s = {
                    val newPos = (currentPlaybackPos + 5000f).coerceAtMost(trimRange.endInclusive)
                    currentPlaybackPos = newPos
                    mediaPlayer.seekTo(newPos.toInt())
                }
            )

            Spacer(modifier = Modifier.height(20.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Trim Range Selector",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Specify start and end ranges to keep. Selected duration: ${formatTime((trimRange.endInclusive - trimRange.start).toLong())}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    RangeSlider(
                        value = trimRange,
                        onValueChange = { 
                            val startChanged = trimRange.start != it.start
                            trimRange = it 
                            if (startChanged) {
                                mediaPlayer.seekTo(it.start.toInt())
                                currentPlaybackPos = it.start
                            } else {
                                // Preview the last 2 seconds when dragging the end handle
                                val seekPos = (it.endInclusive - 2000f).coerceAtLeast(it.start)
                                mediaPlayer.seekTo(seekPos.toInt())
                                currentPlaybackPos = seekPos
                            }
                        },
                        valueRange = 0f..mediaDurationMs.toFloat(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Start: ${formatTime(trimRange.start.toLong())}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "End: ${formatTime(trimRange.endInclusive.toLong())}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            val processAudioAndSet = { setAsNotification: Boolean, setAsRingtone: Boolean ->
                scope.launch {
                    isProcessing = true
                    isPlaying = false
                    processingStatus = "Slicing audio track segment..."
                    
                    val trimmedFile = File(context.cacheDir, "trimmed_audio_${System.currentTimeMillis()}.m4a")
                    val normalizedFile = File(context.cacheDir, "normalized_audio_${System.currentTimeMillis()}.m4a")
                    val success = trimAudio(context, selectedMediaUri!!, trimmedFile, trimRange.start.toLong(), trimRange.endInclusive.toLong())
                    var finalBase64: String? = null
                    if (success) {
                        processingStatus = "Applying volume levels normalization..."
                        val normSuccess = VolumeNormalizer.normalize(context, Uri.fromFile(trimmedFile), normalizedFile)
                        val finalFile = if (normSuccess) {
                            processingStatus = "Success! Updating ringtone database..."
                            normalizedFile
                        } else {
                            processingStatus = "Updating ringtone database..."
                            trimmedFile
                        }
                        if (normSuccess) {
                            Toast.makeText(context, "Volume normalized!", Toast.LENGTH_SHORT).show()
                        }
                        setRingtoneFromUri(context, Uri.fromFile(finalFile), isExtracted = true, setAsNotification = setAsNotification, setAsRingtone = setAsRingtone)
                        finalBase64 = getBase64FromFile(finalFile)
                    } else {
                        Toast.makeText(context, "Trimming failed, setting full audio.", Toast.LENGTH_SHORT).show()
                        setRingtoneFromUri(context, selectedMediaUri!!, isExtracted = false, setAsNotification = setAsNotification, setAsRingtone = setAsRingtone)
                        finalBase64 = try {
                            context.contentResolver.openInputStream(selectedMediaUri!!)?.use { inputStream ->
                                val bytes = inputStream.readBytes()
                                android.util.Base64.encodeToString(bytes, android.util.Base64.DEFAULT)
                            }
                        } catch (e: Exception) {
                            null
                        }
                    }

                    // Save to Firestore if email is provided
                    if (userEmail.isNotEmpty() && finalBase64 != null) {
                        try {
                            processingStatus = "Syncing with cloud storage..."
                            val db = FirebaseFirestore.getInstance()
                            val data = hashMapOf(
                                "selectedFileName" to selectedFileName,
                                "selectedFileSize" to selectedFileSize,
                                "trimRangeStart" to trimRange.start,
                                "trimRangeEnd" to trimRange.endInclusive,
                                "mediaDurationMs" to mediaDurationMs,
                                "audioBase64" to finalBase64,
                                "lastUpdated" to System.currentTimeMillis()
                            )
                            db.collection("users").document(userEmail).set(data)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    
                    currentSystemNotificationSound = getCurrentNotificationSoundName(context)
                    
                    isProcessing = false
                    selectedMediaUri = null
                    selectedFileName = ""
                    selectedFileSize = ""
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = { processAudioAndSet(true, false) },
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isProcessing
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Notification", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                Button(
                    onClick = { processAudioAndSet(false, true) },
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isProcessing
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Call Ringtone", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            TextButton(
                onClick = {
                    isPlaying = false
                    selectedMediaUri = null
                    selectedFileName = ""
                    selectedFileSize = ""
                },
                enabled = !isProcessing,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cancel", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
} else {
    DeveloperVersionScreen(
        onBackToCustomizer = { currentScreen = "customizer" },
        onMenuClick = { scope.launch { drawerState.open() } }
    )
}
}
}
}

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 KB"
    val kb = bytes / 1024f
    if (kb < 1024) return String.format("%.1f KB", kb)
    val mb = kb / 1024f
    return String.format("%.1f MB", mb)
}

fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}


suspend fun trimAudio(context: Context, inputUri: Uri, outputFile: File, startTimeMs: Long, endTimeMs: Long): Boolean = withContext(Dispatchers.IO) {
    val extractor = android.media.MediaExtractor()
    var muxer: android.media.MediaMuxer? = null
    var trackIndex = -1
    var muxerTrackIndex = -1
    try {
        extractor.setDataSource(context, inputUri, null)
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(android.media.MediaFormat.KEY_MIME)
            if (mime?.startsWith("audio/") == true) {
                // MediaMuxer only supports specific formats
                if (mime != "audio/mp4a-latm" && mime != "audio/3gpp" && mime != "audio/amr-wb") {
                    return@withContext false
                }
                extractor.selectTrack(i)
                trackIndex = i
                muxer = android.media.MediaMuxer(outputFile.absolutePath, android.media.MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                muxerTrackIndex = muxer.addTrack(format)
                break
            }
        }
        if (trackIndex == -1 || muxer == null) return@withContext false
        muxer.start()
        extractor.seekTo(startTimeMs * 1000, android.media.MediaExtractor.SEEK_TO_CLOSEST_SYNC)
        val buffer = java.nio.ByteBuffer.allocate(1024 * 1024)
        val bufferInfo = android.media.MediaCodec.BufferInfo()
        var firstSampleTimeUs: Long = -1L
        while (true) {
            bufferInfo.size = extractor.readSampleData(buffer, 0)
            if (bufferInfo.size < 0) break
            val sampleTime = extractor.sampleTime
            if (sampleTime > endTimeMs * 1000) break
            
            if (firstSampleTimeUs == -1L) {
                firstSampleTimeUs = sampleTime
            }
            
            bufferInfo.presentationTimeUs = sampleTime - firstSampleTimeUs
            if (bufferInfo.presentationTimeUs < 0) bufferInfo.presentationTimeUs = 0
            
            bufferInfo.offset = 0
            bufferInfo.flags = extractor.sampleFlags
            muxer.writeSampleData(muxerTrackIndex, buffer, bufferInfo)
            extractor.advance()
        }
        return@withContext true
    } catch (e: Exception) {
        e.printStackTrace()
        return@withContext false
    } finally {
        try { extractor.release() } catch (e: Exception) {}
        try { muxer?.stop(); muxer?.release() } catch (e: Exception) {}
    }
}

suspend fun extractAudioFromVideo(context: Context, videoUri: Uri, outputFile: File): Boolean = withContext(Dispatchers.IO) {
    val extractor = android.media.MediaExtractor()
    var muxer: android.media.MediaMuxer? = null
    var audioTrackIndex = -1
    var muxerAudioTrackIndex = -1
    try {
        extractor.setDataSource(context, videoUri, null)
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(android.media.MediaFormat.KEY_MIME)
            if (mime?.startsWith("audio/") == true) {
                if (mime != "audio/mp4a-latm" && mime != "audio/3gpp" && mime != "audio/amr-wb") {
                    return@withContext false
                }
                extractor.selectTrack(i)
                audioTrackIndex = i
                muxer = android.media.MediaMuxer(outputFile.absolutePath, android.media.MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                muxerAudioTrackIndex = muxer.addTrack(format)
                break
            }
        }
        if (audioTrackIndex == -1 || muxer == null) return@withContext false
        muxer.start()
        val buffer = java.nio.ByteBuffer.allocate(1024 * 1024)
        val bufferInfo = android.media.MediaCodec.BufferInfo()
        while (true) {
            bufferInfo.size = extractor.readSampleData(buffer, 0)
            if (bufferInfo.size < 0) break
            bufferInfo.presentationTimeUs = extractor.sampleTime
            bufferInfo.offset = 0
            bufferInfo.flags = extractor.sampleFlags
            muxer.writeSampleData(muxerAudioTrackIndex, buffer, bufferInfo)
            extractor.advance()
        }
        return@withContext true
    } catch (e: Exception) {
        e.printStackTrace()
        return@withContext false
    } finally {
        try { extractor.release() } catch (e: Exception) {}
        try { muxer?.stop(); muxer?.release() } catch (e: Exception) {}
    }
}

fun setRingtoneFromUri(context: Context, sourceUri: Uri, isExtracted: Boolean = false, setAsNotification: Boolean = true, setAsRingtone: Boolean = false) {
    if (!Settings.System.canWrite(context)) {
        Toast.makeText(context, "Write Settings permission is missing.", Toast.LENGTH_SHORT).show()
        return
    }

    try {
        var displayName = "custom_notification_sound_${System.currentTimeMillis()}.mp3"
        if (!isExtracted) {
            context.contentResolver.query(sourceUri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        displayName = cursor.getString(nameIndex)
                    }
                }
            }
        } else {
            displayName = "extracted_audio_${System.currentTimeMillis()}.m4a"
        }

        // Clean up display name to avoid illegal chars
        displayName = displayName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val title = displayName.substringBeforeLast(".")
        val mimeType = if (isExtracted) "audio/mp4" else (context.contentResolver.getType(sourceUri) ?: "audio/mpeg")

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.Audio.Media.TITLE, title)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.Audio.Media.IS_NOTIFICATION, 1)
            put(MediaStore.Audio.Media.IS_RINGTONE, 1)
            put(MediaStore.Audio.Media.IS_ALARM, 1)
            put(MediaStore.Audio.Media.IS_MUSIC, 0)
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_NOTIFICATIONS)
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            } else {
                // Legacy path for older versions
                val directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_NOTIFICATIONS)
                if (!directory.exists()) {
                    directory.mkdirs()
                }
                val targetFile = File(directory, displayName)
                put(MediaStore.MediaColumns.DATA, targetFile.absolutePath)
            }
        }

        val uri = context.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, contentValues)
        if (uri != null) {
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val updateValues = ContentValues().apply {
                    put(MediaStore.Audio.Media.IS_PENDING, 0)
                }
                context.contentResolver.update(uri, updateValues, null, null)
            } else {
                // Scan the file for older versions
                val directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_NOTIFICATIONS)
                val targetFile = File(directory, displayName)
                android.media.MediaScannerConnection.scanFile(
                    context,
                    arrayOf(targetFile.absolutePath),
                    arrayOf(mimeType)
                ) { path, scannedUri ->
                    // Done scanning
                }
            }

            // Set as default notification sound or ringtone
            if (setAsNotification) {
                RingtoneManager.setActualDefaultRingtoneUri(context, RingtoneManager.TYPE_NOTIFICATION, uri)
            }
            if (setAsRingtone) {
                try {
                    RingtoneManager.setActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE, uri)
                } catch (e: Exception) {
                    // ignore if restricted
                }
            }

            val msg = if (setAsNotification && setAsRingtone) "Notification & Ringtone sound updated successfully!" 
                      else if (setAsNotification) "Notification sound updated successfully!"
                      else "Ringtone updated successfully!"
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(context, "Failed to save audio file to system database.", Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "Error setting sound: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

fun getCurrentNotificationSoundName(context: Context): String {
    return try {
        val uri = RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_NOTIFICATION)
        if (uri != null) {
            val ringtone = RingtoneManager.getRingtone(context, uri)
            ringtone?.getTitle(context) ?: "Custom Sound"
        } else {
            "None / Silent"
        }
    } catch (e: Exception) {
        "Default / Unknown"
    }
}

fun deleteCustomRingtonesFromSystem(context: Context) {
    try {
        val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DISPLAY_NAME)
        val selection = "${MediaStore.Audio.Media.DISPLAY_NAME} LIKE ? OR ${MediaStore.Audio.Media.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("%custom_notification_sound_%", "%extracted_audio_%")
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val uri = android.content.ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                try {
                    context.contentResolver.delete(uri, null, null)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun getSystemDefaultNotificationUri(context: Context): Uri {
    try {
        val manager = RingtoneManager(context).apply {
            setType(RingtoneManager.TYPE_NOTIFICATION)
        }
        val cursor = manager.cursor
        if (cursor.moveToFirst()) {
            do {
                val id = cursor.getLong(RingtoneManager.ID_COLUMN_INDEX)
                val uriStr = cursor.getString(RingtoneManager.URI_COLUMN_INDEX)
                val title = cursor.getString(RingtoneManager.TITLE_COLUMN_INDEX) ?: ""
                val fullUri = Uri.parse("$uriStr/$id")
                
                if (uriStr.contains("internal") && 
                    !title.contains("extracted_audio", ignoreCase = true) && 
                    !title.contains("custom_notification_sound", ignoreCase = true) && 
                    !title.contains("sound_clip", ignoreCase = true)) {
                    return fullUri
                }
            } while (cursor.moveToNext())
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION) ?: Settings.System.DEFAULT_NOTIFICATION_URI
}

fun getSystemDefaultRingtoneUri(context: Context): Uri {
    try {
        val manager = RingtoneManager(context).apply {
            setType(RingtoneManager.TYPE_RINGTONE)
        }
        val cursor = manager.cursor
        if (cursor.moveToFirst()) {
            do {
                val id = cursor.getLong(RingtoneManager.ID_COLUMN_INDEX)
                val uriStr = cursor.getString(RingtoneManager.URI_COLUMN_INDEX)
                val title = cursor.getString(RingtoneManager.TITLE_COLUMN_INDEX) ?: ""
                val fullUri = Uri.parse("$uriStr/$id")
                
                if (uriStr.contains("internal") && 
                    !title.contains("extracted_audio", ignoreCase = true) && 
                    !title.contains("custom_notification_sound", ignoreCase = true) && 
                    !title.contains("sound_clip", ignoreCase = true)) {
                    return fullUri
                }
            } while (cursor.moveToNext())
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE) ?: Settings.System.DEFAULT_RINGTONE_URI
}

@Composable
fun DeveloperVersionScreen(
    onBackToCustomizer: () -> Unit,
    onMenuClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF3F2EE))
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onMenuClick) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Open Drawer",
                    tint = Color(0xFF2C2C2A)
                )
            }
            TextButton(onClick = onBackToCustomizer) {
                Text(
                    text = "Back to Editor",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Box(
            modifier = Modifier
                .size(120.dp)
                .background(Color.White, RoundedCornerShape(28.dp))
                .padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.app_logo_icon_1783527284606),
                contentDescription = "Ravi Notifier Logo",
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(24.dp)),
                contentScale = ContentScale.Fit
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = "DEVELOPER VERSION",
            style = MaterialTheme.typography.labelLarge.copy(
                letterSpacing = 3.sp,
                fontWeight = FontWeight.Bold
            ),
            color = Color(0xFF2C2C2A).copy(alpha = 0.6f)
        )

        Text(
            text = "Ravi's Notifier",
            style = MaterialTheme.typography.displaySmall.copy(
                fontWeight = FontWeight.Bold
            ),
            color = Color(0xFF2C2C2A),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Crafted with passion for custom soundscapes. This premium developer edition offers ultimate control over notification normalizations and trimming.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF2C2C2A).copy(alpha = 0.8f),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Developer",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF2C2C2A).copy(alpha = 0.5f)
                        )
                        Text(
                            text = "Ravi",
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFF2C2C2A)
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Version",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF2C2C2A).copy(alpha = 0.5f)
                        )
                        Text(
                            text = "1.0.0",
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFF2C2C2A)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Key Features Enabled",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = Color(0xFF2C2C2A),
            modifier = Modifier.align(Alignment.Start)
        )

        Spacer(modifier = Modifier.height(10.dp))

        val features = listOf(
            "Volume Levels Auto-Normalizer",
            "Audio/Video Sweet Spot Trim Slicer",
            "Charcoal Hand-Drawn Custom Brand Asset",
            "Coming Soon Custom Typography System",
            "Ringtone & Notification Database Updater"
        )

        features.forEach { feature ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Enabled",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = feature,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF2C2C2A).copy(alpha = 0.9f)
                )
            }
        }
    }
}
