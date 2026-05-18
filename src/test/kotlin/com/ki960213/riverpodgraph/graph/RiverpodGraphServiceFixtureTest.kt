package com.ki960213.riverpodgraph.graph

import com.ki960213.riverpodgraph.test.RiverpodFixtureBase

class RiverpodGraphServiceFixtureTest : RiverpodFixtureBase() {
    fun `test provider snapshot reports ready providers from fixture`() {
        loadFixture("riverpod/basic")

        val snapshot = RiverpodGraphService.getInstance(project).providerSnapshot()

        assertEquals(RiverpodProviderLoadStatus.READY, snapshot.status)
        assertTrue(snapshot.declarations.map { it.providerName }.contains("currentUserProvider"))
        assertTrue(snapshot.declarations.map { it.providerName }.contains("sessionControllerProvider"))
    }

    fun `test provider declarations are cached until psi changes invalidate them`() {
        loadFixture("riverpod/basic")
        val service = RiverpodGraphService.getInstance(project)

        val first = service.providerDeclarations()
        val second = service.providerDeclarations()
        assertSame("Provider declarations should be cached between PSI changes", first, second)

        myFixture.addFileToProject(
            "lib/providers/extra_provider.dart",
            """
            import 'package:riverpod_annotation/riverpod_annotation.dart';

            part 'extra_provider.g.dart';

            @riverpod
            String extraValue(Ref ref) => 'extra';
            """.trimIndent(),
        )

        Thread.sleep(700)
        val afterChange = service.providerDeclarations()

        assertNotSame("Provider declarations should rebuild after PSI changes", first, afterChange)
        assertTrue(
            "Expected newly added extraValueProvider after invalidation, got: ${afterChange.map { it.providerName }}",
            afterChange.any { it.providerName == "extraValueProvider" },
        )
    }

    fun `test provider snapshot reports inactive project without silently returning ready empty`() {
        myFixture.addFileToProject(
            "pubspec.yaml",
            """
            name: plain_app
            dependencies:
              path: ^1.9.0
            """.trimIndent(),
        )
        myFixture.addFileToProject("lib/plain.dart", "String plain() => 'plain';")

        val snapshot = RiverpodGraphService.getInstance(project).providerSnapshot()

        assertEquals(RiverpodProviderLoadStatus.INACTIVE, snapshot.status)
        assertTrue(snapshot.declarations.isEmpty())
    }

    fun `test nested active package keeps monorepo provider snapshot ready`() {
        loadFixture("riverpod/monorepo", "workspace")
        val plain = myFixture.configureByFile("workspace/packages/plain/lib/plain.dart")
        assertFalse(com.ki960213.riverpodgraph.activation.RiverpodActivationService.getInstance(project).isFileActive(plain))

        val snapshot = RiverpodGraphService.getInstance(project).providerSnapshot()

        assertEquals(RiverpodProviderLoadStatus.READY, snapshot.status)
        assertTrue(
            "Nested active app package should keep project active, got: ${snapshot.declarations.map { it.providerName }}",
            snapshot.declarations.any { it.providerName == "appValueProvider" },
        )
    }
}
