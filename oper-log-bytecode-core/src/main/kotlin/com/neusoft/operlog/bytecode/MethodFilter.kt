package com.neusoft.operlog.bytecode

import org.objectweb.asm.Opcodes

/**
 * Filter to determine whether a method should be processed for instrumentation.
 */
object MethodFilter {

    fun isFilterableMethod(
        access: Int,
        name: String,
        descriptor: String
    ): Boolean {
        // Skip constructors and static initializers
        if (name == "<init>" || name == "<clinit>") {
            return true
        }

        // Skip abstract and native methods
        if ((access and Opcodes.ACC_ABSTRACT) != 0 || (access and Opcodes.ACC_NATIVE) != 0) {
            return true
        }

        // Skip synthetic and bridge methods
        if ((access and Opcodes.ACC_SYNTHETIC) != 0 || (access and Opcodes.ACC_BRIDGE) != 0) {
            return true
        }

        // Skip Kotlin compiler generated methods
        if (name.contains("\$default") ||
            name.startsWith("access\$") ||
            name.contains("\$annotations") ||
            name.startsWith("lambda\$")
        ) {
            return true
        }

        return false
    }

    fun isSuspendMethod(descriptor: String): Boolean {
        // Kotlin suspend functions end with Continuation parameter
        return descriptor.contains("Lkotlin/coroutines/Continuation;")
    }
}
