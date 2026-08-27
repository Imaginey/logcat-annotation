package com.neusoft.operlog.bytecode

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes

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

            // COMPUTE_FRAMES automatically generates StackMapTable entries required for try-catch blocks
            val writer = ClassWriter(reader, ClassWriter.COMPUTE_FRAMES)
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
