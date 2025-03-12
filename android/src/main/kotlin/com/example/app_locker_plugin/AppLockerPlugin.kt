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
import android.view.WindowManager
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

        fun getInstance(): AppLockerPlugin {
            if (instance == null) {
                instance = AppLockerPlugin()
            }
            return instance!!
        }

        fun invokeMethod(method: String, arguments: Any?) {
            instance?.channel?.invokeMethod(method, arguments)
        }
    }

    override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        channel = MethodChannel(flutterPluginBinding.binaryMessenger, "app_locker_plugin")
        channel.setMethodCallHandler(this)
        context = flutterPluginBinding.applicationContext
        instance = this
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
        editor.putString("is_stopped", "0") // 0 means service is running
        editor.apply()

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
        editor.putString("is_stopped", "1") // 1 means service is stopped
        editor.apply()
        context.stopService(Intent(context, AppLockService::class.java))
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
        // Notify Flutter to show the overlay UI
        Handler(Looper.getMainLooper()).post {
            invokeMethod("showOverlay", null)
        }
        result.success(true)
    }

    private fun hideOverlay(result: Result) {
        // Notify Flutter to hide the overlay UI
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
    private var isTimerStarted = false
    private var timerReload: Long = 500
    private var currentAppActivityList = arrayListOf<String>()
    private var homeWatcher = HomeWatcher(this)

    override fun onBind(intent: Intent): IBinder? {
        throw UnsupportedOperationException("")
    }

    override fun onCreate() {
      super.onCreate()
      val channelId = "AppLock-10"
      val channel = android.app.NotificationChannel(
          channelId,
          "Channel human readable title",
          android.app.NotificationManager.IMPORTANCE_DEFAULT
      )
      (getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager).createNotificationChannel(channel)
      val notification = NotificationCompat.Builder(this, channelId)
          .setContentTitle("App Locker Service")
          .setContentText("Monitoring app usage")
          .build()
      // Ensure the foreground service type is correctly set
      startForeground(1, notification)
      startMyOwnForeground()
    }

    private fun startMyOwnForeground() {
        homeWatcher.setOnHomePressedListener(object : HomeWatcher.OnHomePressedListener {
            override fun onHomePressed() {
                currentAppActivityList.clear()
            }

            override fun onHomeLongPressed() {
                currentAppActivityList.clear()
            }
        })
        homeWatcher.startWatch()
        timerRun()
    }

    override fun onDestroy() {
        timer.cancel()
        homeWatcher.stopWatch()
        super.onDestroy()
    }

    private fun timerRun() {
        timer.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                isTimerStarted = true
                isServiceRunning()
            }
        }, 0, timerReload)
    }

    private fun isServiceRunning() {
        val saveAppData: SharedPreferences = applicationContext.getSharedPreferences("save_app_data", Context.MODE_PRIVATE)
        val lockedAppList: List<String> = saveAppData.getString("app_data", "")!!.split(",").filter { it.isNotEmpty() }

        val mUsageStatsManager = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager
        val time = System.currentTimeMillis()

        val usageEvents = mUsageStatsManager.queryEvents(time - timerReload, time)
        val event = UsageEvents.Event()

        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            for (element in lockedAppList) {
                if (event.packageName.toString().trim() == element.trim()) {
                    if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED && currentAppActivityList.isEmpty()) {
                        currentAppActivityList.add(event.className)
                        Handler(Looper.getMainLooper()).post {
                            AppLockerPlugin.invokeMethod("showOverlay", null)
                        }
                    } else if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                        if (!currentAppActivityList.contains(event.className)) {
                            currentAppActivityList.add(event.className)
                        }
                    } else if (event.eventType == UsageEvents.Event.ACTIVITY_STOPPED) {
                        if (currentAppActivityList.contains(event.className)) {
                            currentAppActivityList.remove(event.className)
                        }
                    }
                }
            }
        }
    }
}

class HomeWatcher(private val mContext: Context) {
    private val mFilter: IntentFilter
    private var mListener: OnHomePressedListener? = null
    private var mReceiver: InnerReceiver? = null

    fun setOnHomePressedListener(listener: OnHomePressedListener?) {
        mListener = listener
        mReceiver = InnerReceiver()
    }

    fun startWatch() {
        if (mReceiver != null) {
            mContext.registerReceiver(mReceiver, mFilter)
        }
    }

    fun stopWatch() {
        if (mReceiver != null) {
            mContext.unregisterReceiver(mReceiver)
        }
    }

    interface OnHomePressedListener {
        fun onHomePressed()
        fun onHomeLongPressed()
    }

    inner class InnerReceiver : BroadcastReceiver() {
        val SYSTEM_DIALOG_REASON_KEY = "reason"
        val SYSTEM_DIALOG_REASON_RECENT_APPS = "recentapps"
        val SYSTEM_DIALOG_REASON_HOME_KEY = "homekey"

        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (action == Intent.ACTION_CLOSE_SYSTEM_DIALOGS) {
                val reason = intent.getStringExtra(SYSTEM_DIALOG_REASON_KEY)
                if (reason != null) {
                    if (mListener != null) {
                        if (reason == SYSTEM_DIALOG_REASON_HOME_KEY) {
                            mListener!!.onHomePressed()
                        } else if (reason == SYSTEM_DIALOG_REASON_RECENT_APPS) {
                            mListener!!.onHomeLongPressed()
                        }
                    }
                }
            }
        }
    }

    init {
        mFilter = IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
    }
}

class BootUpReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent?) {
      val saveAppData: SharedPreferences = context.getSharedPreferences("save_app_data", Context.MODE_PRIVATE)
      if (saveAppData.getString("is_stopped", "1") == "0") {
          ContextCompat.startForegroundService(context, Intent(context, AppLockService::class.java))
      }
  }
}