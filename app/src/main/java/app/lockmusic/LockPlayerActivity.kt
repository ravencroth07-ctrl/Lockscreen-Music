package app.lockmusic

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.FlashlightOff
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.VolumeMute
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

// Design tokens (see DESIGN.md)
private val TextPrimary = Color.White
private val TextSecondary = Color.White.copy(alpha = 0.88f)
private val PanelRadius = 32.dp
private const val PanelScrim = 0.50f
private const val PanelTint = 0.08f

class LockPlayerActivity : ComponentActivity() {
    private val unlocked = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) { finish() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        enableEdgeToEdge()
        ContextCompat.registerReceiver(
            this, unlocked, IntentFilter(Intent.ACTION_USER_PRESENT), ContextCompat.RECEIVER_NOT_EXPORTED
        )
        setContent { LockPlayerScreen(onUnlock = ::unlock, onClose = ::finish) }
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(unlocked) }
        super.onDestroy()
    }

    private fun unlock() {
        getSystemService(KeyguardManager::class.java).requestDismissKeyguard(
            this,
            object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() { finish() }
            }
        )
    }
}

@Composable
fun LockPlayerScreen(onUnlock: () -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val now by PlaybackRepository.state.collectAsStateWithLifecycle()
    val dim by remember { Prefs.dim(ctx) }.collectAsStateWithLifecycle(initialValue = 0.25f)
    val favs by remember { Prefs.favorites(ctx) }.collectAsStateWithLifecycle(initialValue = emptySet())

    LaunchedEffect(now.dismissed, now.track == null) {
        if (now.dismissed || now.track == null) onClose()
    }

    var clock by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) { while (true) { clock = LocalDateTime.now(); delay(10_000) } }

    var pos by remember { mutableLongStateOf(0L) }
    LaunchedEffect(now) { while (true) { pos = PlaybackRepository.positionNow(now); delay(500) } }

    val inf = rememberInfiniteTransition(label = "kenburns")
    val zoom by inf.animateFloat(
        initialValue = 1.06f, targetValue = 1.14f,
        animationSpec = infiniteRepeatable(tween(16_000, easing = LinearEasing), RepeatMode.Reverse),
        label = "zoom"
    )

    var torch by remember { mutableStateOf(false) }
    var upDrag by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val unlockDistance = with(density) { 160.dp.toPx() }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0B0F))
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = { if (upDrag > unlockDistance) onUnlock(); upDrag = 0f },
                    onDragCancel = { upDrag = 0f },
                ) { _, dy -> upDrag -= dy }
            }
    ) {
        val rootW = maxWidth
        val rootH = maxHeight
        val art = now.artwork

        if (art != null) {
            Image(
                bitmap = art.asImageBitmap(), contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = zoom; scaleY = zoom }
            )
        }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = dim)))
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)),
                    startY = with(density) { (rootH * 0.45f).toPx() }
                )
            )
        )

        // Compact clock under the top edge
        Row(
            Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 28.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(clock.format(DateTimeFormatter.ofPattern("H:mm")), color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text(clock.format(DateTimeFormatter.ofPattern("EEE d MMM")), color = TextSecondary, fontSize = 17.sp)
        }

        Column(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            now.track?.let { t ->
                PlayerPanel(
                    now = now, track = t, pos = pos, art = art, rootW = rootW, rootH = rootH,
                    fav = t.key in favs,
                    onFav = { scope.launch { Prefs.toggleFavorite(ctx, t.key) } },
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                RoundButton(if (torch) Icons.Rounded.FlashlightOff else Icons.Rounded.FlashlightOn, "Flashlight") {
                    torch = !torch
                    setTorch(ctx, torch)
                }
                RoundButton(Icons.Rounded.CameraAlt, "Camera") { openCamera(ctx) }
            }
        }
    }
}

@Composable
private fun RoundButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(52.dp).clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.45f))
            .border(1.dp, Color.White.copy(alpha = 0.22f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, contentDescription = label, tint = TextPrimary) }
}

/** Swipe left to reveal Clear. Clearing hides the player; the music keeps playing. */
@Composable
private fun PlayerPanel(
    now: Now, track: Track, pos: Long, art: Bitmap?, rootW: Dp, rootH: Dp,
    fav: Boolean, onFav: () -> Unit,
) {
    val density = LocalDensity.current
    val clearW = with(density) { 96.dp.toPx() }
    var dx by remember { mutableFloatStateOf(0f) }

    Box(
        Modifier.fillMaxWidth().pointerInput(Unit) {
            detectHorizontalDragGestures(
                onDragEnd = { dx = if (dx < -clearW / 2f) -clearW else 0f },
                onDragCancel = { dx = 0f },
            ) { change, d -> change.consume(); dx = (dx + d).coerceIn(-clearW, 0f) }
        }
    ) {
        if (dx < 0f) {
            Box(
                Modifier.align(Alignment.CenterEnd).size(width = 88.dp, height = 52.dp)
                    .clip(RoundedCornerShape(26.dp)).background(Color.Black.copy(alpha = 0.6f))
                    .clickable { PlaybackRepository.dismiss() },
                contentAlignment = Alignment.Center
            ) { Text("Clear", color = TextPrimary, fontWeight = FontWeight.SemiBold) }
        }
        GlassPanel(art, rootW, rootH, Modifier.offset { IntOffset(dx.roundToInt(), 0) }.fillMaxWidth()) {
            Text(track.title, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track.artist, color = TextSecondary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Box(Modifier.height(8.dp))
            val frac = if (track.durationMs > 0) (pos.toFloat() / track.durationMs).coerceIn(0f, 1f) else 0f
            ProgressBar(frac) { PlaybackRepository.seekTo((it * track.durationMs).toLong()) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(fmt(pos), color = TextSecondary, fontSize = 12.sp)
                Text("-" + fmt((track.durationMs - pos).coerceAtLeast(0)), color = TextSecondary, fontSize = 12.sp)
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconTap(if (fav) Icons.Rounded.Star else Icons.Rounded.StarBorder, "Favourite", 26.dp, onFav)
                IconTap(Icons.Rounded.FastRewind, "Previous", 36.dp) { PlaybackRepository.previous() }
                IconTap(if (now.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (now.isPlaying) "Pause" else "Play", 48.dp) { PlaybackRepository.toggle() }
                IconTap(Icons.Rounded.FastForward, "Next", 36.dp) { PlaybackRepository.next() }
                Box(Modifier.size(26.dp)) // balances the star on the left
            }
            VolumeRow()
        }
    }
}

@Composable
private fun IconTap(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, size: Dp, onClick: () -> Unit) {
    Box(Modifier.size(52.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = label, tint = TextPrimary, modifier = Modifier.size(size))
    }
}

/**
 * Frosted panel. Compose cannot blur what is behind a view, so the panel draws its own
 * blurred copy of the artwork, offset so it lines up with the full-screen picture.
 */
@Composable
private fun GlassPanel(
    art: Bitmap?, rootW: Dp, rootH: Dp, modifier: Modifier, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    var pos by remember { mutableStateOf(IntOffset.Zero) }
    val shape = RoundedCornerShape(PanelRadius)
    Box(
        modifier.clip(shape).border(1.dp, Color.White.copy(alpha = 0.22f), shape)
            .onGloballyPositioned {
                val p = it.positionInRoot()
                pos = IntOffset(p.x.roundToInt(), p.y.roundToInt())
            }
    ) {
        Box(Modifier.matchParentSize()) {
            if (art != null) {
                Image(
                    bitmap = art.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .wrapContentSize(Alignment.TopStart, unbounded = true)
                        .size(rootW, rootH)
                        .offset { IntOffset(-pos.x, -pos.y) }
                        .blur(40.dp)
                )
            }
            Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = PanelScrim)))
            Box(Modifier.matchParentSize().background(Color.White.copy(alpha = PanelTint)))
        }
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), content = content)
    }
}

@Composable
private fun ProgressBar(frac: Float, onSeek: (Float) -> Unit) {
    var drag by remember { mutableStateOf<Float?>(null) }
    val shown = drag ?: frac
    Box(
        Modifier.fillMaxWidth().height(22.dp)
            .pointerInput(Unit) { detectTapGestures { onSeek((it.x / size.width).coerceIn(0f, 1f)) } }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { drag = (it.x / size.width).coerceIn(0f, 1f) },
                    onDragEnd = { drag?.let(onSeek); drag = null },
                    onDragCancel = { drag = null },
                ) { change, _ -> change.consume(); drag = (change.position.x / size.width).coerceIn(0f, 1f) }
            },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(Modifier.fillMaxWidth().height(5.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.35f)))
        Box(Modifier.fillMaxWidth(shown).height(5.dp).clip(CircleShape).background(Color.White))
    }
}

@Composable
private fun VolumeRow() {
    val ctx = LocalContext.current
    val am = remember { ctx.getSystemService(AudioManager::class.java) }
    val max = remember { am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var vol by remember { mutableFloatStateOf(am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Rounded.VolumeMute, null, tint = TextSecondary, modifier = Modifier.size(18.dp))
        Slider(
            value = vol,
            onValueChange = { vol = it; am.setStreamVolume(AudioManager.STREAM_MUSIC, (it * max).roundToInt(), 0) },
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = Color.White, activeTrackColor = Color.White,
                inactiveTrackColor = Color.White.copy(alpha = 0.35f)
            )
        )
        Icon(Icons.Rounded.VolumeUp, null, tint = TextSecondary, modifier = Modifier.size(18.dp))
    }
}

private fun fmt(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

private fun setTorch(ctx: Context, on: Boolean) {
    runCatching {
        val cm = ctx.getSystemService(CameraManager::class.java)
        val id = cm.cameraIdList.first {
            cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
        cm.setTorchMode(id, on)
    }
}

private fun openCamera(ctx: Context) {
    runCatching {
        ctx.startActivity(
            Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
