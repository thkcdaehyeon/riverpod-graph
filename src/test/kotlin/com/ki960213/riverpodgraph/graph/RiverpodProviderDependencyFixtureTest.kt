package com.ki960213.riverpodgraph.graph

import com.intellij.psi.PsiManager
import com.ki960213.riverpodgraph.actions.ProviderGraphSourceFile
import com.ki960213.riverpodgraph.actions.providerGraphEdgesForSourceFiles
import com.ki960213.riverpodgraph.activation.RiverpodActiveSourceScope
import com.ki960213.riverpodgraph.model.RiverpodUsageKind
import com.ki960213.riverpodgraph.test.RiverpodFixtureBase

class RiverpodProviderDependencyFixtureTest : RiverpodFixtureBase() {
    fun `test provider graph classifies family future and select dependencies`() {
        loadFixture("riverpod/basic")
        val activeScope = RiverpodActiveSourceScope.getInstance(project)
        val declarations = activeScope.providerDeclarations()
        val psiManager = PsiManager.getInstance(project)
        val sourceFiles = activeScope.activeDartFiles().mapNotNull { file ->
            val psiFile = psiManager.findFile(file) ?: return@mapNotNull null
            ProviderGraphSourceFile(file.path, psiFile.text, psiFile)
        }

        val edges = providerGraphEdgesForSourceFiles(sourceFiles, declarations)
        val selectedEdges = edges
            .filter { it.fromProvider == "selectedUserNameProvider" && it.toProvider == "userProvider" }
            .map { it.usageKind }
            .toSet()

        assertTrue(
            "Expected selectedUserNameProvider to classify userProvider .future and .select, got: $edges",
            selectedEdges.containsAll(setOf(RiverpodUsageKind.FUTURE, RiverpodUsageKind.SELECT)),
        )
    }
}
