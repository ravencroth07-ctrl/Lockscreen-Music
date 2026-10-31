package app.lockmusic

import android.graphics.Bitmap
import android.media.session.MediaController
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class Track(val title: String, val artist: String, val album: String, val durationMs: Long) {
    val key: String get() = "$artist|$title"
}

data class Now(
    val track: Track? = null,
    val artwork: Bitmap? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val positionAt: Long = 0L, // SystemClock.elapsedRealtime() when positionMs was valid
    val speed: Float = 1f,
    val dismissed: Boolean = false, // the user swiped the player away; audio keeps playing
)

/** One source of truth shared by the wallpaper, the listener service and the lock-screen player. */
object PlaybackRepository {
    private val _state = MutableStateFlow(Now())
    val state: StateFlow<Now> = _state.asStateFlow()

    @Volatile
    var controller: MediaController? = null

    fun update(f: (Now) -> Now) = _state.update(f)

    fun positionNow(n: Now = _state.value): Long {
        if (!n.isPlaying) return n.positionMs
        val elapsed = SystemClock.elapsedRealtime() - n.positionAt
        val max = n.track?.durationMs?.takeIf { it > 0 } ?: Long.MAX_VALUE
        return (n.positionMs + (elapsed * n.speed).toLong()).coerceIn(0L, max)
    }

    fun toggle() {
        val c = controller ?: return
        if (_state.value.isPlaying) c.transportControls.pause() else c.transportControls.play()
    }

    fun next() { controller?.transportControls?.skipToNext() }
    fun previous() { controller?.transportControls?.skipToPrevious() }
    fun seekTo(ms: Long) { controller?.transportControls?.seekTo(ms) }
    fun dismiss() = update { it.copy(dismissed = true) }
}
