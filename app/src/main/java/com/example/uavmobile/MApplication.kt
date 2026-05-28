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
        val runtimeConfig = DjiRuntimeEnvironment.currentConfig()
        val runtimeDecision = DjiRuntimeEnvironment.currentDecision()
        if (runtimeDecision.shouldSkip) {
            Log.i(
                TAG,
                "Skipping DJI Helper.install: ${runtimeDecision.reason ?: "unknown"}; " +
                    DjiRuntimeEnvironment.configSummary(runtimeConfig),
            )
            return
        }

        Log.i(TAG, "MApplication 开始执行 Helper.install; ${DjiRuntimeEnvironment.configSummary(runtimeConfig)}")
        runCatching {
            Helper.install(this)
        }.onFailure { throwable ->
            Log.w(TAG, "DJI Helper.install failed: ${throwable.message}")
        }
    }

    override fun onCreate() {
        super.onCreate()
        val runtimeConfig = DjiRuntimeEnvironment.currentConfig()
        val runtimeDecision = DjiRuntimeEnvironment.currentDecision()
        if (runtimeDecision.shouldSkip) {
            Log.i(
                TAG,
                "DJI runtime will be skipped by DjiMsdkManager: ${runtimeDecision.reason ?: "unknown"}; " +
                    DjiRuntimeEnvironment.configSummary(runtimeConfig),
            )
        }
        Log.i(TAG, "MApplication.onCreate runtime config: ${DjiRuntimeEnvironment.decisionSummary(runtimeConfig, runtimeDecision)}")
        DjiMsdkManager.init(this)
    }

    companion object {
        private const val TAG = "MApplication"
    }
}
