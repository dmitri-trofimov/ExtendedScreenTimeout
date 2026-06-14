package com.example.extendedscreentimeout

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import androidx.core.app.NotificationCompat

class DynamicTimeoutService : AccessibilityService() {

    companion object {
        private const val TAG = "DynamicTimeoutService"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "screen_timeout_channel"
    }

    private var timeoutMs = 10 * 60 * 1000L // Default 10 minutes

    private fun loadTimeoutPreference() {
        val prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE)
        val minutes = prefs.getInt("timeout_minutes", 10)
        timeoutMs = minutes * 60 * 1000L
        Log.d(TAG, "Timeout updated to $minutes minutes ($timeoutMs ms)")
    }
    private val handler = Handler(Looper.getMainLooper())

    // Cached system services — looked up once to avoid overhead in hot paths
    private lateinit var keyguardManager: KeyguardManager

    private var windowManager: WindowManager? = null
    private var keepAwakeView: View? = null

    private val removeKeepAwakeViewRunnable = Runnable {
        Log.d(TAG, "Timeout passed. Removing KeepAwake view.")
        removeKeepAwakeView()
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    Log.d(TAG, "Screen manually turned off. Removing KeepAwake view.")
                    removeKeepAwakeView()
                }
                Intent.ACTION_SCREEN_ON -> {
                    // Handles devices with no lock screen — USER_PRESENT is never fired for them.
                    // If the keyguard IS locked we do nothing and wait for USER_PRESENT instead.
                    if (!keyguardManager.isKeyguardLocked) {
                        Log.d(TAG, "Screen on, no keyguard. Starting KeepAwake view and timer.")
                        addKeepAwakeViewAndResetTimer()
                    }
                }
                Intent.ACTION_USER_PRESENT -> {
                    Log.d(TAG, "Device unlocked. Starting KeepAwake view and timer.")
                    addKeepAwakeViewAndResetTimer()
                }
                "com.example.extendedscreentimeout.UPDATE_TIMEOUT" -> {
                    loadTimeoutPreference()
                    addKeepAwakeViewAndResetTimer() // Reset timer with new duration
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Service Connected")

        loadTimeoutPreference()

        keyguardManager = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction("com.example.extendedscreentimeout.UPDATE_TIMEOUT")
        }
        androidx.core.content.ContextCompat.registerReceiver(
            this,
            screenReceiver,
            filter,
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        )

        addKeepAwakeViewAndResetTimer()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Uses the cached keyguardManager field — avoids a system service lookup on every event
        if (!keyguardManager.isKeyguardLocked) {
            addKeepAwakeViewAndResetTimer()
        }
    }

    override fun onInterrupt() {
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service Destroyed")
        stopForeground(STOP_FOREGROUND_REMOVE)
        unregisterReceiver(screenReceiver)
        removeKeepAwakeView()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_MIN // Silent, no sound or vibration
        ).apply {
            description = getString(R.string.notification_channel_description)
            setShowBadge(false)
        }
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setSilent(true)
            .build()
    }

    private fun addKeepAwakeViewAndResetTimer() {
        handler.removeCallbacks(removeKeepAwakeViewRunnable)
        
        if (keepAwakeView == null && windowManager != null) {
            keepAwakeView = View(this)
            val params = WindowManager.LayoutParams(
                1, 1, // 1x1 pixel size
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            )
            params.gravity = Gravity.TOP or Gravity.START
            
            @android.annotation.SuppressLint("ClickableViewAccessibility")
            keepAwakeView?.setOnTouchListener { _, event ->
                if (event.action == android.view.MotionEvent.ACTION_OUTSIDE) {
                    Log.d(TAG, "Raw touch detected! Resetting timer.")
                    addKeepAwakeViewAndResetTimer()
                }
                false
            }
            
            try {
                windowManager?.addView(keepAwakeView, params)
                Log.d(TAG, "KeepAwake view added successfully.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to add KeepAwake view", e)
                keepAwakeView = null
            }
        }

        Log.d(TAG, "Timer reset to ${timeoutMs / 60000} minutes from now due to interaction.")
        handler.postDelayed(removeKeepAwakeViewRunnable, timeoutMs)
    }

    private fun removeKeepAwakeView() {
        handler.removeCallbacks(removeKeepAwakeViewRunnable)
        if (keepAwakeView != null && windowManager != null) {
            try {
                windowManager?.removeView(keepAwakeView)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to remove KeepAwake view", e)
            }
            keepAwakeView = null
        }
    }
}
