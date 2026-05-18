package com.ki960213.riverpodgraph.activation

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.SearchScope
import com.intellij.util.indexing.FileBasedIndex
import com.ki960213.riverpodgraph.files.isRiverpodDartSourceFile
import com.ki960213.riverpodgraph.index.RIVERPOD_PROVIDER_INDEX_NAME
import com.ki960213.riverpodgraph.index.RiverpodProviderIndexValue
import com.ki960213.riverpodgraph.index.providerDeclarationsFromIndexValues
import com.ki960213.riverpodgraph.model.RiverpodProviderDeclaration
import com.ki960213.riverpodgraph.parser.RiverpodAnnotationParser
import com.ki960213.riverpodgraph.platform.smartCancellableReadAction

/**
 * 활성 Riverpod 모듈에 속한 Dart 소스와 Provider 선언만 노출합니다.
 */
@Service(Service.Level.PROJECT)
class RiverpodActiveSourceScope(private val project: Project) {
    /** 검색 범위 안에서 활성 Riverpod Dart 소스 파일만 반환합니다. */
    fun activeDartFiles(
        searchScope: SearchScope = GlobalSearchScope.projectScope(project),
    ): List<VirtualFile> = readAction {
        activeDartFilesInReadAction(searchScope)
    }

    /** 활성 Riverpod Dart 파일에 속한 모든 인덱싱된 Provider 선언을 반환합니다. */
    fun providerDeclarations(
        scope: GlobalSearchScope = GlobalSearchScope.projectScope(project),
    ): List<RiverpodProviderDeclaration> = readAction {
        providerDeclarationsInReadAction(scope)
    }

    /** 활성 Riverpod Dart 파일에 속한 지정 심볼의 Provider 선언을 반환합니다. */
    fun providerDeclarations(
        symbol: String,
        scope: GlobalSearchScope = GlobalSearchScope.projectScope(project),
    ): List<RiverpodProviderDeclaration> = readAction {
        providerDeclarationsInReadAction(symbol, scope)
    }

    /** 읽기 액션 안에서 검색 범위의 활성 Dart 파일을 계산합니다. */
    internal fun activeDartFilesInReadAction(
        searchScope: SearchScope = GlobalSearchScope.projectScope(project),
    ): List<VirtualFile> {
        val files = when (searchScope) {
            is LocalSearchScope -> searchScope.scope.asSequence()
                .mapNotNull { element -> element.containingFile?.virtualFile }

            is GlobalSearchScope -> FilenameIndex.getAllFilesByExt(project, "dart", searchScope).asSequence()
            else -> FilenameIndex.getAllFilesByExt(project, "dart", GlobalSearchScope.projectScope(project)).asSequence()
        }
        val activationService = RiverpodActivationService.getInstance(project)

        return files
            .filter { file -> file.isRiverpodDartSourceFile() }
            .filter { file -> activationService.isFileActiveInReadAction(file) }
            .distinctBy { file -> file.path }
            .toList()
    }

    /** 읽기 액션 안에서 활성 Riverpod Dart 파일에 속한 모든 Provider 선언을 반환합니다. */
    internal fun providerDeclarationsInReadAction(
        scope: GlobalSearchScope = GlobalSearchScope.projectScope(project),
    ): List<RiverpodProviderDeclaration> {
        val activeFiles = activeDartFilesInReadAction(scope)
        val activeFilePaths = activeFiles.paths()
        val indexedValues = allIndexValuesInReadAction(scope)
        if (indexedValues != null) {
            return indexedValues
                .activeIndexValuesInRiverpodFiles(activeFilePaths)
                .let(::providerDeclarationsFromIndexValues)
        }

        return activeFiles
            .flatMap { file -> declarationsFromSourceFile(file) }
            .sortedWith(
                compareBy<RiverpodProviderDeclaration> { it.filePath }
                    .thenBy { it.textOffset }
                    .thenBy { it.sourceName }
                    .thenBy { it.providerName },
            )
            .distinctBy { declaration ->
                Triple(declaration.providerName, declaration.filePath, declaration.textOffset)
            }
    }

    /** 읽기 액션 안에서 활성 Riverpod Dart 파일에 속한 지정 심볼의 Provider 선언을 반환합니다. */
    internal fun providerDeclarationsInReadAction(
        symbol: String,
        scope: GlobalSearchScope = GlobalSearchScope.projectScope(project),
    ): List<RiverpodProviderDeclaration> {
        val activeFilePaths = activeDartFilesInReadAction(scope).paths()
        val indexedValues = indexValuesInReadAction(symbol, scope)
        if (indexedValues != null) {
            return indexedValues
                .activeIndexValuesInRiverpodFiles(activeFilePaths)
                .let(::providerDeclarationsFromIndexValues)
        }

        return providerDeclarationsInReadAction(scope)
            .filter { declaration ->
                declaration.providerName == symbol ||
                        declaration.sourceName == symbol ||
                        declaration.generatedSuperclassName == symbol
            }
    }

    /** 읽기 액션 안에서 전체 Provider 인덱스 값을 안정적인 목록으로 읽습니다. */
    private fun allIndexValuesInReadAction(scope: GlobalSearchScope): List<RiverpodProviderIndexValue>? =
        try {
            val index = FileBasedIndex.getInstance()
            index.getAllKeys(RIVERPOD_PROVIDER_INDEX_NAME, project)
                .flatMap { key -> index.getValues(RIVERPOD_PROVIDER_INDEX_NAME, key, scope) }
        } catch (exception: IllegalStateException) {
            if (exception.message?.contains("Index is not created") == true) {
                null
            } else {
                throw exception
            }
        }

    /** 읽기 액션 안에서 지정 심볼의 Provider 인덱스 값을 읽습니다. */
    private fun indexValuesInReadAction(
        symbol: String,
        scope: GlobalSearchScope,
    ): List<RiverpodProviderIndexValue>? =
        try {
            FileBasedIndex.getInstance().getValues(RIVERPOD_PROVIDER_INDEX_NAME, symbol, scope)
        } catch (exception: IllegalStateException) {
            if (exception.message?.contains("Index is not created") == true) {
                null
            } else {
                throw exception
            }
        }

    /** PSI 또는 VFS에서 활성 Dart 파일의 Provider 선언을 직접 파싱합니다. */
    private fun declarationsFromSourceFile(file: VirtualFile): List<RiverpodProviderDeclaration> {
        val text = sourceText(file) ?: return emptyList()
        return RiverpodAnnotationParser.parse(file.path, text)
    }

    /** PSI 또는 VFS에서 가상 파일의 텍스트를 읽습니다. */
    private fun sourceText(file: VirtualFile): String? =
        try {
            PsiManager.getInstance(project).findFile(file)?.text ?: VfsUtil.loadText(file)
        } catch (exception: ProcessCanceledException) {
            throw exception
        } catch (_: Exception) {
            null
        }

    /** 프로젝트에 묶인 취소 가능한 읽기 액션으로 Scope 계산을 실행합니다. */
    private fun <T> readAction(action: () -> T): T = smartCancellableReadAction(project, action)

    /** 프로젝트 서비스 인스턴스에 접근하는 진입점입니다. */
    companion object {
        /** 프로젝트의 활성 Riverpod 소스 범위 서비스를 반환합니다. */
        fun getInstance(project: Project): RiverpodActiveSourceScope = project.service()
    }
}

/** 활성 파일 경로에 속한 Provider 인덱스 값만 반환합니다. */
private fun Collection<RiverpodProviderIndexValue>.activeIndexValuesInRiverpodFiles(
    activeFilePaths: Set<String>,
): List<RiverpodProviderIndexValue> =
    filter { value -> value.filePath in activeFilePaths }

/** VirtualFile 목록을 파일 경로 집합으로 변환합니다. */
private fun Collection<VirtualFile>.paths(): Set<String> = mapTo(linkedSetOf()) { file -> file.path }
