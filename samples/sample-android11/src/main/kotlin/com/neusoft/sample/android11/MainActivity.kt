package com.neusoft.sample.android11

import android.app.Activity
import android.os.Bundle
import com.neusoft.operlog.annotation.OperLog

class MainActivity : Activity() {

    private val bluetoothController = BluetoothController()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runDemoWork()
    }

    @OperLog(tag = "DemoApp")
    private fun runDemoWork() {
        bluetoothController.connectBluetooth("AA:BB:CC:DD:EE:FF", 3)
        bluetoothController.authenticateUser("driver01", "superSecretPass")

        try {
            bluetoothController.setHvacTemperature(35)
        } catch (e: Exception) {
            // Handled
        }
    }
}
