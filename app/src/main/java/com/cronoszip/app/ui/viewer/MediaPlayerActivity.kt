package com.cronoszip.app.ui.viewer

import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.cronoszip.app.ui.background.LiquidBackground
import com.cronoszip.app.ui.theme.CronosBlue
import com.cronoszip.app.ui.theme.CronosCyan
import com.cronoszip.app.ui.theme.CronosZipTheme
import com.cronoszip.app.ui.theme.TextPrimary
import com.cronoszip.app.ui.theme.TextSecondary
import java.io.File
import java.util.Locale
import kotlinx.coroutines.delay

/** Which files the built-in player understands. Both video and music use one player. */
object MediaKinds {
    private val videoExt = setOf("mp4", "mkv", "webm", "mov", "m4v", "3gp", "ts")
    private val audioExt = setOf("mp3", "m4a", "aac", "ogg", "opus", "wav", "flac")

    fun isVideo(name: String): Boolean = extension(name) in videoExt
    fun isAudio(name: String): Boolean = extension(name) in audioExt
    fun isMedia(name: String): Boolean = isVideo(name) || isAudio(name)

    /** True when [other] is playable media of the same kind (video or music) as [name]. */
    fun isSameKind(other: String, name: String): Boolean =
        isMedia(other) && isVideo(other) == isVideo(name)

    private fun extension(name: String): String = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
}

private val speeds = listOf(0.5f, 1f, 1.25f, 1.5f, 2f)

/** Built-in player for video and music files in a folder. */
class MediaPlayerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        // Keeps the screen on while a video plays.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val path = intent.getStringExtra(EXTRA_PATH).orEmpty()
        val playlist: List<String> = intent.getStringArrayListExtra(EXTRA_PLAYLIST)
            ?.toList()
            ?.takeIf { it.isNotEmpty() }
            ?: listOf(path)
        val startIndex = playlist.indexOf(path).coerceAtLeast(0)

        setContent {
            CronosZipTheme {
                MediaRoot(playlist = playlist, startIndex = startIndex, onClose = { finish() })
            }
        }
    }

    companion object {
        const val EXTRA_PATH = "com.cronoszip.app.media.PATH"
        const val EXTRA_PLAYLIST = "com.cronoszip.app.media.PLAYLIST"
    }
}

@Composable
private fun MediaRoot(playlist: List<String>, startIndex: Int, onClose: () -> Unit) {
    val context = LocalContext.current
    val exo = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItems(
                playlist.map { MediaItem.fromUri(Uri.fromFile(File(it))) },
                startIndex,
                0L,
            )
            prepare()
            playWhenReady = true
        }
    }

    var index by remember { mutableIntStateOf(startIndex) }
    var isPlaying by remember { mutableStateOf(false) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var speedIndex by remember { mutableIntStateOf(1) }
    var controlsVisible by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    DisposableEffect(exo) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                index = exo.currentMediaItemIndex
                error = null
            }

            override fun onPlayerError(playerError: PlaybackException) {
                error = "This file cannot be played by the built-in player. Its format may not be supported."
            }
        }
        exo.addListener(listener)
        onDispose {
            exo.removeListener(listener)
            exo.release()
        }
    }

    // Keeps the progress bar and time labels moving.
    LaunchedEffect(exo) {
        while (true) {
            position = exo.currentPosition.coerceAtLeast(0L)
            duration = exo.duration.coerceAtLeast(0L)
            delay(250)
        }
    }

    // Controls fade after a few seconds of playback. A tap brings them back.
    LaunchedEffect(controlsVisible, isPlaying) {
        if (controlsVisible && isPlaying) {
            delay(3500)
            controlsVisible = false
        }
    }

    val name = File(playlist.getOrElse(index) { playlist.first() }).name
    val isVideo = MediaKinds.isVideo(name)

    Box(modifier = Modifier.fillMaxSize().background(if (isVideo) Color.Black else Color.Transparent)) {
        if (!isVideo) LiquidBackground(animate = false)

        if (isVideo) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { viewContext ->
                    PlayerView(viewContext).apply {
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        this.player = exo
                    }
                },
                update = { view -> view.player = exo },
            )
        } else {
            AudioCover(name = name, index = index, total = playlist.size)
        }

        error?.let {
            Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextPrimary,
                    textAlign = TextAlign.Center,
                )
            }
        }

        // Full-screen tap target that shows or hides the controls.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable { controlsVisible = !controlsVisible },
        )

        if (controlsVisible) {
            TopBar(
                title = if (isVideo) name else "Now playing",
                onClose = onClose,
                modifier = Modifier.align(Alignment.TopCenter),
            )
            BottomControls(
                exo = exo,
                isPlaying = isPlaying,
                position = position,
                duration = duration,
                speedLabel = "${speeds[speedIndex]}x",
                canPrevious = index > 0,
                canNext = index < playlist.lastIndex,
                onSpeed = {
                    speedIndex = (speedIndex + 1) % speeds.size
                    exo.setPlaybackSpeed(speeds[speedIndex])
                },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
private fun TopBar(title: String, onClose: () -> Unit, modifier: Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.45f))
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoundControl(
            icon = Icons.Rounded.Close,
            description = "Close player",
            size = 42.dp,
            onClick = onClose,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun BottomControls(
    exo: ExoPlayer,
    isPlaying: Boolean,
    position: Long,
    duration: Long,
    speedLabel: String,
    canPrevious: Boolean,
    canNext: Boolean,
    onSpeed: () -> Unit,
    modifier: Modifier,
) {
    val fraction = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.5f))
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Slider(
            value = fraction,
            onValueChange = { if (duration > 0) exo.seekTo((it * duration).toLong()) },
            colors = SliderDefaults.colors(
                thumbColor = CronosCyan,
                activeTrackColor = CronosBlue,
                inactiveTrackColor = Color.White.copy(alpha = 0.2f),
            ),
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = formatTime(position),
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary,
            )
            Text(
                text = formatTime(duration),
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary,
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.1f))
                    .clickable(onClick = onSpeed)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Text(
                    text = speedLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = TextPrimary,
                )
            }
            RoundControl(
                icon = Icons.Rounded.SkipPrevious,
                description = "Previous",
                size = 46.dp,
                enabled = canPrevious,
                onClick = { exo.seekToPrevious() },
            )
            RoundControl(
                icon = Icons.Rounded.Replay10,
                description = "Back 10 seconds",
                size = 46.dp,
                onClick = { exo.seekTo((exo.currentPosition - 10_000L).coerceAtLeast(0L)) },
            )
            RoundControl(
                icon = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                description = if (isPlaying) "Pause" else "Play",
                size = 62.dp,
                filled = true,
                onClick = { if (exo.isPlaying) exo.pause() else exo.play() },
            )
            RoundControl(
                icon = Icons.Rounded.Forward10,
                description = "Forward 10 seconds",
                size = 46.dp,
                onClick = { exo.seekTo(exo.currentPosition + 10_000L) },
            )
            RoundControl(
                icon = Icons.Rounded.SkipNext,
                description = "Next",
                size = 46.dp,
                enabled = canNext,
                onClick = { exo.seekToNext() },
            )
        }
    }
}

@Composable
private fun AudioCover(name: String, index: Int, total: Int) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(230.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(CronosBlue.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = CronosCyan,
                modifier = Modifier.size(104.dp),
            )
        }
        Spacer(Modifier.height(28.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.headlineSmall,
            color = TextPrimary,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Track ${index + 1} of $total",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
        )
    }
}

@Composable
private fun RoundControl(
    icon: ImageVector,
    description: String,
    size: Dp,
    enabled: Boolean = true,
    filled: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(
                when {
                    filled -> CronosBlue
                    enabled -> Color.White.copy(alpha = 0.1f)
                    else -> Color.White.copy(alpha = 0.04f)
                },
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (enabled) TextPrimary else TextSecondary.copy(alpha = 0.4f),
            modifier = Modifier.size(size * 0.5f),
        )
    }
}

private fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0L)
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val seconds = total % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}
