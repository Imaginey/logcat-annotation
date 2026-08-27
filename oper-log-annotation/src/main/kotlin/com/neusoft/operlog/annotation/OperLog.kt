package com.neusoft.operlog.annotation

/**
 * Indicates that the annotated function/method should be logged automatically via OperLog.
 *
 * @param tag Custom log tag. If empty, the simple class name or default tag will be used.
 * @param printArgs Whether to log method parameters on entry. Defaults to true.
 * @param printResult Whether to log method return value on exit. Defaults to false.
 * @param printThread Whether to log thread name on entry. Defaults to true.
 * @param measureTime Whether to measure and log execution duration. Defaults to true.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class OperLog(
    val tag: String = "",
    val printArgs: Boolean = true,
    val printResult: Boolean = false,
    val printThread: Boolean = true,
    val measureTime: Boolean = true
)
