package com.neusoft.operlog.bytecode

/**
 * Parsed data from @OperLog annotation.
 */
data class AnnotationData(
    val tag: String = "",
    val printArgs: Boolean = true,
    val printResult: Boolean = false,
    val printThread: Boolean = true,
    val measureTime: Boolean = true
)
