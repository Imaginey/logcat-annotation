package com.neusoft.operlog.bytecode

import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.commons.AdviceAdapter

/**
 * ASM MethodVisitor responsible for method instrumentation with compile-time allocation optimizations.
 */
class OperLogMethodVisitor(
    api: Int,
    mv: MethodVisitor,
    access: Int,
    private val methodName: String,
    private val methodDesc: String,
    private val className: String,
    private val annotationData: AnnotationData,
    private val config: OperLogConfigParams,
    private val paramNames: List<String> = emptyList(),
    private val ignoredParamIndexes: List<Int> = emptyList()
) : AdviceAdapter(api, mv, access, methodName, methodDesc) {

    private var startTimeLocal: Int = -1
    private val startLabel = Label()
    private val endLabel = Label()
    private val handlerLabel = Label()

    private val runtimeType = Type.getObjectType("com/neusoft/operlog/runtime/OperLogRuntime")
    private val stringArrayType = Type.getType("[Ljava/lang/String;")
    private val objectArrayType = Type.getType("[Ljava/lang/Object;")
    private val intArrayType = Type.getType("[I")
    private val objectType = Type.getType("Ljava/lang/Object;")
    private val stringType = Type.getType("Ljava/lang/String;")
    private val throwableType = Type.getType("Ljava/lang/Throwable;")

    override fun onMethodEnter() {
        startTimeLocal = newLocal(Type.LONG_TYPE)

        val printArgs = annotationData.printArgs
        val namesLocal = newLocal(stringArrayType)
        val valuesLocal = newLocal(objectArrayType)
        val ignoredLocal = newLocal(intArrayType)

        if (printArgs) {
            // 1. Generate parameter names array ([Ljava/lang/String;)
            pushArrayOfStrings(paramNames.toTypedArray())
            storeLocal(namesLocal)

            // 2. Generate parameter values array ([Ljava/lang/Object;)
            val argTypes = argumentTypes
            push(argTypes.size)
            newArray(objectType)

            for (i in argTypes.indices) {
                dup()
                push(i)
                loadArg(i)
                box(argTypes[i])
                arrayStore(objectType)
            }
            storeLocal(valuesLocal)

            // 3. Generate ignored parameter indexes array ([I)
            pushArrayOfInts(ignoredParamIndexes.toIntArray())
            storeLocal(ignoredLocal)
        } else {
            // Optimization: Skip array allocation & boxing completely when printArgs=false
            push(null as String?)
            storeLocal(namesLocal)
            push(null as String?)
            storeLocal(valuesLocal)
            push(null as String?)
            storeLocal(ignoredLocal)
        }

        // 4. Push arguments for OperLogRuntime.enter(...)
        push(className)
        push(methodName)
        push(annotationData.tag)
        loadLocal(namesLocal)
        loadLocal(valuesLocal)
        loadLocal(ignoredLocal)
        push(printArgs)
        push(annotationData.printThread)

        invokeStatic(
            runtimeType,
            org.objectweb.asm.commons.Method(
                "enter",
                "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;[Ljava/lang/String;[Ljava/lang/Object;[IZZ)J"
            )
        )

        storeLocal(startTimeLocal)

        // Mark start of try-catch block for exception logging
        visitLabel(startLabel)
    }

    override fun onMethodExit(opcode: Int) {
        if (opcode == ATHROW) {
            return
        }

        val printResult = annotationData.printResult
        val returnType = returnType

        if (printResult && returnType != Type.VOID_TYPE) {
            if (returnType.size == 2) {
                dup2()
            } else {
                dup()
            }
            box(returnType)
        } else {
            push(null as String?)
        }

        val resultLocal = newLocal(objectType)
        storeLocal(resultLocal)

        push(className)
        push(methodName)
        push(annotationData.tag)
        loadLocal(startTimeLocal)
        loadLocal(resultLocal)
        push(printResult)
        push(annotationData.measureTime)

        invokeStatic(
            runtimeType,
            org.objectweb.asm.commons.Method(
                "exit",
                "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;JLjava/lang/Object;ZZ)V"
            )
        )
    }

    override fun visitMaxs(maxStack: Int, maxLocals: Int) {
        // Mark end of try block
        visitLabel(endLabel)

        // Define try-catch block for Throwable
        visitTryCatchBlock(startLabel, endLabel, handlerLabel, "java/lang/Throwable")

        // Handler block for exception logging
        visitLabel(handlerLabel)

        val throwableLocal = newLocal(throwableType)
        storeLocal(throwableLocal)

        push(className)
        push(methodName)
        push(annotationData.tag)
        loadLocal(startTimeLocal)
        loadLocal(throwableLocal)
        push(annotationData.measureTime)

        invokeStatic(
            runtimeType,
            org.objectweb.asm.commons.Method(
                "error",
                "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;JLjava/lang/Throwable;Z)V"
            )
        )

        loadLocal(throwableLocal)
        throwException()

        super.visitMaxs(maxStack, maxLocals)
    }

    private fun pushArrayOfStrings(arr: Array<String>) {
        push(arr.size)
        newArray(stringType)
        for (i in arr.indices) {
            dup()
            push(i)
            push(arr[i])
            arrayStore(stringType)
        }
    }

    private fun pushArrayOfInts(arr: IntArray) {
        push(arr.size)
        newArray(Type.INT_TYPE)
        for (i in arr.indices) {
            dup()
            push(i)
            push(arr[i])
            arrayStore(Type.INT_TYPE)
        }
    }
}
