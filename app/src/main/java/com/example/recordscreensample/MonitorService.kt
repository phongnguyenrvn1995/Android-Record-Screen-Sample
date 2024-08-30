package com.example.recordscreensample


import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress


class MonitorService : Service() {

    private lateinit var mediaProjectionManager: MediaProjectionManager
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var isRecording = false
    private lateinit var imageReader: ImageReader

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
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP) {
            if (isRecording) {
                stopScreenRecording()
            }
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
        clearExternalCacheDir(baseContext)
        val screenResolution = getScreenResolution()
        val width = screenResolution.first
        val height = screenResolution.second

        // Tạo ImageReader
        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        imageReader.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage()
            if (image != null) {
                processImage(image)
                Log.d(TAG, "startScreenRecording: $image")
                image.close()
            }
        }, null)

        // Tạo VirtualDisplay
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenRecorder",
            width,
            height,
            DisplayMetrics.DENSITY_DEVICE_STABLE,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader.surface,
            null,
            null
        )

        isRecording = true
    }

    private fun processImage(image: Image) {
        // Chuyển đổi Image thành Bitmap
        val buffer = image.planes[0].buffer
//        val width = image.width
//        val height = image.height
//        var bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        val pixelStride: Int = image.planes[0].pixelStride
        val rowStride: Int = image.planes[0].rowStride
        val rowPadding = rowStride - pixelStride * imageReader.width

        val bitmap = Bitmap.createBitmap(
            imageReader.width + rowPadding / pixelStride,
            imageReader.height, Bitmap.Config.ARGB_8888
        )

        buffer.rewind()
        bitmap.copyPixelsFromBuffer(buffer)

        saveBitmapToFile(cropBitmap(bitmap, 0, 0, image.width, image.height))
    }

    fun cropBitmap(originalBitmap: Bitmap, x: Int, y: Int, width: Int, height: Int): Bitmap {
        // Kiểm tra các tham số để đảm bảo không vượt quá kích thước hình ảnh gốc
        require(!(x < 0 || y < 0 || x + width > originalBitmap.width || y + height > originalBitmap.height)) { "Crop dimensions are out of bounds" }

        // Cắt hình ảnh
        return Bitmap.createBitmap(originalBitmap, x, y, width, height)
    }

    private fun saveBitmapToFile(bitmap: Bitmap) {
        val file = File(externalCacheDir, "screen_record${System.currentTimeMillis()}.jpg")
        try {
            FileOutputStream(file).use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
            }
            sendData2Server(convertBitmapToByteArray(bitmap))

        } catch (e: IOException) {
            Log.e(TAG, "Failed to save bitmap to file", e)
        }
    }

    fun convertBitmapToByteArray(bitmap: Bitmap): ByteArray {
        // Tạo một ByteArrayOutputStream
        val stream = ByteArrayOutputStream()

        // Chuyển đổi Bitmap sang mảng byte bằng cách sử dụng phương thức compress()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)

        // Lấy mảng byte từ ByteArrayOutputStream
        val byteArray = stream.toByteArray()

        return byteArray
    }

    private fun stopScreenRecording() {
        if (isRecording) {
            isRecording = false
            stopForeground(STOP_FOREGROUND_REMOVE)
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

    private fun getScreenResolution(): Pair<Int, Int> {
        val wm = baseContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val display = wm.defaultDisplay
        val metrics = DisplayMetrics()
        display.getRealMetrics(metrics)
        return Pair(metrics.widthPixels, metrics.heightPixels)
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

    override fun onDestroy() {
        stopScreenRecording()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    fun clearExternalCacheDir(context: Context) {
        val externalCacheDir = context.externalCacheDir
        externalCacheDir?.let { dir ->
            if (dir.isDirectory) {
                val files = dir.listFiles()
                files?.forEach { file ->
                    if (file.isFile) {
                        file.delete()
                    }
                }
            }
        }
    }

    companion object {
        const val ACTION_STOP = "com.example.screenrecord.ACTION_STOP"
        const val TAG = "ScreenRecordService"
        var remoteIP = ""
        var remotePort = 9876
        var quality = 1

        var isSendingData = false
        fun sendData2Server(sendData: ByteArray) = GlobalScope.launch {
            Log.d(TAG, "sendData2Server: ${sendData.size}")
            if (isSendingData) return@launch
            isSendingData = true
            _sendData2Server("==IMAGE START==".toByteArray())
            val size = 10 * 1024
            var idx = 0
            while (idx < sendData.size) {
                var expectSize = size
                if (idx + size > sendData.size) {
                    expectSize = sendData.size - idx
                }
                val expectBuff = ByteArray(expectSize)
                System.arraycopy(sendData, idx, expectBuff, 0, expectSize)
                _sendData2Server(expectBuff)

                idx += expectSize
            }
            _sendData2Server("==IMAGE END==".toByteArray())
            isSendingData = false
        }

        fun _sendData2Server(sendData: ByteArray) {
            try {
                // Tạo socket client
                val clientSocket = DatagramSocket()
                // Địa chỉ IP và cổng của server
                val serverAddress = InetAddress.getByName(remoteIP)
                val serverPort = remotePort

                // Tạo gói tin để gửi
                val sendPacket = DatagramPacket(sendData, sendData.size, serverAddress, serverPort)
                clientSocket.send(sendPacket) // Gửi gói tin đến server

                // Tạo mảng byte để nhận dữ liệu từ server
                /*val receiveData = ByteArray(1024)
                val receivePacket = DatagramPacket(receiveData, receiveData.size)
                clientSocket.receive(receivePacket) // Nhận gói tin phản hồi từ server*/

                // Chuyển đổi dữ liệu nhận được thành chuỗi và hiển thị
                /*val replyMessage = String(receivePacket.data, 0, receivePacket.length)
                println("Received from server: $replyMessage")*/

                // Đóng socket sau khi hoàn tất
                clientSocket.close()
            } catch (e: java.lang.Exception) {
                e.printStackTrace()
            }
        }
    }
}

