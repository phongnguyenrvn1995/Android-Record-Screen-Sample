package com.example.recordscreensample

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity3 : AppCompatActivity() {


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

    override fun onResume() {
        super.onResume()
        checkNotificationPermission()
    }

    private fun checkNotificationPermission() {
        // Check if the notification permission is granted (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                // Permission is already granted
            } else if (ActivityCompat.shouldShowRequestPermissionRationale(
                    this,
                    POST_NOTIFICATIONS
                )
            ) {
                // Show rationale and request permission
                requestPermissionLauncher.launch(POST_NOTIFICATIONS)
            } else {
                // Directly request for required permission
                requestPermissionLauncher.launch(POST_NOTIFICATIONS)
            }
        }
    }

    // Define the request code
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Permission is granted. Continue with the action or workflow
        } else {
            // Explain to the user that the feature is unavailable because the
            // features require a permission that the user has denied
            finish()
        }
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
            val serviceIntent = Intent(this, ScreenRecordService3::class.java)
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
        private const val POST_NOTIFICATIONS = "android.permission.POST_NOTIFICATIONS"
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
        val stopIntent = Intent(this, ScreenRecordService3::class.java)
        stopIntent.action = ScreenRecordService3.ACTION_STOP
        startService(stopIntent)
    }

}