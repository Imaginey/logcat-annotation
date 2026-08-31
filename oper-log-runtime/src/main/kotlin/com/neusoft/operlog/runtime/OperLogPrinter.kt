package com.neusoft.operlog.runtime

import java.lang.reflect.Method

/**
 * Interface and default implementation for printing OperLog messages.
 */
interface OperLogPrinter {
    fun printEnter(tag: String, message: String)
    fun printExit(tag: String, message: String)
    fun printError(tag: String, message: String, throwable: Throwable?)
}

/**
 * Default Android / Standard printer implementation using reflection fallback.
 */
class DefaultOperLogPrinter : OperLogPrinter {

    private val logClass: Class<*>? = try {
        Class.forName("android.util.Log")
    } catch (t: Throwable) {
        if (t is VirtualMachineError || t is ThreadDeath) throw t
        null
    }

    private val logDMethod: Method? = try {
        logClass?.getMethod("d", String::class.java, String::class.java)
    } catch (t: Throwable) {
        if (t is VirtualMachineError || t is ThreadDeath) throw t
        null
    }

    private val logEMethodTwoArgs: Method? = try {
        logClass?.getMethod("e", String::class.java, String::class.java)
    } catch (t: Throwable) {
        if (t is VirtualMachineError || t is ThreadDeath) throw t
        null
    }

    private val logEMethodThreeArgs: Method? = try {
        logClass?.getMethod("e", String::class.java, String::class.java, Throwable::class.java)
    } catch (t: Throwable) {
        if (t is VirtualMachineError || t is ThreadDeath) throw t
        null
    }

    override fun printEnter(tag: String, message: String) {
        logDebug(tag, message)
    }

    override fun printExit(tag: String, message: String) {
        logDebug(tag, message)
    }

    override fun printError(tag: String, message: String, throwable: Throwable?) {
        logError(tag, message, throwable)
    }

    private fun logDebug(tag: String, message: String) {
        if (logDMethod != null) {
            try {
                logDMethod.invoke(null, tag, message)
                return
            } catch (t: Throwable) {
                if (t is VirtualMachineError || t is ThreadDeath) throw t
            }
        }
        try {
            println("D/$tag: $message")
        } catch (t: Throwable) {
            if (t is VirtualMachineError || t is ThreadDeath) throw t
        }
    }

    private fun logError(tag: String, message: String, throwable: Throwable?) {
        if (logClass != null) {
            try {
                if (throwable != null && logEMethodThreeArgs != null) {
                    logEMethodThreeArgs.invoke(null, tag, message, throwable)
                    return
                } else if (logEMethodTwoArgs != null) {
                    logEMethodTwoArgs.invoke(null, tag, message)
                    return
                }
            } catch (t: Throwable) {
                if (t is VirtualMachineError || t is ThreadDeath) throw t
            }
        }
        try {
            System.err.println("E/$tag: $message")
            throwable?.printStackTrace(System.err)
        } catch (t: Throwable) {
            if (t is VirtualMachineError || t is ThreadDeath) throw t
        }
    }
}
