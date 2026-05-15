package com.ki960213.riverpodgraph.search

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.util.Processor
import com.intellij.util.indexing.FileBasedIndex
import com.ki960213.riverpodgraph.analysis.ProviderUsageScanner
import com.ki960213.riverpodgraph.index.RiverpodProviderIndex
import com.ki960213.riverpodgraph.index.RiverpodProviderIndexValue

class RiverpodReferencesSearchExecutor : QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters>(true) {
    override fun processQuery(queryParameters: ReferencesSearch.SearchParameters, consumer: Processor<in PsiReference>) {
        val element = queryParameters.elementToSearch
        val project = element.project
        val searchScope = queryParameters.effectiveSearchScope
        val indexScope = searchScope as? GlobalSearchScope ?: GlobalSearchScope.projectScope(project)
        val providerTarget = providerTargetFor(element.text, indexScope) ?: return
        val psiManager = PsiManager.getInstance(project)

        for (file in dartFiles(project, searchScope, indexScope)) {
            ProgressManager.checkCanceled()
            val psiFile = psiManager.findFile(file) ?: continue
            val text = psiFile.text
            if (!mayContainUsage(text, providerTarget)) {
                continue
            }

            val usages = ProviderUsageScanner.scan(
                filePath = file.path,
                content = text,
                providerNames = setOf(providerTarget.providerName),
                directCallSourceNamesByProvider = mapOf(providerTarget.providerName to providerTarget.directCallSourceNames),
                declarationOffsetsByProvider = mapOf(
                    providerTarget.providerName to providerTarget.declarationOffsetsByFile[file.path].orEmpty(),
                ),
            )
            for (usage in usages) {
                val usageElement = psiFile.findElementAt(usage.textOffset) ?: continue
                val reference = RiverpodReference(
                    element = usageElement,
                    rangeInElement = TextRange(0, usageElement.textLength),
                    providerName = providerTarget.providerName,
                )
                if (!consumer.process(reference)) {
                    return
                }
            }
        }
    }

    private fun providerTargetFor(symbol: String, scope: GlobalSearchScope): ProviderTarget? {
        val values = withAvailableIndex {
            FileBasedIndex.getInstance().getValues(RiverpodProviderIndex.NAME, symbol, scope)
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

        return files.filter { it.name.endsWith(".dart") && !it.name.endsWith(".g.dart") }
    }

    private fun List<RiverpodProviderIndexValue>.sourceNamesFor(providerName: String): Set<String> =
        filter { it.providerName == providerName }
            .mapTo(linkedSetOf()) { it.sourceName }

    private fun List<RiverpodProviderIndexValue>.declarationOffsetsByFileFor(providerName: String): Map<String, Set<Int>> =
        filter { it.providerName == providerName }
            .groupBy { it.filePath }
            .mapValues { (_, values) -> values.mapTo(linkedSetOf()) { it.textOffset } }

    private fun mayContainUsage(text: String, providerTarget: ProviderTarget): Boolean =
        text.contains(providerTarget.providerName) ||
            providerTarget.directCallSourceNames.any { text.contains(it) }

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
