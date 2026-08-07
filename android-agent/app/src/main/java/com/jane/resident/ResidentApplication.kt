package com.jane.resident

import android.app.Application

class ResidentApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AgentRepository(this).initialize()
        AgentScheduler(this).ensureAutonomousWake()
    }
}
