package com.example.extendedscreentimeout

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent

class DynamicTimeoutService : AccessibilityService() {

    companion object {
        private const val TAG = "DynamicTimeoutService"
    }

    private var timeoutMs = 10 * 60 * 1000L // Default 10 minutes

    private fun loadTimeoutPreference() {
        val prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE)
        val minutes = prefs.getInt("timeout_minutes", 10)
        timeoutMs = minutes * 60 * 1000L
        Log.d(TAG, "Timeout updated to $minutes minutes ($timeoutMs ms)")
    }
    private val handler = Handler(Looper.getMainLooper())
    
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

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
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
        val keyguardManager = getSystemService(KEYGUARD_SERVICE) as android.app.KeyguardManager

        if (!keyguardManager.isKeyguardLocked) {
            addKeepAwakeViewAndResetTimer()
        }
    }

    override fun onInterrupt() {
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service Destroyed")
        unregisterReceiver(screenReceiver)
        removeKeepAwakeView()
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
