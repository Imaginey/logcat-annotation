package com.neusoft.operlog.agp.legacy

import com.android.build.api.transform.Format
import com.android.build.api.transform.QualifiedContent
import com.android.build.api.transform.Transform
import com.android.build.api.transform.TransformInvocation
import com.neusoft.operlog.bytecode.OperLogBytecodeTransformer
import com.neusoft.operlog.bytecode.OperLogConfigParams
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class OperLogLegacyTransform(
    private val config: OperLogConfigParams
) : Transform() {

    override fun getName(): String = "OperLogLegacyTransform"

    override fun getInputTypes(): Set<QualifiedContent.ContentType> {
        return setOf(QualifiedContent.DefaultContentType.CLASSES)
    }

    override fun getScopes(): MutableSet<in QualifiedContent.Scope> {
        return mutableSetOf(
            QualifiedContent.Scope.PROJECT,
            QualifiedContent.Scope.SUB_PROJECTS,
            QualifiedContent.Scope.EXTERNAL_LIBRARIES
        )
    }

    override fun isIncremental(): Boolean = false

    override fun transform(transformInvocation: TransformInvocation) {
        super.transform(transformInvocation)

        val outputProvider = transformInvocation.outputProvider
        if (!config.enabled) {
            transformInvocation.inputs.forEach { input ->
                input.directoryInputs.forEach { dir ->
                    val dest = outputProvider.getContentLocation(dir.name, dir.contentTypes, dir.scopes, Format.DIRECTORY)
                    dir.file.copyRecursively(dest, overwrite = true)
                }
                input.jarInputs.forEach { jar ->
                    val dest = outputProvider.getContentLocation(jar.name, jar.contentTypes, jar.scopes, Format.JAR)
                    jar.file.copyTo(dest, overwrite = true)
                }
            }
            return
        }

        transformInvocation.inputs.forEach { input ->
            input.directoryInputs.forEach { dirInput ->
                val destDir = outputProvider.getContentLocation(
                    dirInput.name,
                    dirInput.contentTypes,
                    dirInput.scopes,
                    Format.DIRECTORY
                )

                dirInput.file.walkTopDown().forEach { file ->
                    if (file.isFile) {
                        val relativePath = file.toRelativeString(dirInput.file)
                        val destFile = File(destDir, relativePath)
                        destFile.parentFile.mkdirs()

                        if (file.name.endsWith(".class")) {
                            val originalBytes = file.readBytes()
                            val transformedBytes = OperLogBytecodeTransformer.transform(originalBytes, config)
                            destFile.writeBytes(transformedBytes)
                        } else {
                            file.copyTo(destFile, overwrite = true)
                        }
                    }
                }
            }

            input.jarInputs.forEach { jarInput ->
                val destJar = outputProvider.getContentLocation(
                    jarInput.name,
                    jarInput.contentTypes,
                    jarInput.scopes,
                    Format.JAR
                )
                destJar.parentFile.mkdirs()
                transformJar(jarInput.file, destJar)
            }
        }
    }

    private fun transformJar(inputJar: File, outputJar: File) {
        ZipFile(inputJar).use { zipFile ->
            ZipOutputStream(FileOutputStream(outputJar)).use { zos ->
                val entries = zipFile.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val entryName = entry.name
                    val isClass = entryName.endsWith(".class")

                    val newEntry = ZipEntry(entryName)
                    zos.putNextEntry(newEntry)

                    zipFile.getInputStream(entry).use { inputStream ->
                        val bytes = inputStream.readBytes()
                        if (isClass) {
                            val transformed = OperLogBytecodeTransformer.transform(bytes, config)
                            zos.write(transformed)
                        } else {
                            zos.write(bytes)
                        }
                    }
                    zos.closeEntry()
                }
            }
        }
    }
}
