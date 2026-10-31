package app.lockmusic

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Android blocks background activity starts, so the lock-screen player is launched through a
 * full-screen-intent notification. The user must allow full-screen notifications for the app.
 */
object PlayerLauncher {
    private const val CHANNEL = "player"
    private const val ID = 42

    fun onScreenOn(c: Context) {
        val n = PlaybackRepository.state.value
        val track = n.track ?: return
        if (!Prefs.playerEnabled || !n.isPlaying || n.dismissed) return
        val km = c.getSystemService(KeyguardManager::class.java)
        if (!km.isKeyguardLocked) return
        val nm = c.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 34 && !nm.canUseFullScreenIntent()) return

        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Lock screen player", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null)
                setShowBadge(false)
            }
        )
        val intent = Intent(c, LockPlayerActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pi = PendingIntent.getActivity(
            c, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = Notification.Builder(c, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(track.title)
            .setContentText(track.artist)
            .setFullScreenIntent(pi, true)
            .setAutoCancel(true)
            .setTimeoutAfter(5000)
            .build()
        nm.notify(ID, notification)
    }

    fun cancel(c: Context) {
        c.getSystemService(NotificationManager::class.java).cancel(ID)
    }
}
