package com.ki960213.riverpodgraph.gutter

import com.ki960213.riverpodgraph.test.RiverpodFixtureBase

class RiverpodGutterFixtureTest : RiverpodFixtureBase() {
    fun `test doHighlighting creates gutter markers for riverpod declarations`() {
        loadFixture("riverpod/basic")
        myFixture.configureByFile("lib/providers/user_providers.dart")

        myFixture.doHighlighting()

        val tooltips = myFixture.findAllGutters().map { it.tooltipText.orEmpty() }
        assertTrue(
            "Expected Riverpod gutter marker from real highlighting, got: $tooltips",
            tooltips.any { it.contains("Riverpod provider") },
        )
    }
}
