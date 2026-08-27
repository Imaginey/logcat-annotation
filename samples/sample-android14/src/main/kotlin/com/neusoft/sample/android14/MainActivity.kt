package com.neusoft.sample.android14

import android.app.Activity
import android.os.Bundle
import com.neusoft.operlog.annotation.OperLog
import com.neusoft.operlog.annotation.OperLogIgnore

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startServiceConnection("Service-A", "SecretKey-999")
    }

    @OperLog(tag = "Android14Sample", printResult = true)
    fun startServiceConnection(serviceName: String, @OperLogIgnore secretKey: String): Boolean {
        println("Android 14 connecting to $serviceName...")
        return true
    }
}
