package app.lockmusic

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Shader
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Draws the album artwork full bleed with a slow zoom and drift. When nothing is playing it
 * shows the user's idle photo (if set). Pure Canvas, no video, so nothing depends on codecs.
 */
class MusicWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = ArtEngine()

    private inner class ArtEngine : Engine() {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        private val handler = Handler(Looper.getMainLooper())
        private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        private val matrix = Matrix()
        private var art: Bitmap? = null
        private var idle: Bitmap? = null
        private var playing = false
        private var dim = 0.25f
        private var visible = false
        private var kind = -1
        private val start = System.nanoTime()

        private val frame = object : Runnable {
            override fun run() {
                draw()
                if (visible && showingArt()) handler.postDelayed(this, 33)
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            val ctx = this@MusicWallpaperService
            scope.launch {
                PlaybackRepository.state.collect { n ->
                    art = n.artwork
                    playing = n.isPlaying
                    restart()
                }
            }
            scope.launch { Prefs.dim(ctx).collect { dim = it; draw() } }
            scope.launch {
                Prefs.idleUri(ctx).collect { uri ->
                    idle = uri?.let { loadIdle(it) }
                    restart()
                }
            }
        }

        private fun showingArt() = (playing && art != null) || (!playing && idle == null && art != null)

        private fun currentBitmap(): Bitmap? =
            if (playing) art ?: idle else idle ?: art

        private fun restart() {
            handler.removeCallbacks(frame)
            frame.run()
        }

        override fun onVisibilityChanged(v: Boolean) {
            visible = v
            handler.removeCallbacks(frame)
            if (v) frame.run()
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            draw()
        }

        override fun onDestroy() {
            handler.removeCallbacks(frame)
            scope.cancel()
            super.onDestroy()
        }

        /**
         * Realme UI / ColorOS can refuse to lock a surface again after its content type changes
         * (error "Invalid argument -22"). Switching the pixel format forces a clean new surface.
         * Needs checking on the device.
         */
        private fun nudgeSurface(newKind: Int) {
            if (kind != -1 && kind != newKind) {
                val fmt = if (newKind % 2 == 0) PixelFormat.RGBX_8888 else PixelFormat.RGBA_8888
                runCatching { surfaceHolder.setFormat(fmt) }
            }
            kind = newKind
        }

        private fun draw() {
            val bmp = currentBitmap()
            nudgeSurface(if (bmp == null) 0 else if (bmp === art) 1 else 2)
            var c: Canvas? = null
            try {
                c = surfaceHolder.lockCanvas() ?: return
                if (bmp == null) drawFallback(c) else drawArt(c, bmp, animate = bmp === art)
            } catch (_: Exception) {
                // surface not ready or locked; the next frame or surface change retries
            } finally {
                runCatching { c?.let { surfaceHolder.unlockCanvasAndPost(it) } }
            }
        }

        private fun drawArt(c: Canvas, bmp: Bitmap, animate: Boolean) {
            val w = c.width.toFloat()
            val h = c.height.toFloat()
            val t = (System.nanoTime() - start) / 1e9f
            val zoom = if (animate) 1.06f + 0.05f * sin(t * 0.35f) else 1f
            val scale = max(w / bmp.width, h / bmp.height) * zoom
            val dx = (w - bmp.width * scale) / 2f + if (animate) sin(t * 0.21f) * w * 0.015f else 0f
            val dy = (h - bmp.height * scale) / 2f + if (animate) cos(t * 0.17f) * h * 0.01f else 0f
            matrix.reset()
            matrix.postScale(scale, scale)
            matrix.postTranslate(dx, dy)
            c.drawColor(Color.BLACK)
            c.drawBitmap(bmp, matrix, paint)
            c.drawColor(Color.argb((dim * 255).toInt().coerceIn(0, 255), 0, 0, 0))
        }

        private fun drawFallback(c: Canvas) {
            val p = Paint().apply {
                shader = LinearGradient(
                    0f, 0f, 0f, c.height.toFloat(),
                    Color.parseColor("#1B1B24"), Color.parseColor("#0B0B0F"), Shader.TileMode.CLAMP
                )
            }
            c.drawRect(0f, 0f, c.width.toFloat(), c.height.toFloat(), p)
        }

        private suspend fun loadIdle(uri: String): Bitmap? = withContext(Dispatchers.IO) {
            runCatching {
                val source = ImageDecoder.createSource(contentResolver, Uri.parse(uri))
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    // software bitmap: a lockCanvas() canvas cannot draw hardware bitmaps
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    val longest = max(info.size.width, info.size.height)
                    decoder.setTargetSampleSize(max(1, longest / 2200))
                }
            }.getOrNull()
        }
    }
}
