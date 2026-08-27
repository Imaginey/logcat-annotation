package com.neusoft.operlog.agp.legacy

import com.neusoft.operlog.bytecode.OperLogBytecodeTransformer
import com.neusoft.operlog.bytecode.OperLogConfigParams

/**
 * Legacy AGP Adapter transformer. Receives class input stream/byte array and delegates transformation to oper-log-bytecode-core.
 */
object OperLogLegacyTransformer {

    fun transformClassBytes(
        inputBytes: ByteArray,
        config: OperLogConfigParams
    ): ByteArray {
        return OperLogBytecodeTransformer.transform(inputBytes, config)
    }
}
