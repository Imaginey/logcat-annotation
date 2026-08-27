package com.neusoft.sample.android11

import com.neusoft.operlog.annotation.OperLog
import com.neusoft.operlog.annotation.OperLogIgnore

class BluetoothController {

    @OperLog(tag = "Bluetooth", printArgs = true, printResult = true, measureTime = true)
    fun connectBluetooth(address: String, retryCount: Int): Boolean {
        println("Connecting to Bluetooth device at $address (retry: $retryCount)...")
        return true
    }

    @OperLog(tag = "Auth", printArgs = true, printResult = true)
    fun authenticateUser(account: String, @OperLogIgnore passwordSecret: String): String {
        return "SessionToken-$account"
    }

    @OperLog(tag = "HvacError", printArgs = true)
    fun setHvacTemperature(temperature: Int) {
        if (temperature < 16 || temperature > 30) {
            throw IllegalArgumentException("Temperature $temperature is out of valid range (16-30)")
        }
    }
}
