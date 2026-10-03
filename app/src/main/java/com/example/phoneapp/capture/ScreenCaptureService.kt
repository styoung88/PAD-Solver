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
import com.example.phoneapp.MainActivity
import com.example.phoneapp.R
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean

class ScreenCaptureService : Service() {
    private val capturePending = AtomicBoolean(false)
    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
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
    }

    private fun onImageAvailable(reader: ImageReader) {
        val image = reader.acquireLatestImage() ?: return
        if (!capturePending.compareAndSet(true, false)) {
            image.close()
            return
        }
        val frame = image.use(::imageToBitmap) ?: run {
            updateNotification("Could not read the screen. Tap Capture board to retry.")
            return
        }
        saveCapture(frame)
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
        if (!capturePending.compareAndSet(false, true)) return
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

        val reviewIntent = Intent(this, MainActivity::class.java).apply {
            action = ACTION_REVIEW_CAPTURE
            putExtra(EXTRA_CAPTURE_PATH, captureFile.absolutePath)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        try {
            startActivity(reviewIntent)
        } finally {
            stopSelf()
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
        val notification = makeNotification("Switch to PAD, then use this notification to capture the board.")
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
        val captureIntent = Intent(this, ScreenCaptureService::class.java).setAction(ACTION_CAPTURE_NOW)
        val capturePendingIntent = PendingIntent.getService(
            this,
            REQUEST_CAPTURE,
            captureIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = Intent(this, ScreenCaptureService::class.java).setAction(ACTION_STOP)
        val stopPendingIntent = PendingIntent.getService(
            this,
            REQUEST_STOP,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("PAD board capture is active")
            .setContentText(message)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(R.drawable.ic_notification, "Capture board", capturePendingIntent)
            .addAction(R.drawable.ic_notification, "Stop capture", stopPendingIntent)
            .build()
    }

    override fun onDestroy() {
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
        const val ACTION_START = "com.example.phoneapp.action.START_CAPTURE"
        const val ACTION_CAPTURE_NOW = "com.example.phoneapp.action.CAPTURE_NOW"
        const val ACTION_STOP = "com.example.phoneapp.action.STOP_CAPTURE"
        const val ACTION_REVIEW_CAPTURE = "com.example.phoneapp.action.REVIEW_CAPTURE"
        const val EXTRA_RESULT_CODE = "capture_result_code"
        const val EXTRA_RESULT_DATA = "capture_result_data"
        const val EXTRA_CAPTURE_PATH = "capture_path"
        private const val CAPTURE_FILE_NAME = "last-board-capture.png"
        private const val CHANNEL_ID = "pad_screen_capture"
        private const val NOTIFICATION_ID = 41
        private const val REQUEST_STOP = 42
        private const val REQUEST_CAPTURE = 43
    }
}
