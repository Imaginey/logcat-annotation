package com.neusoft.operlog.bytecode

/**
 * Configuration options passed into the bytecode transformer.
 */
data class OperLogConfigParams(
    val enabled: Boolean = true,
    val includePackages: List<String> = emptyList(),
    val excludePackages: List<String> = emptyList(),
    val printArgs: Boolean = true,
    val printThread: Boolean = true,
    val printResult: Boolean = false,
    val measureTime: Boolean = true
)
