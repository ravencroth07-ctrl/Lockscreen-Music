package app.lockmusic

import android.Manifest
import android.app.NotificationManager
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class SetupActivity : ComponentActivity() {
    private val pickImage = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            lifecycleScope.launch { Prefs.setIdle(this@SetupActivity, uri.toString()) }
        }
    }
    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(Modifier.fillMaxSize()) {
                    SetupScreen(
                        onPickIdle = { pickImage.launch(arrayOf("image/*")) },
                        onAskNotifications = { if (Build.VERSION.SDK_INT >= 33) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SetupScreen(onPickIdle: () -> Unit, onAskNotifications: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }

    // `tick` makes these re-evaluate every time the user comes back from a settings screen
    val listenerOn = tick >= 0 && NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)
    val notifOn = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    val fsiOn = Build.VERSION.SDK_INT < 34 ||
        ctx.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
    val batteryOn = ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)
    val wallpaperOn = isWallpaperSet(ctx)

    val dimSaved by remember { Prefs.dim(ctx) }.collectAsStateWithLifecycle(initialValue = 0.25f)
    var dimLocal by remember { mutableStateOf<Float?>(null) }
    val playerOn by remember { Prefs.player(ctx) }.collectAsStateWithLifecycle(initialValue = true)
    val idle by remember { Prefs.idleUri(ctx) }.collectAsStateWithLifecycle(initialValue = null)

    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("LockMusic setup", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Each step shows a tick when it is done. Come back here after each settings screen.")

        Step("1. Notification access", "Lets the app see what is playing and control it.", listenerOn, "Open settings") {
            ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        Step("2. Show notifications", "Needed to show the lock-screen player.", notifOn, "Allow") { onAskNotifications() }
        Step("3. Full-screen notifications", "Lets the player open over the lock screen (Android 14 and later).", fsiOn, "Open settings") {
            runCatching {
                ctx.startActivity(
                    Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${ctx.packageName}"))
                )
            }
        }
        Step("4. Set the wallpaper", "Choose Lock screen or Both on the next screen.", wallpaperOn, "Choose") {
            val i = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
                .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, ComponentName(ctx, MusicWallpaperService::class.java))
            ctx.startActivity(i)
        }
        Step("5. Battery: no restrictions", "Realme UI stops background apps. Set this app to unrestricted.", batteryOn, "Open settings") {
            ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
        Step(
            "6. Auto-launch and recents lock",
            "Open App info, enable 'Allow auto launch' (if shown), and lock this app in the recent apps list. This cannot be detected, so it has no tick.",
            null, "App info"
        ) {
            ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")))
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Options", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Show the player on the lock screen")
                    Switch(checked = playerOn, onCheckedChange = { scope.launch { Prefs.setPlayer(ctx, it) } })
                }
                Text("Dim the artwork (keeps the clock readable)")
                Slider(
                    value = dimLocal ?: dimSaved,
                    onValueChange = { dimLocal = it; scope.launch { Prefs.setDim(ctx, it) } },
                    onValueChangeFinished = { dimLocal = null },
                )
                Text(if (idle == null) "No idle photo set" else "Idle photo set (shown when nothing is playing)")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onPickIdle) { Text("Choose photo") }
                    if (idle != null) OutlinedButton(onClick = { scope.launch { Prefs.setIdle(ctx, null) } }) { Text("Remove") }
                }
            }
        }
    }
}

@Composable
private fun Step(title: String, detail: String, done: Boolean?, action: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(title, fontWeight = FontWeight.SemiBold)
                if (done != null) Text(if (done) "Done" else "To do")
            }
            Text(detail, style = MaterialTheme.typography.bodyMedium)
            if (done != true) Button(onClick = onClick) { Text(action) }
        }
    }
}

private fun isWallpaperSet(ctx: Context): Boolean {
    val wm = WallpaperManager.getInstance(ctx)
    val info = if (Build.VERSION.SDK_INT >= 34) wm.getWallpaperInfo(WallpaperManager.FLAG_LOCK) else wm.wallpaperInfo
    return info?.packageName == ctx.packageName
}
