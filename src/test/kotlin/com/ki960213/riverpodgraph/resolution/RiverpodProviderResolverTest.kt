package com.ki960213.riverpodgraph.resolution

import com.ki960213.riverpodgraph.index.RiverpodProviderIndexValue
import com.ki960213.riverpodgraph.model.RiverpodProviderKind
import org.junit.Assert.assertEquals
import org.junit.Test

class RiverpodProviderResolverTest {
    @Test
    fun `sorts duplicate declarations by file path then offset`() {
        val sorted = sortProviderIndexValues(
            listOf(
                value(sourceName = "ZUser", filePath = "lib/z_user.dart", textOffset = 3),
                value(sourceName = "AUserLate", filePath = "lib/a_user.dart", textOffset = 42),
                value(sourceName = "AUserEarly", filePath = "lib/a_user.dart", textOffset = 7),
            ),
        )

        assertEquals(
            listOf("AUserEarly", "AUserLate", "ZUser"),
            sorted.map { it.sourceName },
        )
    }

    private fun value(
        sourceName: String,
        filePath: String,
        textOffset: Int,
    ): RiverpodProviderIndexValue = RiverpodProviderIndexValue(
        kind = RiverpodProviderKind.FUNCTION,
        sourceName = sourceName,
        providerName = "userProvider",
        generatedSuperclassName = null,
        returnType = "User",
        familySignature = "",
        keepAlive = false,
        isPrivate = false,
        filePath = filePath,
        textOffset = textOffset,
        line = 1,
    )
}
