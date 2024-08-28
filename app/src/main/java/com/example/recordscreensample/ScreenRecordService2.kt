package com.example.recordscreensample

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaCodec
import android.media.MediaCodec.Callback
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.util.Log
import android.view.Surface
import android.view.WindowManager
import androidx.core.app.NotificationCompat

class ScreenRecordService2 : Service() {

    private lateinit var mediaProjectionManager: MediaProjectionManager
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var isRecording = false
    private lateinit var mediaCodec: MediaCodec
    private lateinit var outputFilePath: String
    private var videoRecorder: VideoRecorder? = null

    private val mediaProjectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            super.onStop()
            stopScreenRecording()
            stopSelf()
        }
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = "screen_record_channel"
            val channelName = "Screen Recording Service"
            val channel =
                NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW)
            val manager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel(this)
        getOutputFilePath()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP) {
            if (isRecording)
                mediaCodec.signalEndOfInputStream()
        } else {
            startForegroundService()
            val resultCode = intent?.getIntExtra("RESULT_CODE", Activity.RESULT_CANCELED)
                ?: Activity.RESULT_CANCELED
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
        val inputSurface = setupMediaCodec()

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenRecorder",
            getScreenResolution().first,
            getScreenResolution().second,
            DisplayMetrics.DENSITY_DEFAULT,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            inputSurface,
            null, null
        )

        mediaCodec.setCallback(object : Callback() {
            override fun onInputBufferAvailable(p0: MediaCodec, p1: Int) {
                Log.d(TAG, "onInputBufferAvailable: ")
            }

            override fun onOutputBufferAvailable(
                codec: MediaCodec,
                index: Int,
                info: MediaCodec.BufferInfo
            ) {
                val outputBuffer = codec.getOutputBuffer(index)
                if (outputBuffer != null && info.size > 0) {
                    // Ghi dữ liệu vào file thông qua MediaMuxer
                    videoRecorder?.writeSampleData(outputBuffer, info)
                }

                codec.releaseOutputBuffer(index, false)

                if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    // Kết thúc stream
                    stopScreenRecording()
                    stopSelf()
                    Log.d(TAG, "onOutputBufferAvailable: STOP")
                }
            }


            override fun onError(p0: MediaCodec, p1: MediaCodec.CodecException) {
                Log.d(TAG, "onError: ${p1}")
            }

            override fun onOutputFormatChanged(p0: MediaCodec, p1: MediaFormat) {
                Log.d(TAG, "onOutputFormatChanged: ")
            }
        })

        mediaCodec.start()
        isRecording = true
    }

    fun getOutputFilePath(): String {
        outputFilePath = "${externalCacheDir?.absolutePath}/screen_record.mp4"
        return outputFilePath
    }

    private fun setupMediaCodec(): Surface {
        val width = getScreenResolution().first
        val height = getScreenResolution().second
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height)
        format.setInteger(
            MediaFormat.KEY_COLOR_FORMAT,
            MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
        )
        format.setInteger(MediaFormat.KEY_BIT_RATE, (0.5 * 1024 * 1024).toInt())
        format.setInteger(MediaFormat.KEY_FRAME_RATE, 10)
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)

        mediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        mediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        videoRecorder = VideoRecorder()
        videoRecorder?.startRecording(outputFilePath)
        videoRecorder?.addVideoTrack(format)

        // Ensure that createInputSurface() is called after configure()
        val inputSurface = mediaCodec.createInputSurface()

        return inputSurface
    }

    private fun startForegroundService() {
        val notificationChannelId = "SCREEN_RECORD_SERVICE"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                notificationChannelId,
                "Screen Recording",
                NotificationManager.IMPORTANCE_LOW
            )
            val notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        val notificationBuilder = NotificationCompat.Builder(this, notificationChannelId)
            .setContentTitle("Screen Recording")
            .setContentText("Recording screen in progress...")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        startForeground(1, notificationBuilder.build())
    }

    private fun stopScreenRecording() {
        try {
            if (isRecording) {
                // Gửi tín hiệu kết thúc stream
//                mediaCodec.signalEndOfInputStream()

                // Set a flag to handle the end of the stream in the callback
                isRecording = false
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            isRecording = false
        }

        try {
            videoRecorder?.stopRecording()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            videoRecorder = null
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
        const val TAG = "ScreenRecordService"
    }
}

