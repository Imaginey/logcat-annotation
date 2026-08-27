package com.neusoft.operlog.bytecode

import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

/**
 * ASM ClassVisitor responsible for identifying @OperLog annotated methods and instrumenting them.
 */
class OperLogClassVisitor(
    api: Int,
    cv: ClassVisitor,
    private val config: OperLogConfigParams
) : ClassVisitor(api, cv) {

    private var currentClassName: String = ""

    override fun visit(
        version: Int,
        access: Int,
        name: String,
        signature: String?,
        superName: String?,
        interfaces: Array<out String>?
    ) {
        currentClassName = name.replace('/', '.')
        super.visit(version, access, name, signature, superName, interfaces)
    }

    override fun visitMethod(
        access: Int,
        name: String,
        descriptor: String,
        signature: String?,
        exceptions: Array<out String>?
    ): MethodVisitor {
        val mv = super.visitMethod(access, name, descriptor, signature, exceptions)

        if (!ClassFilter.isTargetClass(currentClassName, config)) {
            return mv
        }

        if (MethodFilter.isFilterableMethod(access, name, descriptor)) {
            return mv
        }

        return OperLogMethodAnnotationScanner(
            api = api,
            parentMv = mv,
            access = access,
            name = name,
            descriptor = descriptor,
            className = currentClassName,
            config = config
        )
    }

    private class OperLogMethodAnnotationScanner(
        private val api: Int,
        private val parentMv: MethodVisitor,
        private val access: Int,
        private val name: String,
        private val descriptor: String,
        private val className: String,
        private val config: OperLogConfigParams
    ) : MethodVisitor(api, parentMv) {

        private var isOperLogAnnotated = false
        private var tag: String = ""
        private var printArgs: Boolean = config.printArgs
        private var printResult: Boolean = config.printResult
        private var printThread: Boolean = config.printThread
        private var measureTime: Boolean = config.measureTime

        private val paramNames = mutableListOf<String>()
        private val ignoredParamIndexes = mutableListOf<Int>()

        override fun visitParameter(name: String?, access: Int) {
            if (name != null) {
                paramNames.add(name)
            }
            super.visitParameter(name, access)
        }

        override fun visitParameterAnnotation(
            parameter: Int,
            descriptor: String?,
            visible: Boolean
        ): AnnotationVisitor? {
            val av = super.visitParameterAnnotation(parameter, descriptor, visible)
            if (descriptor == "Lcom/neusoft/operlog/annotation/OperLogIgnore;") {
                ignoredParamIndexes.add(parameter)
            }
            return av
        }

        override fun visitAnnotation(descriptor: String?, visible: Boolean): AnnotationVisitor? {
            val av = super.visitAnnotation(descriptor, visible)
            if (descriptor == "Lcom/neusoft/operlog/annotation/OperLog;") {
                isOperLogAnnotated = true

                if (MethodFilter.isSuspendMethod(this.descriptor)) {
                    println("OperLog Warning: Skipping suspend method '$name' in class '$className'. Suspend functions are not fully supported in V1.")
                }

                return object : AnnotationVisitor(api, av) {
                    override fun visit(name: String?, value: Any?) {
                        when (name) {
                            "tag" -> tag = value as? String ?: ""
                            "printArgs" -> printArgs = value as? Boolean ?: config.printArgs
                            "printResult" -> printResult = value as? Boolean ?: config.printResult
                            "printThread" -> printThread = value as? Boolean ?: config.printThread
                            "measureTime" -> measureTime = value as? Boolean ?: config.measureTime
                        }
                        super.visit(name, value)
                    }
                }
            }
            return av
        }

        override fun visitCode() {
            if (isOperLogAnnotated && !MethodFilter.isSuspendMethod(descriptor)) {
                val annotationData = AnnotationData(
                    tag = tag,
                    printArgs = printArgs,
                    printResult = printResult,
                    printThread = printThread,
                    measureTime = measureTime
                )

                val instrumentor = OperLogMethodVisitor(
                    api = api,
                    mv = parentMv,
                    access = access,
                    methodName = name,
                    methodDesc = descriptor,
                    className = className,
                    annotationData = annotationData,
                    config = config,
                    paramNames = paramNames,
                    ignoredParamIndexes = ignoredParamIndexes
                )

                // Re-route visitCode and method contents through the instrumentor
                this.mv = instrumentor
            }
            super.visitCode()
        }
    }
}
