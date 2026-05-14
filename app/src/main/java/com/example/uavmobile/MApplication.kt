package com.example.uavmobile

import android.app.Application
import android.content.Context
import android.util.Log
import com.cySdkyc.clx.Helper
import com.example.uavmobile.dji.DjiMsdkManager

class MApplication : Application() {
    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        if (DjiMsdkManager.isProbablyVirtualDevice()) {
            Log.i(TAG, "Skipping DJI Helper.install on virtual device")
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
        DjiMsdkManager.init(this)
    }

    companion object {
        private const val TAG = "MApplication"
    }
}
