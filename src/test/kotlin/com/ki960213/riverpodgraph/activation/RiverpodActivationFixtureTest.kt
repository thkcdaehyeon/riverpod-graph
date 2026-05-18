package com.ki960213.riverpodgraph.activation

import com.ki960213.riverpodgraph.test.RiverpodFixtureBase

class RiverpodActivationFixtureTest : RiverpodFixtureBase() {
    fun `test nested package with its own riverpod pubspec is active`() {
        loadFixture("riverpod/monorepo")
        val file = myFixture.configureByFile("packages/app/lib/app_provider.dart")

        assertTrue(RiverpodActivationService.getInstance(project).isFileActive(file))
    }

    fun `test active source scope includes nested riverpod package and excludes plain package`() {
        loadFixture("riverpod/monorepo")

        val activeFiles = RiverpodActiveSourceScope.getInstance(project)
            .activeDartFiles()
            .map { it.path }
            .sorted()

        assertTrue(
            "Expected nested Riverpod package source in active files, got: $activeFiles",
            activeFiles.any { it.endsWith("packages/app/lib/app_provider.dart") },
        )
        assertFalse(
            "Plain package without riverpod_annotation should stay inactive, got: $activeFiles",
            activeFiles.any { it.endsWith("packages/plain/lib/plain.dart") },
        )
    }
}
