package com.neusoft.operlog.annotation

/**
 * Indicates that the annotated parameter contains sensitive information and should be masked in OperLog output.
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.BINARY)
annotation class OperLogIgnore
