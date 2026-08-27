package com.neusoft.operlog.plugin

open class OperLogExtension {
    var enabled: Boolean = true
    var enableInRelease: Boolean = false
    var includePackages: List<String> = mutableListOf()
    var excludePackages: List<String> = mutableListOf()
    var printArgs: Boolean = true
    var printThread: Boolean = true
    var printResult: Boolean = false
    var measureTime: Boolean = true
}
