package com.hypershare.application

import android.app.Application

import com.hypershare.service.LanSocketManager

class HyperShareApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        LanSocketManager.getInstance().init(this)
    }
}
