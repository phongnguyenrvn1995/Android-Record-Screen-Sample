package com.example.recordscreensample

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {


    private lateinit var mediaProjectionManager: MediaProjectionManager
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
//        openNotificationSettings(this)
    }

    fun openNotificationSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }
        context.startActivity(intent)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_CODE && resultCode == RESULT_OK) {
            // Khởi động ScreenRecordService với các tham số cần thiết
            val serviceIntent = Intent(this, ScreenRecordService::class.java)
            serviceIntent.putExtra("RESULT_CODE", resultCode)
            serviceIntent.putExtra("DATA", data)
            startService(serviceIntent)
        } else {
            // Người dùng không cho phép quay màn hình
            // Xử lý trường hợp từ chối ở đây nếu cần
        }
    }

    companion object {
        private const val REQUEST_CODE = 1000
    }

    fun start(view: View) {
        // Khởi tạo MediaProjectionManager
        mediaProjectionManager =
            getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        // Tạo intent để yêu cầu quyền quay màn hình
        val screenCaptureIntent = mediaProjectionManager.createScreenCaptureIntent()
        startActivityForResult(screenCaptureIntent, REQUEST_CODE)
    }

    fun stop(view: View) {
        val stopIntent = Intent(this, ScreenRecordService::class.java)
        stopIntent.action = ScreenRecordService.ACTION_STOP
        startService(stopIntent)
    }

}