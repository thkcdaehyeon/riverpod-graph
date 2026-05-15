package com.ki960213.riverpodgraph.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RiverpodModelsTest {
    @Test
    fun `provider declaration stores model fields`() {
        val declaration = RiverpodProviderDeclaration(
            kind = RiverpodProviderKind.FUNCTION,
            sourceName = "user",
            providerName = "userProvider",
            generatedSuperclassName = null,
            returnType = "String",
            familySignature = "",
            keepAlive = false,
            isPrivate = false,
            filePath = "lib/user.dart",
            textOffset = 0,
            line = 1,
        )

        assertEquals(RiverpodProviderKind.FUNCTION, declaration.kind)
        assertEquals("String", declaration.returnType)
        assertEquals("", declaration.familySignature)
        assertNull(declaration.generatedSuperclassName)
    }

    @Test
    fun `usage and dependency edge markers default to null`() {
        val usage = RiverpodProviderUsage(
            providerName = "userProvider",
            kind = RiverpodUsageKind.WATCH,
            filePath = "lib/user.dart",
            textOffset = 10,
            line = 2,
        )
        val edge = RiverpodDependencyEdge(
            fromProvider = "profileProvider",
            toProvider = "userProvider",
            usageKind = RiverpodUsageKind.WATCH,
        )

        assertNull(usage.marker)
        assertNull(edge.marker)
    }
}
