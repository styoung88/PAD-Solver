package com.example.phoneapp.capture

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.DisplayMetrics
import androidx.core.app.NotificationCompat
import com.example.phoneapp.R
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean

class ScreenCaptureService : Service() {
    @Volatile private var frameReady = false
    private val capturePending = AtomicBoolean(false)
    @Volatile private var captureSequence = 0
    private val mainHandler = Handler(android.os.Looper.getMainLooper())
    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    @Volatile private var imageReader: ImageReader? = null
    private var worker: HandlerThread? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CAPTURE_NOW -> captureCurrentFrame()
            ACTION_STOP -> stopSelf()
            ACTION_START -> startProjection(intent)
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startProjection(intent: Intent) {
        if (projection != null) return
        startAsForeground()
        val resultData = intent.parcelableExtra<Intent>(EXTRA_RESULT_DATA)
        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
        if (resultData == null || resultCode == 0) {
            stopSelf()
            return
        }

        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        val windowManager = getSystemService(WINDOW_SERVICE) as android.view.WindowManager
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi
        if (width <= 0 || height <= 0) {
            stopSelf()
            return
        }

        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = manager.getMediaProjection(resultCode, resultData)
        worker = HandlerThread("pad-screen-capture").also { it.start() }
        val handler = Handler(worker!!.looper)
        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2).also { reader ->
            reader.setOnImageAvailableListener(::onImageAvailable, handler)
        }
        projection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                stopSelf()
            }
        }, handler)
        virtualDisplay = projection?.createVirtualDisplay(
            "PAD board capture",
            width,
            height,
            density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            handler,
        )
        active = virtualDisplay != null
        intent.parcelableExtra<android.os.ResultReceiver>(EXTRA_READY_RECEIVER)?.send(if (active) 1 else 0, null)
    }

    private fun onImageAvailable(reader: ImageReader) {
        val image = runCatching { reader.acquireLatestImage() }.getOrNull() ?: return
        if (reader !== imageReader || !frameReady || !capturePending.compareAndSet(true, false)) {
            image.close()
            return
        }
        val frame = runCatching { image.use(::imageToBitmap) }.onFailure {
            android.util.Log.e("PadCapture", "Could not read captured pixels", it)
        }.getOrNull() ?: run {
            updateNotification("Could not read the screen. Retry from the overlay.")
            reportCaptureFailure()
            return
        }
        runCatching { saveCapture(frame) }.onFailure {
            android.util.Log.e("PadCapture", "Could not save capture", it)
            reportCaptureFailure()
        }
    }

    private fun reportCaptureFailure() {
        if (SolutionOverlayService.enabled) {
            startService(Intent(this, SolutionOverlayService::class.java).setAction(SolutionOverlayService.ACTION_CAPTURE_FAILED))
        }
    }

    private fun imageToBitmap(image: Image): Bitmap? {
        val plane = image.planes.firstOrNull() ?: return null
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val paddedWidth = rowStride / pixelStride
        val padded = Bitmap.createBitmap(paddedWidth, image.height, Bitmap.Config.ARGB_8888)
        padded.copyPixelsFromBuffer(plane.buffer)
        if (paddedWidth == image.width) return padded
        val cropped = Bitmap.createBitmap(padded, 0, 0, image.width, image.height)
        padded.recycle()
        return cropped
    }

    private fun captureCurrentFrame() {
        if (projection == null || !SolutionOverlayService.enabled || capturePending.get()) return
        val sequence = ++captureSequence
        if (SolutionOverlayService.enabled) {
            startService(Intent(this, SolutionOverlayService::class.java).setAction(SolutionOverlayService.ACTION_PROCESSING))
        }
        frameReady = false
        capturePending.set(true)
        virtualDisplay?.surface = null
        mainHandler.postDelayed({
            val captureWorker = worker ?: return@postDelayed
            Handler(captureWorker.looper).post {
                if (!active || sequence != captureSequence) return@post
                runCatching {
                    val previous = imageReader ?: return@post
                    val fresh = replaceCaptureReader(previous)
                    fresh.setOnImageAvailableListener(::onImageAvailable, Handler(captureWorker.looper))
                    imageReader = fresh
                    frameReady = true
                    virtualDisplay?.surface = fresh.surface
                }.onFailure {
                    capturePending.set(false)
                    android.util.Log.e("PadCapture", "Could not prepare fresh capture", it)
                    reportCaptureFailure()
                }
            }
        }, 350)
        mainHandler.postDelayed({
            if (sequence == captureSequence && capturePending.compareAndSet(true, false)) {
                reportCaptureFailure()
                android.util.Log.w("PadCapture", "Capture timed out waiting for a fresh frame")
                updateNotification("Capture timed out. Retry from the overlay.")
            }
        }, 5000)
        updateNotification("Capturing the current screen…")
    }

    private fun saveCapture(frame: Bitmap) {
        val captureFile = File(filesDir, CAPTURE_FILE_NAME)
        try {
            FileOutputStream(captureFile).use { output ->
                frame.compress(Bitmap.CompressFormat.PNG, 100, output)
            }
        } finally {
            frame.recycle()
        }

        if (SolutionOverlayService.enabled) {
            startService(Intent(this, SolutionOverlayService::class.java).apply {
                action = SolutionOverlayService.ACTION_SOLVE_CAPTURE
                putExtra(EXTRA_CAPTURE_PATH, captureFile.absolutePath)
            })
            updateNotification("Ready for the next board.")
            return
        }
    }

    private fun startAsForeground() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "PAD board capture",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Visible while the app is capturing a PAD board by request."
        }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        val notification = makeNotification("Use Capture in the PAD overlay, or take a phone screenshot.")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(message: String) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, makeNotification(message))
    }

    private fun makeNotification(message: String): Notification {
        val stopIntent = Intent(this, ScreenCaptureService::class.java).setAction(ACTION_STOP)
        val stopPendingIntent = PendingIntent.getService(
            this,
            REQUEST_STOP,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("PAD overlay screen sharing")
            .setContentText(message)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(R.drawable.ic_notification, "Stop sharing", stopPendingIntent)
            .build()
    }

    override fun onDestroy() {
        active = false
        mainHandler.removeCallbacksAndMessages(null)
        stopService(Intent(this, SolutionOverlayService::class.java))
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
        projection?.stop()
        projection = null
        worker?.quitSafely()
        worker = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private inline fun <reified T : android.os.Parcelable> Intent.parcelableExtra(key: String): T? =
        if (Build.VERSION.SDK_INT >= 33) getParcelableExtra(key, T::class.java) else {
            @Suppress("DEPRECATION")
            getParcelableExtra(key)
        }

    companion object {
        internal fun replaceCaptureReader(previous: ImageReader): ImageReader {
            val fresh = ImageReader.newInstance(previous.width, previous.height, PixelFormat.RGBA_8888, 2)
            previous.close()
            return fresh
        }
        @Volatile var active = false
            private set
        const val ACTION_START = "com.example.phoneapp.action.START_CAPTURE"
        const val ACTION_CAPTURE_NOW = "com.example.phoneapp.action.CAPTURE_NOW"
        const val ACTION_STOP = "com.example.phoneapp.action.STOP_CAPTURE"
        const val EXTRA_RESULT_CODE = "capture_result_code"
        const val EXTRA_RESULT_DATA = "capture_result_data"
        const val EXTRA_CAPTURE_PATH = "capture_path"
        const val EXTRA_READY_RECEIVER = "capture_ready_receiver"
        private const val CAPTURE_FILE_NAME = "last-board-capture.png"
        private const val CHANNEL_ID = "pad_screen_capture"
        private const val NOTIFICATION_ID = 41
        private const val REQUEST_STOP = 42
    }
}
