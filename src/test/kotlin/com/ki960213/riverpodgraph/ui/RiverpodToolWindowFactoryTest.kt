package com.ki960213.riverpodgraph.ui

import com.ki960213.riverpodgraph.index.RiverpodProviderIndexValue
import com.ki960213.riverpodgraph.model.RiverpodProviderKind
import org.junit.Assert.assertEquals
import org.junit.Test

class RiverpodToolWindowFactoryTest {
    @Test
    fun `providerDeclarationsFromIndexValues dedupes by provider path and offset then sorts`() {
        val declarations = RiverpodToolWindowFactory.providerDeclarationsFromIndexValues(
            listOf(
                value(providerName = "zProvider", filePath = "lib/z.dart", textOffset = 4),
                value(providerName = "aProvider", sourceName = "a", filePath = "lib/a.dart", textOffset = 8),
                value(providerName = "aProvider", sourceName = "A", filePath = "lib/a.dart", textOffset = 8),
                value(providerName = "aProvider", filePath = "lib/a.dart", textOffset = 2),
            ),
        )

        assertEquals(
            listOf("aProvider:lib/a.dart:2", "aProvider:lib/a.dart:8", "zProvider:lib/z.dart:4"),
            declarations.map { "${it.providerName}:${it.filePath}:${it.textOffset}" },
        )
    }

    private fun value(
        providerName: String,
        sourceName: String = providerName.removeSuffix("Provider"),
        filePath: String,
        textOffset: Int,
    ): RiverpodProviderIndexValue = RiverpodProviderIndexValue(
        kind = RiverpodProviderKind.FUNCTION,
        sourceName = sourceName,
        providerName = providerName,
        generatedSuperclassName = null,
        returnType = "String",
        familySignature = "",
        keepAlive = false,
        isPrivate = false,
        filePath = filePath,
        textOffset = textOffset,
        line = 1,
    )
}
