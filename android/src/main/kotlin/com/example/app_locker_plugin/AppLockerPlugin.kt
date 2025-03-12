package com.example.app_locker_plugin

import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import java.util.Timer
import java.util.TimerTask

class AppLockerPlugin : FlutterPlugin, MethodCallHandler, ActivityAware {
    private lateinit var channel: MethodChannel
    private lateinit var context: Context
    private var activityBinding: ActivityPluginBinding? = null

    companion object {
        private var instance: AppLockerPlugin? = null
        private const val TAG = "AppLockerPlugin"

        fun getInstance(): AppLockerPlugin {
            if (instance == null) {
                instance = AppLockerPlugin()
            }
            return instance!!
        }

        fun invokeMethod(method: String, arguments: Any?) {
            Log.d(TAG, "Invoking method: $method with args: $arguments")
            instance?.channel?.invokeMethod(method, arguments) { result ->
                Log.d(TAG, "Method $method result: $result")
            }
        }
    }

    override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        channel = MethodChannel(flutterPluginBinding.binaryMessenger, "app_locker_plugin")
        channel.setMethodCallHandler(this)
        context = flutterPluginBinding.applicationContext
        instance = this
        Log.d(TAG, "Plugin attached to engine")
    }

    override fun onMethodCall(call: MethodCall, result: Result) {
        when (call.method) {
            "startService" -> startService(call, result)
            "stopService" -> stopService(result)
            "checkOverlayPermission" -> checkOverlayPermission(result)
            "askOverlayPermission" -> askOverlayPermission(result)
            "checkUsageStatsPermission" -> checkUsageStatsPermission(result)
            "askUsageStatsPermission" -> askUsageStatsPermission(result)
            "showOverlay" -> showOverlay(result)
            "hideOverlay" -> hideOverlay(result)
            else -> result.notImplemented()
        }
    }

    private fun startService(call: MethodCall, result: Result) {
        val appList = call.argument<List<String>>("appList") ?: emptyList()
        val saveAppData: SharedPreferences = context.getSharedPreferences("save_app_data", Context.MODE_PRIVATE)
        val editor = saveAppData.edit()
        editor.putString("app_data", appList.joinToString(","))
        editor.putString("is_stopped", "0")
        editor.apply()
        Log.d(TAG, "Starting service with app list: $appList")

        if (Settings.canDrawOverlays(context)) {
            ContextCompat.startForegroundService(context, Intent(context, AppLockService::class.java))
            result.success("Service started successfully")
        } else {
            result.error("PERMISSION_DENIED", "Overlay permission is not granted", null)
        }
    }

    private fun stopService(result: Result) {
        val saveAppData: SharedPreferences = context.getSharedPreferences("save_app_data", Context.MODE_PRIVATE)
        val editor = saveAppData.edit()
        editor.putString("is_stopped", "1")
        editor.apply()
        context.stopService(Intent(context, AppLockService::class.java))
        Log.d(TAG, "Service stopped")
        result.success("Service stopped successfully")
    }

    private fun checkOverlayPermission(result: Result) {
        result.success(Settings.canDrawOverlays(context))
    }

    private fun askOverlayPermission(result: Result) {
        if (!Settings.canDrawOverlays(context)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            activityBinding?.activity?.startActivity(intent)
        }
        result.success(Settings.canDrawOverlays(context))
    }

    private fun checkUsageStatsPermission(result: Result) {
        val appOpsManager = context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        val mode = appOpsManager.checkOpNoThrow(
            android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName
        )
        result.success(mode == android.app.AppOpsManager.MODE_ALLOWED)
    }

    private fun askUsageStatsPermission(result: Result) {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        activityBinding?.activity?.startActivity(intent)
        result.success(true)
    }

    private fun showOverlay(result: Result) {
        Handler(Looper.getMainLooper()).post {
            invokeMethod("showOverlay", null)
        }
        result.success(true)
    }

    private fun hideOverlay(result: Result) {
        Handler(Looper.getMainLooper()).post {
            invokeMethod("hideOverlay", null)
        }
        result.success(true)
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
        instance = null
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        activityBinding = binding
    }

    override fun onDetachedFromActivity() {
        activityBinding = null
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        activityBinding = binding
    }

    override fun onDetachedFromActivityForConfigChanges() {
        activityBinding = null
    }
}

class AppLockService : Service() {
    private var timer: Timer = Timer()
    private var timerReload: Long = 500
    private var lockedApps: List<String> = emptyList()
    private val TAG = "AppLockService"
    private var isOverlayVisible = false

    override fun onBind(intent: Intent): IBinder? {
        throw UnsupportedOperationException("")
    }

    override fun onCreate() {
        super.onCreate()
        val channelId = "AppLock-10"
        val channel = android.app.NotificationChannel(
            channelId,
            "App Lock Service",
            android.app.NotificationManager.IMPORTANCE_DEFAULT
        )
        (getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager).createNotificationChannel(channel)
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("App Locker Service")
            .setContentText("Monitoring app usage")
            .build()
        startForeground(1, notification)
        Log.d(TAG, "Service created and started as foreground")
        loadLockedApps()
        startMonitoring()
    }

    private fun loadLockedApps() {
        val saveAppData: SharedPreferences = applicationContext.getSharedPreferences("save_app_data", Context.MODE_PRIVATE)
        lockedApps = saveAppData.getString("app_data", "")!!.split(",").filter { it.isNotEmpty() }
        Log.d(TAG, "Loaded locked apps: $lockedApps")
    }

    private fun startMonitoring() {
        timer.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                checkForegroundApp()
            }
        }, 0, timerReload)
    }

    private fun checkForegroundApp() {
        val saveAppData: SharedPreferences = applicationContext.getSharedPreferences("save_app_data", Context.MODE_PRIVATE)
        if (saveAppData.getString("is_stopped", "1") == "1") {
            Log.d(TAG, "Service is stopped, skipping check")
            return
        }

        val mUsageStatsManager = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager
        val time = System.currentTimeMillis()
        val usageEvents = mUsageStatsManager.queryEvents(time - timerReload * 2, time)
        val event = UsageEvents.Event()

        var foregroundApp: String? = null
        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                foregroundApp = event.packageName
                Log.d(TAG, "Activity resumed: $foregroundApp")
            }
        }

        if (foregroundApp != null && lockedApps.contains(foregroundApp) && !isOverlayVisible) {
            Log.d(TAG, "Locked app detected: $foregroundApp, showing overlay")
            Handler(Looper.getMainLooper()).post {
                AppLockerPlugin.invokeMethod("showOverlay", mapOf("packageName" to foregroundApp))
                isOverlayVisible = true
                forceHomeScreen() // Optional: Force back to home screen
            }
        } else if (foregroundApp != null && !lockedApps.contains(foregroundApp) && isOverlayVisible) {
            Log.d(TAG, "Non-locked app in foreground: $foregroundApp, hiding overlay")
            Handler(Looper.getMainLooper()).post {
                AppLockerPlugin.invokeMethod("hideOverlay", null)
                isOverlayVisible = false
            }
        }
    }

    private fun forceHomeScreen() {
        val homeIntent = Intent(Intent.ACTION_MAIN)
        homeIntent.addCategory(Intent.CATEGORY_HOME)
        homeIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(homeIntent)
        Log.d(TAG, "Forced back to home screen")
    }

    override fun onDestroy() {
        timer.cancel()
        Log.d(TAG, "Service destroyed")
        super.onDestroy()
    }
}

class BootUpReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val saveAppData: SharedPreferences = context.getSharedPreferences("save_app_data", Context.MODE_PRIVATE)
        if (saveAppData.getString("is_stopped", "1") == "0") {
            ContextCompat.startForegroundService(context, Intent(context, AppLockService::class.java))
            Log.d("BootUpReceiver", "Service restarted after boot")
        }
    }
}