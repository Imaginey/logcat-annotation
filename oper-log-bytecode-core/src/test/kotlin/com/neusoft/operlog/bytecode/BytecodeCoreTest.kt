package com.neusoft.operlog.bytecode

import com.neusoft.operlog.annotation.OperLog
import com.neusoft.operlog.annotation.OperLogIgnore
import com.neusoft.operlog.runtime.OperLogConfig
import com.neusoft.operlog.runtime.OperLogPrinter
import org.objectweb.asm.ClassReader
import org.objectweb.asm.util.CheckClassAdapter
import java.io.PrintWriter
import java.io.StringWriter
import java.lang.reflect.InvocationTargetException

// Dummy target class for ASM transformation testing
class SampleTarget {

    @OperLog(tag = "TestTag", printArgs = true, printResult = true, measureTime = true)
    fun addNumbers(a: Int, b: Int): Int {
        return a + b
    }

    @OperLog(tag = "SensitiveTag", printArgs = true, printResult = true)
    fun login(user: String, @OperLogIgnore pass: String): String {
        return "Token-$user"
    }

    @OperLog(tag = "ErrorTag")
    fun throwError(msg: String): String {
        throw IllegalStateException(msg)
    }

    @OperLog(tag = "MultiReturnTag", printResult = true)
    fun calculate(val1: Double, flag: Boolean): String {
        if (flag) {
            return "Positive-$val1"
        }
        return "Negative-$val1"
    }

    @OperLog(tag = "NoArgsTag", printArgs = false, measureTime = false)
    fun noArgsAndNoTime(x: Int, y: Int): Long {
        return (x + y).toLong()
    }

    @OperLog(tag = "VoidTag")
    fun doNothing(action: String) {
        // Void return method
    }

    @OperLog(tag = "FaultyParamTag", printArgs = true)
    fun processFaulty(obj: Any): String {
        return "Processed-Success"
    }
}

class FaultyParamObject {
    override fun toString(): String {
        throw RuntimeException("Param toString exploded")
    }
}

class TestLogPrinter : OperLogPrinter {
    val enterLogs = mutableListOf<String>()
    val exitLogs = mutableListOf<String>()
    val errorLogs = mutableListOf<String>()

    var throwOnEnter: Boolean = false
    var throwOnExit: Boolean = false
    var throwOnError: Boolean = false

    fun clear() {
        enterLogs.clear()
        exitLogs.clear()
        errorLogs.clear()
        throwOnEnter = false
        throwOnExit = false
        throwOnError = false
    }

    override fun printEnter(tag: String, message: String) {
        if (throwOnEnter) {
            throw RuntimeException("Intentional printEnter exception")
        }
        enterLogs.add("[$tag] $message")
    }

    override fun printExit(tag: String, message: String) {
        if (throwOnExit) {
            throw RuntimeException("Intentional printExit exception")
        }
        exitLogs.add("[$tag] $message")
    }

    override fun printError(tag: String, message: String, throwable: Throwable?) {
        if (throwOnError) {
            throw RuntimeException("Intentional printError exception")
        }
        errorLogs.add("[$tag] $message (throwable=${throwable?.javaClass?.simpleName})")
    }
}

class CustomClassLoader(parent: ClassLoader) : ClassLoader(parent) {
    fun defineClassFromBytes(name: String, bytes: ByteArray): Class<*> {
        return defineClass(name, bytes, 0, bytes.size)
    }
}

class BytecodeCoreTest {

    private val printer = TestLogPrinter()

    fun setUp() {
        OperLogConfig.enabled = true
        OperLogConfig.customPrinter = printer
        printer.clear()
    }

    fun testBytecodeTransformationAndClassValidation() {
        println("[BytecodeCoreTest] Starting ASM transformation and validation tests...")
        val className = SampleTarget::class.java.name
        val classAsPath = className.replace('.', '/') + ".class"
        val inputStream = ClassLoader.getSystemResourceAsStream(classAsPath)
            ?: javaClass.classLoader.getResourceAsStream(classAsPath)
            ?: fail("Could not find class file for $className")

        val originalBytes = inputStream.readBytes()

        // 1. Transform class bytes
        val transformedBytes = OperLogBytecodeTransformer.transform(
            originalBytes,
            OperLogConfigParams(
                enabled = true,
                includePackages = listOf("com.neusoft.operlog")
            )
        )

        assertTrue(transformedBytes.isNotEmpty(), "Transformed bytes should not be empty")

        // 2. Validate bytecode with ASM CheckClassAdapter
        val reader = ClassReader(transformedBytes)
        val sw = StringWriter()
        val pw = PrintWriter(sw)
        CheckClassAdapter.verify(reader, false, pw)
        val verifyOutput = sw.toString()
        assertTrue(verifyOutput.isEmpty(), "Bytecode verification error: $verifyOutput")
        println("  ✓ ASM CheckClassAdapter bytecode verification PASSED")

        // 3. Load transformed class into custom ClassLoader and invoke methods dynamically
        val loader = CustomClassLoader(javaClass.classLoader)
        val transformedClass = loader.defineClassFromBytes(className, transformedBytes)
        val instance = transformedClass.getDeclaredConstructor().newInstance()

        // Test Method 1: addNumbers
        val addMethod = transformedClass.getMethod("addNumbers", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
        val addResult = addMethod.invoke(instance, 10, 20) as Int
        assertEquals(30, addResult, "addNumbers result mismatch")

        assertEquals(1, printer.enterLogs.size, "Enter log count mismatch")
        assertTrue(printer.enterLogs[0].contains("→ ENTER SampleTarget#addNumbers"), "Enter log text mismatch")
        assertTrue(
            printer.enterLogs[0].contains("a=10, b=20") || printer.enterLogs[0].contains("arg0=10, arg1=20"),
            "Args format mismatch: ${printer.enterLogs[0]}"
        )

        assertEquals(1, printer.exitLogs.size, "Exit log count mismatch")
        assertTrue(printer.exitLogs[0].contains("← EXIT SampleTarget#addNumbers"), "Exit log text mismatch")
        assertTrue(printer.exitLogs[0].contains("result=30"), "Result format mismatch")
        println("  ✓ Method 1 (addNumbers) ENTER/EXIT & primitive boxing test PASSED")

        printer.clear()

        // Test Method 2: login (@OperLogIgnore sensitive parameter)
        val loginMethod = transformedClass.getMethod("login", String::class.java, String::class.java)
        val loginResult = loginMethod.invoke(instance, "admin", "secret123") as String
        assertEquals("Token-admin", loginResult, "login result mismatch")

        assertEquals(1, printer.enterLogs.size, "Enter log count mismatch")
        assertTrue(
            printer.enterLogs[0].contains("user=\"admin\", pass=***") || printer.enterLogs[0].contains("arg0=\"admin\", arg1=***"),
            "@OperLogIgnore masking failed: ${printer.enterLogs[0]}"
        )
        println("  ✓ Method 2 (login) @OperLogIgnore sensitive parameter masking test PASSED")

        printer.clear()

        // Test Method 3: throwError (exception logging and rethrow)
        val errorMethod = transformedClass.getMethod("throwError", String::class.java)
        try {
            errorMethod.invoke(instance, "bluetooth failed")
            fail("Expected InvocationTargetException wrapping IllegalStateException")
        } catch (e: InvocationTargetException) {
            assertTrue(e.targetException is IllegalStateException, "Target exception should be IllegalStateException")
            assertEquals("bluetooth failed", e.targetException.message, "Exception message mismatch")
        }

        assertEquals(1, printer.enterLogs.size, "Enter log count mismatch")
        assertEquals(0, printer.exitLogs.size, "Exit log should not be called on error")
        assertEquals(1, printer.errorLogs.size, "Error log count mismatch")
        assertTrue(printer.errorLogs[0].contains("✕ ERROR SampleTarget#throwError"), "Error log text mismatch")
        assertTrue(printer.errorLogs[0].contains("IllegalStateException: bluetooth failed"), "Exception format mismatch")
        println("  ✓ Method 3 (throwError) exception logging & rethrow test PASSED")

        printer.clear()

        // Test Method 4: calculate (multi-return)
        val calcMethod = transformedClass.getMethod("calculate", Double::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)
        val calcResult = calcMethod.invoke(instance, 45.5, true) as String
        assertEquals("Positive-45.5", calcResult, "calculate result mismatch")

        assertEquals(1, printer.enterLogs.size, "Enter log count mismatch")
        assertEquals(1, printer.exitLogs.size, "Exit log count mismatch")
        assertTrue(printer.exitLogs[0].contains("result=\"Positive-45.5\""), "Multi-return result format mismatch")
        println("  ✓ Method 4 (calculate) multi-return test PASSED")

        printer.clear()

        // Test Method 5: noArgsAndNoTime (printArgs=false, measureTime=false)
        val noArgsMethod = transformedClass.getMethod("noArgsAndNoTime", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
        val noArgsResult = noArgsMethod.invoke(instance, 100, 200) as Long
        assertEquals(300L, noArgsResult, "noArgsAndNoTime result mismatch")

        assertEquals(1, printer.enterLogs.size, "Enter log count mismatch")
        assertTrue(!printer.enterLogs[0].contains("args:"), "Enter log should not contain args when printArgs=false")
        assertEquals(1, printer.exitLogs.size, "Exit log count mismatch")
        assertTrue(!printer.exitLogs[0].contains("cost="), "Exit log should not contain cost when measureTime=false")
        println("  ✓ Method 5 (noArgsAndNoTime) printArgs=false & measureTime=false optimization test PASSED")

        printer.clear()

        // Test Method 6: Fault isolation when Printer throws in printEnter
        printer.throwOnEnter = true
        val addMethodIsolated = transformedClass.getMethod("addNumbers", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
        val addResultIsolated = addMethodIsolated.invoke(instance, 5, 15) as Int
        assertEquals(20, addResultIsolated, "Method should succeed even when printEnter throws")
        println("  ✓ Fault Isolation 1: Printer exception in printEnter does not affect business method execution")

        printer.clear()

        // Test Method 7: Fault isolation when Printer throws in printExit
        printer.throwOnExit = true
        val addResultExitIsolated = addMethodIsolated.invoke(instance, 7, 8) as Int
        assertEquals(15, addResultExitIsolated, "Method should return original value even when printExit throws")
        println("  ✓ Fault Isolation 2: Printer exception in printExit does not affect business return value")

        printer.clear()

        // Test Method 8: Fault isolation when Printer throws in printError
        printer.throwOnError = true
        try {
            errorMethod.invoke(instance, "error with faulty printer")
            fail("Expected business exception to be thrown")
        } catch (e: InvocationTargetException) {
            assertTrue(e.targetException is IllegalStateException, "Original business exception must not be replaced")
            assertEquals("error with faulty printer", e.targetException.message, "Original exception message must be preserved")
        }
        println("  ✓ Fault Isolation 3: Printer exception in printError does not replace original business exception")

        printer.clear()

        // Test Method 9: Faulty parameter toString() does not break business execution
        val faultyMethod = transformedClass.getMethod("processFaulty", Any::class.java)
        val faultyObj = FaultyParamObject()
        val faultyResult = faultyMethod.invoke(instance, faultyObj) as String
        assertEquals("Processed-Success", faultyResult, "Business method must execute normally with faulty toString param")
        assertEquals(1, printer.enterLogs.size, "Enter log count mismatch")
        println("  ✓ Fault Isolation 4: Parameter with faulty toString() does not break business method execution")

        printer.clear()

        // Test Method 10: OperLogConfig.enabled = false
        OperLogConfig.enabled = false
        val addResultDisabled = addMethod.invoke(instance, 1, 2) as Int
        assertEquals(3, addResultDisabled, "Method must execute normally when OperLogConfig.enabled=false")
        assertEquals(0, printer.enterLogs.size, "No enter logs when disabled")
        assertEquals(0, printer.exitLogs.size, "No exit logs when disabled")
        OperLogConfig.enabled = true
        println("  ✓ OperLogConfig.enabled=false runtime disable test PASSED")

        // Test Package Boundary Matching
        val configParams = OperLogConfigParams(
            enabled = true,
            includePackages = listOf("com.neusoft.sample")
        )
        assertTrue(ClassFilter.isTargetClass("com.neusoft.sample.MyController", configParams), "Should match exact sub-package")
        assertTrue(ClassFilter.isTargetClass("com.neusoft.sample.sub.MyController", configParams), "Should match deep sub-package")
        assertTrue(!ClassFilter.isTargetClass("com.neusoft.sampleapp.MyController", configParams), "Must NOT match prefix-only name com.neusoft.sampleapp")
        assertTrue(!ClassFilter.isTargetClass("com.neusoft.samples.MyController", configParams), "Must NOT match prefix-only name com.neusoft.samples")
        println("  ✓ ClassFilter exact package boundary matching test PASSED")

        println("\n=======================================================")
        println("  ALL OPERLOG BYTECODE CORE & FAULT ISOLATION TESTS PASSED! ")
        println("=======================================================\n")
    }

    private fun assertTrue(condition: Boolean, message: String) {
        if (!condition) {
            throw AssertionError("Assertion failed: $message")
        }
    }

    private fun assertEquals(expected: Any?, actual: Any?, message: String = "") {
        if (expected != actual) {
            throw AssertionError("Assertion failed: expected <$expected> but was <$actual>. $message")
        }
    }

    private fun fail(message: String): Nothing {
        throw AssertionError("Test failed: $message")
    }
}

fun main() {
    val test = BytecodeCoreTest()
    test.setUp()
    test.testBytecodeTransformationAndClassValidation()
}
