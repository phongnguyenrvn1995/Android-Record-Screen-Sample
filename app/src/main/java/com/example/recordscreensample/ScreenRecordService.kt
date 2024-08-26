package com.example.recordscreensample

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import java.io.File

class ScreenRecordService : Service() {

    private lateinit var mediaProjectionManager: MediaProjectionManager
    private var mediaProjection: MediaProjection? = null
    private var mediaRecorder: MediaRecorder? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var isRecording = false
    private val mediaProjectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            super.onStop()
            // Clean up resources here
            stopSelf()
        }
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = "screen_record_channel"
            val channelName = "Screen Recording Service"
            val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW)
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel(this)
    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP) {
            stopScreenRecording()
            stopSelf()
        } else {
            startForegroundService()
            val resultCode = intent?.getIntExtra("RESULT_CODE", Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
            val data = intent?.getParcelableExtra<Intent>("DATA")

            mediaProjectionManager =
                getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data!!)
            mediaProjection?.registerCallback(mediaProjectionCallback, null)

            startScreenRecording()
        }

        return START_NOT_STICKY
    }

    private fun startScreenRecording() {
        mediaRecorder = MediaRecorder()
        setupMediaRecorder()

        // Lấy WindowManager từ context của service
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenRecorder",
            DisplayMetrics().apply {
                windowManager.defaultDisplay.getMetrics(this)
            }.widthPixels,
            DisplayMetrics().apply {
                windowManager.defaultDisplay.getMetrics(this)
            }.heightPixels,
            DisplayMetrics().apply {
                windowManager.defaultDisplay.getMetrics(this)
            }.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            mediaRecorder?.surface, null, null
        )

        mediaRecorder?.start()
        isRecording = true
    }

    private fun setupMediaRecorder() {
        val videoFile = "${externalCacheDir?.absolutePath}/screen_record.mp4"
        val videoF = File(externalCacheDir, "screen_record.mp4")
        if (videoF.exists()) {
            Log.d("ScreenRecord", "File exists: ${videoF.absolutePath}")
        } else {
            Log.d("ScreenRecord", "File does not exist.")
        }

        mediaRecorder?.apply {
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setOutputFile(videoFile)
            setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            setVideoSize(getScreenResolution().first, getScreenResolution().second)
            setVideoFrameRate(30)
            setVideoEncodingBitRate(5 * 1024 * 1024)
            prepare()
        }
    }

    private fun startForegroundService() {
        val notificationChannelId = "SCREEN_RECORD_SERVICE"
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                notificationChannelId,
                "Screen Recording",
                NotificationManager.IMPORTANCE_LOW
            )
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        val notificationBuilder = NotificationCompat.Builder(this, notificationChannelId)
            .setContentTitle("Screen Recording")
            .setContentText("Recording screen in progress...")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setPriority(NotificationCompat.PRIORITY_LOW)  // Sử dụng PRIORITY thấp cho các API thấp hơn

        startForeground(1, notificationBuilder.build())
    }


    private fun stopScreenRecording() {
        try {
            mediaRecorder?.apply {
                if (isRecording) {  // Kiểm tra trạng thái ghi
                    stop()
                    reset()
                    release()
                }
            }
        } catch (e: IllegalStateException) {
            e.printStackTrace()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            mediaRecorder = null
        }

        try {
            virtualDisplay?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            virtualDisplay = null
        }

        try {
            mediaProjection?.unregisterCallback(mediaProjectionCallback)
            mediaProjection?.stop()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            mediaProjection = null
        }
    }

    fun getScreenResolution(): Pair<Int, Int> {
        val wm = baseContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val display = wm.defaultDisplay
        val metrics = DisplayMetrics()
        display.getRealMetrics(metrics)
        return Pair(metrics.widthPixels, metrics.heightPixels)
    }

    override fun onDestroy() {
        stopScreenRecording()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    companion object {
        const val ACTION_STOP = "com.example.screenrecord.ACTION_STOP"
    }
}