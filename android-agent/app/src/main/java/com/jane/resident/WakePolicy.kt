package com.jane.resident

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager

object WakePolicyRules {
    fun decide(environment: WakeEnvironment): WakeDecision {
        if (environment.thermalStatus >= PowerManager.THERMAL_STATUS_SEVERE) {
            return WakeDecision(false, "device_thermal_pressure")
        }
        if (environment.batteryPercent in 0..10 && !environment.charging) {
            return WakeDecision(false, "battery_critical")
        }
        return WakeDecision(true, "resources_available")
    }
}

class WakePolicy(private val context: Context) {
    fun inspect(): Pair<WakeEnvironment, WakeDecision> {
        val batteryManager = context.getSystemService(BatteryManager::class.java)
        val batteryPercent = batteryManager
            ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            ?.takeIf { it in 0..100 }
            ?: -1

        val batteryIntent = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
        )
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL

        val thermalStatus = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.getSystemService(PowerManager::class.java)?.currentThermalStatus
                ?: PowerManager.THERMAL_STATUS_NONE
        } else {
            PowerManager.THERMAL_STATUS_NONE
        }

        val environment = WakeEnvironment(
            batteryPercent = batteryPercent,
            charging = charging,
            thermalStatus = thermalStatus,
        )
        return environment to WakePolicyRules.decide(environment)
    }
}
