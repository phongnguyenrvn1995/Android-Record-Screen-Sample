package com.example.recordscreensample

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MonitorActivity : AppCompatActivity() {
    private lateinit var mediaProjectionManager: MediaProjectionManager
    private lateinit var remote_ip: EditText
    private lateinit var remote_port: EditText
    private lateinit var quality: SeekBar
    private lateinit var r_group: RadioGroup
    private lateinit var r_btn_udp: RadioButton
    private lateinit var r_btn_tcp: RadioButton
    private lateinit var btn_start: Button
    private lateinit var btn_stop: Button
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        findViewById<View>(R.id.cfg_pannel).visibility = View.VISIBLE
        remote_ip = findViewById(R.id.remote_ip)
        remote_port = findViewById(R.id.remote_port)
        quality = findViewById(R.id.quality)
        r_group = findViewById(R.id.r_group)
        r_btn_udp = findViewById(R.id.r_btn_udp)
        r_btn_tcp = findViewById(R.id.r_btn_tcp)
        btn_start = findViewById(R.id.btn_start)
        btn_stop = findViewById(R.id.btn_stop)
        quality.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onProgressChanged(p0: SeekBar?, p1: Int, p2: Boolean) {
                Log.d("MainActivity3", "onProgressChanged: $p1 $p2")
                MonitorService.quality = if (p1 < 1) 1 else p1
            }

            override fun onStartTrackingTouch(p0: SeekBar?) {
            }

            override fun onStopTrackingTouch(p0: SeekBar?) {
            }
        })

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
//        openNotificationSettings(this)
    }

    private fun isUDP() =
        r_group.checkedRadioButtonId == R.id.r_btn_udp

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
            val serviceIntent = Intent(this, MonitorService::class.java)
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
        remote_ip.isEnabled = false
        remote_port.isEnabled = false
        r_btn_tcp.isEnabled = false
        r_btn_udp.isEnabled = false
        btn_start.isEnabled = false
        btn_stop.isEnabled = true

        MonitorService.remoteIP = remote_ip.text.toString()
        MonitorService.remotePort = remote_port.text.toString().toInt()
        MonitorService.isUDP = isUDP()
        // Khởi tạo MediaProjectionManager
        mediaProjectionManager =
            getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        // Tạo intent để yêu cầu quyền quay màn hình
        val screenCaptureIntent = mediaProjectionManager.createScreenCaptureIntent()
        startActivityForResult(screenCaptureIntent, REQUEST_CODE)
    }

    fun stop(view: View) {
        remote_ip.isEnabled = true
        remote_port.isEnabled = true
        r_btn_tcp.isEnabled = true
        r_btn_udp.isEnabled = true
        btn_start.isEnabled = true
        btn_stop.isEnabled = false

        val stopIntent = Intent(this, MonitorService::class.java)
        stopIntent.action = MonitorService.ACTION_STOP
        startService(stopIntent)
    }

}