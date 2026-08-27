package com.neusoft.operlog.runtime

import java.util.Locale

/**
 * Main runtime entry point called directly by ASM instrumented bytecode.
 */
object OperLogRuntime {

    private val defaultPrinter: OperLogPrinter = DefaultOperLogPrinter()

    private fun getPrinter(): OperLogPrinter {
        return OperLogConfig.customPrinter ?: defaultPrinter
    }

    private fun resolveTag(tag: String?, className: String): String {
        if (!tag.isNullOrEmpty()) {
            return tag
        }
        val simpleName = className.substringAfterLast('.').substringAfterLast('$')
        return if (simpleName.isNotEmpty()) simpleName else OperLogConfig.defaultTag
    }

    @JvmStatic
    fun enter(
        className: String,
        methodName: String,
        tag: String?,
        parameterNames: Array<String>?,
        parameterValues: Array<Any?>?,
        ignoredParameterIndexes: IntArray?,
        printArgs: Boolean,
        printThread: Boolean
    ): Long {
        if (!OperLogConfig.enabled) {
            return 0L
        }

        val startTime = System.nanoTime()
        val logTag = resolveTag(tag, className)
        val simpleClassName = className.substringAfterLast('.')

        val sb = StringBuilder()
        sb.append("→ ENTER ").append(simpleClassName).append("#").append(methodName)

        if (printThread) {
            sb.append("\n  thread=").append(Thread.currentThread().name)
        }

        if (printArgs && parameterValues != null && parameterValues.isNotEmpty()) {
            val formattedArgs = OperLogFormatter.formatArgs(parameterNames, parameterValues, ignoredParameterIndexes)
            if (formattedArgs.isNotEmpty()) {
                sb.append("\n  args: ").append(formattedArgs)
            }
        }

        getPrinter().printEnter(logTag, sb.toString())
        return startTime
    }

    @JvmStatic
    fun exit(
        className: String,
        methodName: String,
        tag: String?,
        startTime: Long,
        result: Any?,
        printResult: Boolean,
        measureTime: Boolean
    ) {
        if (!OperLogConfig.enabled) {
            return
        }

        val logTag = resolveTag(tag, className)
        val simpleClassName = className.substringAfterLast('.')

        val sb = StringBuilder()
        sb.append("← EXIT ").append(simpleClassName).append("#").append(methodName)

        if (measureTime && startTime > 0L) {
            val costMs = (System.nanoTime() - startTime) / 1_000_000.0
            sb.append(String.format(Locale.US, " cost=%.2fms", costMs))
        }

        if (printResult) {
            sb.append("\n  result=").append(OperLogFormatter.formatValue(result))
        }

        getPrinter().printExit(logTag, sb.toString())
    }

    @JvmStatic
    fun error(
        className: String,
        methodName: String,
        tag: String?,
        startTime: Long,
        throwable: Throwable,
        measureTime: Boolean
    ) {
        if (!OperLogConfig.enabled) {
            return
        }

        val logTag = resolveTag(tag, className)
        val simpleClassName = className.substringAfterLast('.')

        val sb = StringBuilder()
        sb.append("✕ ERROR ").append(simpleClassName).append("#").append(methodName)

        if (measureTime && startTime > 0L) {
            val costMs = (System.nanoTime() - startTime) / 1_000_000.0
            sb.append(String.format(Locale.US, " cost=%.2fms", costMs))
        }

        sb.append("\n  exception=").append(throwable.javaClass.name)
        if (!throwable.message.isNullOrEmpty()) {
            sb.append(": ").append(throwable.message)
        }

        getPrinter().printError(logTag, sb.toString(), throwable)
    }
}
