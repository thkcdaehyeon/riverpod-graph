package com.ki960213.riverpodgraph.index

import com.ki960213.riverpodgraph.model.RiverpodProviderKind
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

class RiverpodProviderIndexValueTest : StringSpec({
    "externalizer가 인덱스 값을 왕복 직렬화한다" {
        val value = RiverpodProviderIndexValue(
            kind = RiverpodProviderKind.NOTIFIER_CLASS,
            sourceName = "UserController",
            providerName = "userControllerProvider",
            generatedSuperclassName = $$"_$UserController",
            returnType = "User",
            familySignature = "String id",
            keepAlive = true,
            isPrivate = false,
            filePath = "lib/user.dart",
            textOffset = 42,
            line = 7,
            textEndOffset = 88,
        )

        val bytes = ByteArrayOutputStream()
        RiverpodProviderIndexValue.Externalizer.save(DataOutputStream(bytes), value)

        val restored = RiverpodProviderIndexValue.Externalizer.read(
            DataInputStream(ByteArrayInputStream(bytes.toByteArray())),
        )

        (restored) shouldBe value
    }
})
