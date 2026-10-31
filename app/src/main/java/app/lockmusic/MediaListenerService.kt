package app.lockmusic

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Needs notification access. Reads whatever music app is playing, keeps PlaybackRepository
 * up to date, and shows the lock-screen player when the screen turns on while music plays.
 */
class MediaListenerService : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var msm: MediaSessionManager? = null
    private var current: MediaController? = null
    private var artJob: Job? = null
    private var settingsJob: Job? = null
    private val component by lazy { ComponentName(this, MediaListenerService::class.java) }

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { pick(it) }

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = readMetadata(metadata)
        override fun onPlaybackStateChanged(state: PlaybackState?) = readState(state)
        override fun onSessionDestroyed() { pick(msm?.getActiveSessions(component)) }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            when (i.action) {
                Intent.ACTION_SCREEN_ON -> PlayerLauncher.onScreenOn(c)
                Intent.ACTION_USER_PRESENT -> PlayerLauncher.cancel(c)
            }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        msm = getSystemService(MediaSessionManager::class.java)
        msm?.addOnActiveSessionsChangedListener(sessionsListener, component)
        pick(msm?.getActiveSessions(component))
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        runCatching { unregisterReceiver(screenReceiver) }
        ContextCompat.registerReceiver(this, screenReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        settingsJob?.cancel()
        settingsJob = scope.launch { Prefs.player(applicationContext).collect { Prefs.playerEnabled = it } }
    }

    override fun onListenerDisconnected() {
        teardown()
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        teardown()
        scope.cancel()
        super.onDestroy()
    }

    private fun teardown() {
        runCatching { msm?.removeOnActiveSessionsChangedListener(sessionsListener) }
        runCatching { current?.unregisterCallback(callback) }
        current = null
        PlaybackRepository.controller = null
        runCatching { unregisterReceiver(screenReceiver) }
    }

    private fun pick(list: List<MediaController>?) {
        val best = list?.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: list?.firstOrNull()
        if (best?.sessionToken == current?.sessionToken) return
        runCatching { current?.unregisterCallback(callback) }
        current = best
        PlaybackRepository.controller = best
        if (best == null) {
            PlaybackRepository.update { Now() }
            return
        }
        best.registerCallback(callback)
        readMetadata(best.metadata)
        readState(best.playbackState)
    }

    private fun readMetadata(m: MediaMetadata?) {
        if (m == null) return
        val title = m.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: m.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: return
        val artist = m.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: m.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: m.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE) ?: ""
        val album = m.getString(MediaMetadata.METADATA_KEY_ALBUM) ?: ""
        val track = Track(title, artist, album, m.getLong(MediaMetadata.METADATA_KEY_DURATION))
        val art = m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: m.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: m.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        val changed = PlaybackRepository.state.value.track?.key != track.key
        PlaybackRepository.update {
            it.copy(
                track = track,
                artwork = art ?: if (changed) null else it.artwork,
                dismissed = if (changed) false else it.dismissed, // a new track brings the player back
            )
        }
        artJob?.cancel()
        if (art == null || art.width < 300) {
            artJob = scope.launch {
                ArtworkLookup.fetch(track)?.let { found ->
                    PlaybackRepository.update { s -> if (s.track?.key == track.key) s.copy(artwork = found) else s }
                }
            }
        }
    }

    private fun readState(s: PlaybackState?) {
        if (s == null) return
        val playing = s.state == PlaybackState.STATE_PLAYING
        val at = s.lastPositionUpdateTime.takeIf { it > 0 } ?: SystemClock.elapsedRealtime()
        PlaybackRepository.update {
            it.copy(
                isPlaying = playing,
                positionMs = s.position,
                positionAt = at,
                speed = s.playbackSpeed,
                dismissed = if (playing && !it.isPlaying) false else it.dismissed, // resuming brings it back
            )
        }
    }
}
