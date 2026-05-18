package com.ki960213.riverpodgraph.test

import com.ki960213.riverpodgraph.activation.RiverpodActivationService
import com.ki960213.riverpodgraph.activation.RiverpodActiveSourceScope

class RiverpodFixtureSanityTest : RiverpodFixtureBase() {
    fun `test basic fixture loads at project root and activates riverpod sources`() {
        loadFixture("riverpod/basic")
        assertNotNull(myFixture.findFileInTempDir("pubspec.yaml"))
        val source = myFixture.configureByFile("lib/providers/user_providers.dart")

        assertEquals("Dart", source.language.id)
        assertTrue(RiverpodActivationService.getInstance(project).isFileActive(source))
        assertTrue(
            RiverpodActiveSourceScope.getInstance(project)
                .providerDeclarations()
                .map { it.providerName }
                .containsAll(listOf("currentUserProvider", "userProvider", "profileProvider")),
        )
    }
}
