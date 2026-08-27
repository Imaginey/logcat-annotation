package com.neusoft.operlog.agp.modern

import com.android.build.api.instrumentation.AsmClassVisitorFactory
import com.android.build.api.instrumentation.ClassContext
import com.android.build.api.instrumentation.ClassData
import com.android.build.api.instrumentation.InstrumentationParameters
import com.neusoft.operlog.bytecode.ClassFilter
import com.neusoft.operlog.bytecode.OperLogClassVisitor
import com.neusoft.operlog.bytecode.OperLogConfigParams
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Opcodes

interface OperLogInstrumentationParams : InstrumentationParameters {
    @get:Input
    val enabled: Property<Boolean>

    @get:Input
    val includePackages: ListProperty<String>

    @get:Input
    val excludePackages: ListProperty<String>

    @get:Input
    val printArgs: Property<Boolean>

    @get:Input
    val printThread: Property<Boolean>

    @get:Input
    val printResult: Property<Boolean>

    @get:Input
    val measureTime: Property<Boolean>
}

/**
 * Modern AGP Instrumentation Factory delegating class visitation strictly to oper-log-bytecode-core.
 */
abstract class OperLogModernAsmFactory : AsmClassVisitorFactory<OperLogInstrumentationParams> {

    override fun createClassVisitor(
        classContext: ClassContext,
        nextClassVisitor: ClassVisitor
    ): ClassVisitor {
        val params = parameters.get()
        val config = OperLogConfigParams(
            enabled = params.enabled.getOrElse(true),
            includePackages = params.includePackages.getOrElse(emptyList()),
            excludePackages = params.excludePackages.getOrElse(emptyList()),
            printArgs = params.printArgs.getOrElse(true),
            printThread = params.printThread.getOrElse(true),
            printResult = params.printResult.getOrElse(false),
            measureTime = params.measureTime.getOrElse(true)
        )
        val asmApiVersion = try {
            instrumentationContext.apiVersion.get()
        } catch (t: Throwable) {
            Opcodes.ASM9
        }
        return OperLogClassVisitor(asmApiVersion, nextClassVisitor, config)
    }

    override fun isInstrumentable(classData: ClassData): Boolean {
        val params = parameters.get()
        val config = OperLogConfigParams(
            enabled = params.enabled.getOrElse(true),
            includePackages = params.includePackages.getOrElse(emptyList()),
            excludePackages = params.excludePackages.getOrElse(emptyList())
        )
        return ClassFilter.isTargetClass(classData.className, config)
    }
}
