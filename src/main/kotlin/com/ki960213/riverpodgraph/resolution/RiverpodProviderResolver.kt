package com.ki960213.riverpodgraph.resolution

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.indexing.FileBasedIndex
import com.ki960213.riverpodgraph.index.RIVERPOD_PROVIDER_INDEX_NAME
import com.ki960213.riverpodgraph.index.RiverpodProviderIndexValue
import com.ki960213.riverpodgraph.model.RiverpodProviderDeclaration
import com.ki960213.riverpodgraph.platform.smartCancellableReadAction

/** 인덱싱된 Riverpod 프로바이더 심볼을 선언과 PSI 요소로 해석합니다. */
class RiverpodProviderResolver(private val project: Project) {
    /** 지정한 범위에서 프로바이더 심볼의 모든 인덱싱된 선언을 찾습니다. */
    fun findDeclarations(
        symbol: String,
        scope: GlobalSearchScope = GlobalSearchScope.projectScope(project),
    ): List<RiverpodProviderDeclaration> = readAction {
        findDeclarationsInReadAction(symbol, scope)
    }

    /** 지정한 범위에서 프로바이더 심볼의 첫 번째 인덱싱된 선언을 찾습니다. */
    fun findDeclaration(
        symbol: String,
        scope: GlobalSearchScope = GlobalSearchScope.projectScope(project),
    ): RiverpodProviderDeclaration? = readAction {
        findDeclarationsInReadAction(symbol, scope).firstOrNull()
    }

    /** 프로바이더 심볼의 모든 인덱싱된 원본 선언에 해당하는 PSI 요소를 찾습니다. */
    fun findSourceElements(
        symbol: String,
        scope: GlobalSearchScope = GlobalSearchScope.projectScope(project),
    ): List<PsiElement> = readAction {
        findSourceElementsInReadAction(symbol, scope)
    }

    /** 인덱싱된 프로바이더 원본 선언의 첫 번째 PSI 요소를 찾습니다. */
    fun findSourceElement(
        symbol: String,
        scope: GlobalSearchScope = GlobalSearchScope.projectScope(project),
    ): PsiElement? = readAction {
        findSourceElementsInReadAction(symbol, scope).firstOrNull()
    }

    private fun findDeclarationsInReadAction(
        symbol: String,
        scope: GlobalSearchScope,
    ): List<RiverpodProviderDeclaration> = sortProviderIndexValues(
        FileBasedIndex.getInstance().getValues(RIVERPOD_PROVIDER_INDEX_NAME, symbol, scope),
    )

    private fun findSourceElementsInReadAction(
        symbol: String,
        scope: GlobalSearchScope,
    ): List<PsiElement> {
        val declarations = findDeclarationsInReadAction(symbol, scope)
        if (declarations.isEmpty()) return emptyList()

        val filesByPath = FileBasedIndex.getInstance()
            .getContainingFiles(RIVERPOD_PROVIDER_INDEX_NAME, symbol, scope)
            .associateBy { it.path }
        val psiManager = PsiManager.getInstance(project)

        return declarations.mapNotNull { declaration ->
            val file = filesByPath[declaration.filePath] ?: return@mapNotNull null
            val psiFile = psiManager.findFile(file) ?: return@mapNotNull null
            psiFile.findElementAt(declaration.textOffset)
        }
    }

    private fun <T> readAction(action: () -> T): T = smartCancellableReadAction(project, action)
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
