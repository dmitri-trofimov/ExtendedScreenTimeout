package com.example.extendedscreentimeout

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.TextUtils
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.MaterialColors
import com.google.android.material.slider.Slider

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var btnOpenSettings: Button
    
    private lateinit var tvTimeoutValue: TextView
    private lateinit var timeoutSlider: Slider
    
    private lateinit var tvBatteryStatus: TextView
    private lateinit var btnBatteryOptimization: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        DynamicColors.applyToActivityIfAvailable(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        btnOpenSettings = findViewById(R.id.btnOpenSettings)
        tvTimeoutValue = findViewById(R.id.tvTimeoutValue)
        timeoutSlider = findViewById(R.id.timeoutSlider)
        tvBatteryStatus = findViewById(R.id.tvBatteryStatus)
        btnBatteryOptimization = findViewById(R.id.btnBatteryOptimization)

        btnOpenSettings.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            val componentName = ComponentName(this, DynamicTimeoutService::class.java).flattenToString()
            intent.putExtra(":settings:fragment_args_key", componentName)
            val bundle = Bundle()
            bundle.putString(":settings:fragment_args_key", componentName)
            intent.putExtra(":settings:show_fragment_args", bundle)

            try {
                val samsungIntent = Intent()
                samsungIntent.setClassName("com.android.settings", "com.android.settings.Settings\$AccessibilityInstalledServiceActivity")
                startActivity(samsungIntent)
            } catch (e: Exception) {
                startActivity(intent)
            }
        }
        
        btnBatteryOptimization.setOnClickListener {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            startActivity(intent)
        }

        // Setup Timeout Slider
        val prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        val savedTimeout = prefs.getInt("timeout_minutes", 10)
        timeoutSlider.value = savedTimeout.toFloat()
        tvTimeoutValue.text = "$savedTimeout Minutes"

        timeoutSlider.addOnChangeListener { _, value, _ ->
            val minutes = value.toInt()
            tvTimeoutValue.text = "$minutes Minutes"
            prefs.edit().putInt("timeout_minutes", minutes).apply()
            
            // Notify service to reload preferences
            // setPackage makes this an explicit broadcast, scoped to this app's process only
            val updateIntent = Intent("com.example.extendedscreentimeout.UPDATE_TIMEOUT").apply {
                setPackage(packageName)
            }
            sendBroadcast(updateIntent)
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
        updateBatteryStatus()
        requestNotificationPermissionIfNeeded()
    }

    private fun updateStatus() {
        if (isAccessibilityServiceEnabled(this, DynamicTimeoutService::class.java)) {
            tvStatus.text = "Status: ENABLED"
            tvStatus.setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary, Color.parseColor("#4CAF50")))
        } else {
            tvStatus.text = "Status: DISABLED"
            tvStatus.setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorError, Color.parseColor("#F44336")))
        }
    }

    private fun updateBatteryStatus() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (powerManager.isIgnoringBatteryOptimizations(packageName)) {
            tvBatteryStatus.text = "Optimization: Ignored (Good!)"
            tvBatteryStatus.setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary, Color.parseColor("#4CAF50")))
            btnBatteryOptimization.isEnabled = false
            btnBatteryOptimization.text = "Already Ignored"
        } else {
            tvBatteryStatus.text = "Optimization: Enabled (May cause issues)"
            tvBatteryStatus.setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorError, Color.parseColor("#F44336")))
            btnBatteryOptimization.isEnabled = true
            btnBatteryOptimization.text = "Ignore Battery Optimization"
        }
    }

    private fun isAccessibilityServiceEnabled(context: Context, accessibilityService: Class<*>): Boolean {
        val expectedComponentName = ComponentName(context, accessibilityService)
        val enabledServicesSetting = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServicesSetting)
        while (colonSplitter.hasNext()) {
            val componentNameString = colonSplitter.next()
            val enabledService = ComponentName.unflattenFromString(componentNameString)
            if (enabledService != null && enabledService == expectedComponentName) {
                return true
            }
        }
        return false
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                    0
                )
            }
        }
    }
}