package com.neusoft.operlog.runtime

import java.util.Locale

/**
 * Main runtime entry point called directly by ASM instrumented bytecode.
 * Designed with absolute fault isolation: logging failures never propagate to business code.
 */
object OperLogRuntime {

    private val defaultPrinter: OperLogPrinter = DefaultOperLogPrinter()

    private fun getPrinter(): OperLogPrinter {
        return try {
            OperLogConfig.customPrinter ?: defaultPrinter
        } catch (t: Throwable) {
            if (t is VirtualMachineError || t is ThreadDeath) throw t
            defaultPrinter
        }
    }

    private fun resolveTag(tag: String?, className: String): String {
        return try {
            if (!tag.isNullOrEmpty()) {
                tag
            } else {
                val simpleName = className.substringAfterLast('.').substringAfterLast('$')
                if (simpleName.isNotEmpty()) simpleName else OperLogConfig.defaultTag
            }
        } catch (t: Throwable) {
            if (t is VirtualMachineError || t is ThreadDeath) throw t
            "OperLog"
        }
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
        try {
            if (!OperLogConfig.enabled) {
                return 0L
            }

            val startTime = System.nanoTime()
            val logTag = resolveTag(tag, className)
            val simpleClassName = className.substringAfterLast('.')

            val sb = StringBuilder()
            sb.append("→ ENTER ").append(simpleClassName).append("#").append(methodName)

            if (printThread) {
                try {
                    sb.append("\n  thread=").append(Thread.currentThread().name)
                } catch (t: Throwable) {
                    if (t is VirtualMachineError || t is ThreadDeath) throw t
                }
            }

            if (printArgs && parameterValues != null && parameterValues.isNotEmpty()) {
                val formattedArgs = OperLogFormatter.formatArgs(parameterNames, parameterValues, ignoredParameterIndexes)
                if (formattedArgs.isNotEmpty()) {
                    sb.append("\n  args: ").append(formattedArgs)
                }
            }

            getPrinter().printEnter(logTag, sb.toString())
            return startTime
        } catch (t: Throwable) {
            if (t is VirtualMachineError || t is ThreadDeath) throw t
            // Fault isolation: Silently absorb log failure so business method proceeds normally
            return 0L
        }
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
        try {
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
        } catch (t: Throwable) {
            if (t is VirtualMachineError || t is ThreadDeath) throw t
            // Fault isolation: Silently absorb log failure so business return value is unaffected
        }
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
        try {
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
            val msg = throwable.message
            if (!msg.isNullOrEmpty()) {
                sb.append(": ").append(msg)
            }

            getPrinter().printError(logTag, sb.toString(), throwable)
        } catch (t: Throwable) {
            if (t is VirtualMachineError || t is ThreadDeath) throw t
            // Fault isolation: Silently absorb log failure so original business exception is rethrown intact
        }
    }
}
