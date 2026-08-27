package com.neusoft.operlog.runtime

/**
 * Runtime configuration for OperLog.
 */
object OperLogConfig {
    @Volatile
    var enabled: Boolean = true

    @Volatile
    var defaultTag: String = "OperLog"

    @Volatile
    var maxValueLength: Int = 500

    @Volatile
    var maxCollectionSize: Int = 20

    @Volatile
    var maxArraySize: Int = 20

    @Volatile
    var maxMapSize: Int = 20

    @Volatile
    var sensitiveMask: String = "***"

    @Volatile
    var customPrinter: OperLogPrinter? = null
}
