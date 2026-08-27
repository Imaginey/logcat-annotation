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
}

class TestLogPrinter : OperLogPrinter {
    val enterLogs = mutableListOf<String>()
    val exitLogs = mutableListOf<String>()
    val errorLogs = mutableListOf<String>()

    fun clear() {
        enterLogs.clear()
        exitLogs.clear()
        errorLogs.clear()
    }

    override fun printEnter(tag: String, message: String) {
        enterLogs.add("[$tag] $message")
    }

    override fun printExit(tag: String, message: String) {
        exitLogs.add("[$tag] $message")
    }

    override fun printError(tag: String, message: String, throwable: Throwable?) {
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

        println("\n=======================================================")
        println("  ALL OPERLOG BYTECODE CORE TESTS PASSED SUCCESSFULLY! ")
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
