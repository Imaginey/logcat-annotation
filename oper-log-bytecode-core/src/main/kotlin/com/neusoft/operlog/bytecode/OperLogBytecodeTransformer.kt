package com.neusoft.operlog.bytecode

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes

/**
 * Custom ClassWriter that overrides getCommonSuperClass to prevent ClassNotFoundException
 * during ASM frame computation when analyzing third-party or Android SDK classes.
 */
class SafeClassWriter(
    reader: ClassReader,
    flags: Int
) : ClassWriter(reader, flags) {

    override fun getCommonSuperClass(type1: String, type2: String): String {
        return try {
            super.getCommonSuperClass(type1, type2)
        } catch (e: Throwable) {
            // Fallback to java/lang/Object when class resolution fails on plugin ClassLoader
            "java/lang/Object"
        }
    }
}

/**
 * Single ASM Bytecode Transformer entry point.
 */
object OperLogBytecodeTransformer {

    fun transform(
        classBytes: ByteArray,
        config: OperLogConfigParams = OperLogConfigParams()
    ): ByteArray {
        if (!config.enabled) {
            return classBytes
        }

        try {
            val reader = ClassReader(classBytes)
            val className = reader.className.replace('/', '.')

            if (!ClassFilter.isTargetClass(className, config)) {
                return classBytes
            }

            val writer = SafeClassWriter(reader, ClassWriter.COMPUTE_FRAMES)
            val cv = OperLogClassVisitor(Opcodes.ASM9, writer, config)
            reader.accept(cv, ClassReader.EXPAND_FRAMES)

            return writer.toByteArray()
        } catch (t: Throwable) {
            System.err.println("OperLog Error: Failed to transform class bytes: ${t.message}")
            t.printStackTrace()
            return classBytes
        }
    }
}
