package com.ki960213.riverpodgraph.resolution

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.indexing.FileBasedIndex
import com.ki960213.riverpodgraph.index.RiverpodProviderIndex
import com.ki960213.riverpodgraph.index.RiverpodProviderIndexValue
import com.ki960213.riverpodgraph.model.RiverpodProviderDeclaration

class RiverpodProviderResolver(private val project: Project) {
    fun findDeclarations(
        symbol: String,
        scope: GlobalSearchScope = GlobalSearchScope.projectScope(project),
    ): List<RiverpodProviderDeclaration> = readAction {
        findDeclarationsInReadAction(symbol, scope)
    }

    fun findDeclaration(
        symbol: String,
        scope: GlobalSearchScope = GlobalSearchScope.projectScope(project),
    ): RiverpodProviderDeclaration? = readAction {
        findDeclarationsInReadAction(symbol, scope).firstOrNull()
    }

    fun findSourceElements(
        symbol: String,
        scope: GlobalSearchScope = GlobalSearchScope.projectScope(project),
    ): List<PsiElement> = readAction {
        findSourceElementsInReadAction(symbol, scope)
    }

    fun findSourceElement(
        symbol: String,
        scope: GlobalSearchScope = GlobalSearchScope.projectScope(project),
    ): PsiElement? = readAction {
        findSourceElementsInReadAction(symbol, scope).firstOrNull()
    }

    private fun findDeclarationsInReadAction(
        symbol: String,
        scope: GlobalSearchScope,
    ): List<RiverpodProviderDeclaration> {
        return sortProviderIndexValues(
            FileBasedIndex.getInstance().getValues(RiverpodProviderIndex.NAME, symbol, scope),
        )
    }

    private fun findSourceElementsInReadAction(
        symbol: String,
        scope: GlobalSearchScope,
    ): List<PsiElement> {
        val declarations = findDeclarationsInReadAction(symbol, scope)
        if (declarations.isEmpty()) {
            return emptyList()
        }

        val filesByPath = FileBasedIndex.getInstance()
            .getContainingFiles(RiverpodProviderIndex.NAME, symbol, scope)
            .associateBy { it.path }
        val psiManager = PsiManager.getInstance(project)

        return declarations.mapNotNull { declaration ->
            val file = filesByPath[declaration.filePath] ?: return@mapNotNull null
            val psiFile = psiManager.findFile(file) ?: return@mapNotNull null
            psiFile.findElementAt(declaration.textOffset)
        }
    }

    companion object {
        private fun <T> readAction(action: () -> T): T = ReadAction.compute<T, RuntimeException> { action() }
    }
}

internal fun sortProviderIndexValues(
    values: Collection<RiverpodProviderIndexValue>,
): List<RiverpodProviderDeclaration> = values
    .map { it.toDeclaration() }
    .sortedWith(
        compareBy<RiverpodProviderDeclaration> { it.filePath }
            .thenBy { it.textOffset }
            .thenBy { it.sourceName }
            .thenBy { it.providerName },
    )
