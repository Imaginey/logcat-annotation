package com.neusoft.operlog.plugin

import com.android.build.api.instrumentation.FramesComputationMode
import com.android.build.api.instrumentation.InstrumentationScope
import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.gradle.BaseExtension
import com.neusoft.operlog.agp.legacy.OperLogLegacyTransform
import com.neusoft.operlog.agp.modern.OperLogModernAsmFactory
import com.neusoft.operlog.bytecode.OperLogConfigParams
import org.gradle.api.Plugin
import org.gradle.api.Project

class OperLogGradlePlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val extension = project.extensions.create("operLog", OperLogExtension::class.java)

        project.plugins.withId("com.android.application") {
            configureAndroidPlugin(project, extension)
        }

        project.plugins.withId("com.android.library") {
            configureAndroidPlugin(project, extension)
        }
    }

    private fun configureAndroidPlugin(project: Project, extension: OperLogExtension) {
        val androidComponents = project.extensions.findByType(AndroidComponentsExtension::class.java)

        if (androidComponents != null && supportsInstrumentationApi()) {
            println("[OperLog] Modern AGP Component API (AGP >= 7.2.0) detected. Registering Modern AGP Adapter.")
            androidComponents.onVariants { variant ->
                val isRelease = variant.buildType == "release"
                if (isRelease && !extension.enableInRelease) {
                    println("[OperLog] Skipping bytecode instrumentation for release variant '${variant.name}' (enableInRelease=false)")
                    return@onVariants
                }

                if (!extension.enabled) {
                    println("[OperLog] OperLog disabled globally via extension (enabled=false)")
                    return@onVariants
                }

                variant.instrumentation.transformClassesWith(
                    OperLogModernAsmFactory::class.java,
                    InstrumentationScope.PROJECT
                ) { params ->
                    params.enabled.set(extension.enabled)
                    params.includePackages.set(extension.includePackages)
                    params.excludePackages.set(extension.excludePackages)
                    params.printArgs.set(extension.printArgs)
                    params.printThread.set(extension.printThread)
                    params.printResult.set(extension.printResult)
                    params.measureTime.set(extension.measureTime)
                }

                variant.instrumentation.setAsmFramesComputationMode(
                    FramesComputationMode.COMPUTE_FRAMES_FOR_INSTRUMENTED_METHODS
                )
            }
        } else {
            println("[OperLog] Legacy AGP detected (AGP < 7.2.0). Registering Legacy Transform API pipeline.")
            val baseExtension = project.extensions.findByType(BaseExtension::class.java)
            if (baseExtension != null) {
                val config = OperLogConfigParams(
                    enabled = extension.enabled,
                    includePackages = extension.includePackages,
                    excludePackages = extension.excludePackages,
                    printArgs = extension.printArgs,
                    printThread = extension.printThread,
                    printResult = extension.printResult,
                    measureTime = extension.measureTime
                )
                baseExtension.registerTransform(OperLogLegacyTransform(config))
            } else {
                println("[OperLog] Warning: BaseExtension not found. Legacy AGP transform registration skipped.")
            }
        }
    }

    private fun supportsInstrumentationApi(): Boolean {
        return try {
            val variantClass = Class.forName("com.android.build.api.variant.Variant")
            variantClass.getMethod("getInstrumentation")
            true
        } catch (e: Throwable) {
            false
        }
    }
}
