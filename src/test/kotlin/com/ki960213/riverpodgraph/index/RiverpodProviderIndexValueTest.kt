package com.ki960213.riverpodgraph.index

import com.ki960213.riverpodgraph.model.RiverpodProviderKind
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

class RiverpodProviderIndexValueTest {
    @Test
    fun `externalizer round trips index value`() {
        val value = RiverpodProviderIndexValue(
            kind = RiverpodProviderKind.NOTIFIER_CLASS,
            sourceName = "UserController",
            providerName = "userControllerProvider",
            generatedSuperclassName = "_${'$'}UserController",
            returnType = "User",
            familySignature = "String id",
            keepAlive = true,
            isPrivate = false,
            filePath = "lib/user.dart",
            textOffset = 42,
            line = 7,
        )

        val bytes = ByteArrayOutputStream()
        RiverpodProviderIndexValue.Externalizer.save(DataOutputStream(bytes), value)

        val restored = RiverpodProviderIndexValue.Externalizer.read(
            DataInputStream(ByteArrayInputStream(bytes.toByteArray())),
        )

        assertEquals(value, restored)
    }
}
