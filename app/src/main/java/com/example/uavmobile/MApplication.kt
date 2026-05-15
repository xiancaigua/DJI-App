package com.example.uavmobile

import android.app.Application
import android.content.Context
import android.util.Log
import com.cySdkyc.clx.Helper
import com.example.uavmobile.dji.DjiMsdkManager
import com.example.uavmobile.dji.DjiRuntimeEnvironment

class MApplication : Application() {
    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        val runtimeDecision = DjiRuntimeEnvironment.currentDecision()
        if (runtimeDecision.shouldSkip) {
            Log.i(TAG, runtimeDecision.reason ?: "Skipping DJI Helper.install")
            return
        }

        runCatching {
            Helper.install(this)
        }.onFailure { throwable ->
            Log.w(TAG, "DJI Helper.install failed: ${throwable.message}")
        }
    }

    override fun onCreate() {
        super.onCreate()
        val runtimeDecision = DjiRuntimeEnvironment.currentDecision()
        if (runtimeDecision.shouldSkip) {
            Log.i(TAG, runtimeDecision.reason ?: "Skipping DJI MSDK init")
            return
        }
        DjiMsdkManager.init(this)
    }

    companion object {
        private const val TAG = "MApplication"
    }
}
