package id.syne.locationtracker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Boot completed received")

        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON" -> {

                val prefs = context.getSharedPreferences("location_settings", Context.MODE_PRIVATE)
                val wasTrackingEnabled = prefs.getBoolean("tracking_enabled", false)

                if (wasTrackingEnabled) {
                    Log.d(TAG, "Restarting location tracking service after boot")

                    val serviceIntent = Intent(context, LocationTrackingService::class.java)

                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            context.startForegroundService(serviceIntent)
                        } else {
                            context.startService(serviceIntent)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to start service after boot", e)
                    }
                }
            }
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}