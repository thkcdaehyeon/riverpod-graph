package com.ki960213.riverpodgraph.resolution

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.indexing.FileBasedIndex
import com.ki960213.riverpodgraph.activation.RiverpodActiveSourceScope
import com.ki960213.riverpodgraph.index.RIVERPOD_PROVIDER_INDEX_NAME
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

    /** 읽기 액션 안에서 인덱스 값들을 프로바이더 선언으로 변환합니다. */
    private fun findDeclarationsInReadAction(
        symbol: String,
        scope: GlobalSearchScope,
    ): List<RiverpodProviderDeclaration> =
        RiverpodActiveSourceScope.getInstance(project).providerDeclarationsInReadAction(symbol, scope)

    /** 인덱싱된 선언 오프셋을 실제 PSI 원본 요소로 변환합니다. */
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

    /** 프로젝트에 묶인 취소 가능한 읽기 액션으로 작업을 실행합니다. */
    private fun <T> readAction(action: () -> T): T = smartCancellableReadAction(project, action)
}
