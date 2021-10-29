package de.cketti.adbscreenawake

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import android.os.PowerManager.WakeLock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.content.getSystemService

/**
 * Quick and dirty hack to keep the screen awake while ADB is connected to the device
 */
class AdbNotificationListenerService : NotificationListenerService() {
    private var wakeLock: WakeLock? = null

    private var adbConnected = false
        set(value) {
            if (field == value) return

            if (value) {
                println("ADB connected")
                keepScreenAwake()
            } else {
                println("ADB disconnected")
                leaveScreenAlone()
            }

            field = value
        }

    private val intentFilter = IntentFilter().apply {
        addAction(Intent.ACTION_SCREEN_ON)
        addAction(Intent.ACTION_SCREEN_OFF)
    }

    private val screenBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_ON -> onScreenTurnedOn()
                Intent.ACTION_SCREEN_OFF -> onScreenTurnedOff()
            }
        }
    }

    override fun onListenerConnected() {
        adbConnected = activeNotifications.any(::isAdbConnectedNotification)
    }

    override fun onListenerDisconnected() {
        leaveScreenAlone()
    }

    override fun onNotificationPosted(statusBarNotification: StatusBarNotification) {
        if (isAdbConnectedNotification(statusBarNotification)) {
            adbConnected = true
        }
    }

    override fun onNotificationRemoved(statusBarNotification: StatusBarNotification) {
        if (isAdbConnectedNotification(statusBarNotification)) {
            adbConnected = false
        }
    }

    private fun onScreenTurnedOn() {
        acquireWakelock()
    }

    private fun onScreenTurnedOff() {
        releaseWakelock()
    }

    private fun isAdbConnectedNotification(statusBarNotification: StatusBarNotification): Boolean {
        return with(statusBarNotification) {
            packageName == "android" && notification.channelId == "DEVELOPER_IMPORTANT"
        }
    }

    private fun keepScreenAwake() {
        acquireWakelock()
        registerScreenBroadcastReceiver()
    }

    @SuppressLint("WakelockTimeout")
    private fun acquireWakelock() {
        if (wakeLock != null) return

        println("Acquiring full wake lock")

        val powerManager = getSystemService<PowerManager>() ?: error("Error retrieving PowerManager")

        val wakeLock = powerManager.newWakeLock(
            PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "de.cketti.adbscreenawake:Full"
        )
        wakeLock.acquire()

        this.wakeLock = wakeLock
    }

    private fun registerScreenBroadcastReceiver() {
        registerReceiver(screenBroadcastReceiver, intentFilter)
    }

    private fun leaveScreenAlone() {
        unregisterScreenBroadcastReceiver()
        releaseWakelock()
    }

    private fun unregisterScreenBroadcastReceiver() {
        unregisterReceiver(screenBroadcastReceiver)
    }

    private fun releaseWakelock() {
        println("Releasing wake lock")
        wakeLock?.release()
        wakeLock = null
    }
}
