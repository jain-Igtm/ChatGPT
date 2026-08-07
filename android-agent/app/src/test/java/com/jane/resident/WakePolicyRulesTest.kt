package com.jane.resident

import android.os.PowerManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WakePolicyRulesTest {
    @Test
    fun sleepsUnderSevereThermalPressure() {
        val decision = WakePolicyRules.decide(
            WakeEnvironment(
                batteryPercent = 90,
                charging = true,
                thermalStatus = PowerManager.THERMAL_STATUS_SEVERE,
            ),
        )
        assertFalse(decision.canRun)
    }

    @Test
    fun sleepsOnCriticalBatteryWhenUnplugged() {
        val decision = WakePolicyRules.decide(
            WakeEnvironment(
                batteryPercent = 8,
                charging = false,
                thermalStatus = PowerManager.THERMAL_STATUS_NONE,
            ),
        )
        assertFalse(decision.canRun)
    }

    @Test
    fun wakesWhenResourcesAreAvailable() {
        val decision = WakePolicyRules.decide(
            WakeEnvironment(
                batteryPercent = 40,
                charging = false,
                thermalStatus = PowerManager.THERMAL_STATUS_LIGHT,
            ),
        )
        assertTrue(decision.canRun)
    }
}
