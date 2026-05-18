package com.ki960213.riverpodgraph.search

import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiReference
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.util.Processor
import com.intellij.util.indexing.FileBasedIndex
import com.ki960213.riverpodgraph.activation.RiverpodActivationService
import com.ki960213.riverpodgraph.analysis.ProviderUsageScanner
import com.ki960213.riverpodgraph.analysis.RefExtensionDependency
import com.ki960213.riverpodgraph.analysis.RefExtensionScanner
import com.ki960213.riverpodgraph.files.isRiverpodDartSourceFile
import com.ki960213.riverpodgraph.index.RIVERPOD_PROVIDER_INDEX_NAME
import com.ki960213.riverpodgraph.index.RiverpodProviderIndexValue

/** Dart 파일에서 인덱싱된 Riverpod 프로바이더 참조를 검색합니다. */
class RiverpodReferencesSearchExecutor : QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters>(true) {
    /** IntelliJ 참조 검색 쿼리에 Riverpod 프로바이더 사용 위치를 내보냅니다. */
    override fun processQuery(
        queryParameters: ReferencesSearch.SearchParameters,
        consumer: Processor<in PsiReference>
    ) {
        val element = queryParameters.elementToSearch
        val project = element.project
        val activationService = RiverpodActivationService.getInstance(project)
        val elementFile = element.containingFile
        if (elementFile != null) {
            if (!activationService.isFileActive(elementFile)) {
                return
            }
        } else if (!activationService.isProjectActive()) {
            return
        }

        val searchScope = queryParameters.effectiveSearchScope
        val indexScope = searchScope as? GlobalSearchScope ?: GlobalSearchScope.projectScope(project)
        val providerTarget = providerTargetFor(element.text, indexScope) ?: return
        val psiManager = PsiManager.getInstance(project)
        val extensionDependencies = extensionDependenciesFor(project, psiManager, providerTarget, indexScope)
        val extensionDependenciesByFile = extensionDependencies.groupBy { it.filePath }
        val extensionMemberNames = extensionDependencies.mapTo(linkedSetOf()) { it.memberName() }

        for (file in dartFiles(project, searchScope, indexScope)) {
            ProgressManager.checkCanceled()
            val psiFile = psiManager.findFile(file) ?: continue
            val text = psiFile.text
            if (!mayContainUsage(text, providerTarget, extensionMemberNames)) {
                continue
            }

            val emittedOffsets = mutableSetOf<Int>()
            for (dependency in extensionDependenciesByFile[file.path].orEmpty()) {
                ProgressManager.checkCanceled()
                for (offset in dependency.referenceOffsetsFor(providerTarget.providerName)) {
                    ProgressManager.checkCanceled()
                    if (!emitReference(psiFile, offset, providerTarget.providerName, emittedOffsets, consumer)) {
                        return
                    }
                }
            }

            val usages = ProviderUsageScanner.scan(
                filePath = file.path,
                content = text,
                providerNames = setOf(providerTarget.providerName),
                directCallSourceNamesByProvider = mapOf(providerTarget.providerName to providerTarget.directCallSourceNames),
                declarationOffsetsByProvider = mapOf(
                    providerTarget.providerName to providerTarget.declarationOffsetsByFile[file.path].orEmpty(),
                ),
                extensionDependencies = extensionDependencies,
            )
            for (usage in usages) {
                ProgressManager.checkCanceled()
                if (!emitReference(psiFile, usage.textOffset, usage.providerName, emittedOffsets, consumer)) {
                    return
                }
            }
        }
    }

    private fun providerTargetFor(symbol: String, scope: GlobalSearchScope): ProviderTarget? {
        val values = withAvailableIndex {
            FileBasedIndex.getInstance().getValues(RIVERPOD_PROVIDER_INDEX_NAME, symbol, scope)
        }.orEmpty()
        if (values.isEmpty()) {
            return null
        }

        val providerName = values.first().providerName
        return ProviderTarget(
            providerName = providerName,
            directCallSourceNames = values.sourceNamesFor(providerName),
            declarationOffsetsByFile = values.declarationOffsetsByFileFor(providerName),
        )
    }

    private fun dartFiles(
        project: Project,
        searchScope: SearchScope,
        indexScope: GlobalSearchScope,
    ): Sequence<VirtualFile> {
        val files = when (searchScope) {
            is LocalSearchScope -> searchScope.scope.asSequence()
                .mapNotNull { it.containingFile?.virtualFile }
                .distinct()

            else -> FilenameIndex.getAllFilesByExt(project, "dart", indexScope).asSequence()
        }

        return files.filter { it.isRiverpodDartSourceFile() }
    }

    private fun extensionDependenciesFor(
        project: Project,
        psiManager: PsiManager,
        providerTarget: ProviderTarget,
        indexScope: GlobalSearchScope,
    ): List<RefExtensionDependency> {
        val dependencies = mutableListOf<RefExtensionDependency>()
        for (file in dartFiles(project, indexScope, indexScope)) {
            ProgressManager.checkCanceled()
            val psiFile = psiManager.findFile(file) ?: continue
            val text = psiFile.text
            if (!mayContainProviderSource(text, providerTarget)) {
                continue
            }

            dependencies += RefExtensionScanner.scan(
                filePath = file.path,
                content = text,
                providerNames = setOf(providerTarget.providerName),
            )
        }

        return dependencies.distinct()
    }

    private fun List<RiverpodProviderIndexValue>.sourceNamesFor(providerName: String): Set<String> =
        filter { it.providerName == providerName }
            .mapTo(linkedSetOf()) { it.sourceName }

    private fun List<RiverpodProviderIndexValue>.declarationOffsetsByFileFor(providerName: String): Map<String, Set<Int>> =
        filter { it.providerName == providerName }
            .groupBy { it.filePath }
            .mapValues { (_, values) -> values.mapTo(linkedSetOf()) { it.textOffset } }

    private fun mayContainUsage(
        text: String,
        providerTarget: ProviderTarget,
        extensionMemberNames: Set<String>,
    ): Boolean =
        mayContainProviderSource(text, providerTarget) ||
                mayContainExtensionMember(text, extensionMemberNames)

    private fun mayContainProviderSource(text: String, providerTarget: ProviderTarget): Boolean =
        text.contains(providerTarget.providerName) ||
                providerTarget.directCallSourceNames.any { text.contains(it) }

    private fun mayContainExtensionMember(text: String, extensionMemberNames: Set<String>): Boolean {
        for (memberName in extensionMemberNames) {
            ProgressManager.checkCanceled()
            if (text.contains(memberName)) {
                return true
            }
        }

        return false
    }

    private fun RefExtensionDependency.memberName(): String = memberId.substringAfterLast('.')

    private fun RefExtensionDependency.referenceOffsetsFor(providerName: String): List<Int> {
        val providerOffsets = providerOffsetsByProvider[providerName].orEmpty()
        if (providerOffsets.isNotEmpty()) {
            return providerOffsets
        }

        return if (providerName in providerNames) listOf(textOffset) else emptyList()
    }

    private fun emitReference(
        psiFile: PsiFile,
        textOffset: Int,
        providerName: String,
        emittedOffsets: MutableSet<Int>,
        consumer: Processor<in PsiReference>,
    ): Boolean {
        if (!emittedOffsets.add(textOffset)) {
            return true
        }

        val usageElement = psiFile.findElementAt(textOffset) ?: return true
        val reference = RiverpodReference(
            element = usageElement,
            rangeInElement = TextRange(0, usageElement.textLength),
            providerName = providerName,
        )
        return consumer.process(reference)
    }

    private fun <T> withAvailableIndex(action: () -> T): T? {
        return try {
            action()
        } catch (exception: IllegalStateException) {
            if (exception.message?.contains("Index is not created") == true) {
                null
            } else {
                throw exception
            }
        }
    }

    private data class ProviderTarget(
        val providerName: String,
        val directCallSourceNames: Set<String>,
        val declarationOffsetsByFile: Map<String, Set<Int>>,
    )
}
