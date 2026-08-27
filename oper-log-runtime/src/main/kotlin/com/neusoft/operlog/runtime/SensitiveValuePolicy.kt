package com.neusoft.operlog.runtime

/**
 * Handles sensitive value masking for @OperLogIgnore parameters.
 */
object SensitiveValuePolicy {
    fun mask(value: Any?): String {
        return OperLogConfig.sensitiveMask
    }
}
