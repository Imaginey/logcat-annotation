package com.neusoft.sample.android15

import android.app.Activity
import android.os.Bundle
import com.neusoft.operlog.annotation.OperLog
import com.neusoft.operlog.annotation.OperLogIgnore

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        executeAndroid15Feature("API-35-Engine", "Token-X-Secret")
    }

    @OperLog(tag = "Android15Sample", printArgs = true, printResult = true, measureTime = true)
    fun executeAndroid15Feature(featureName: String, @OperLogIgnore featureToken: String): Map<String, Any> {
        return mapOf("status" to "OK", "feature" to featureName)
    }
}
