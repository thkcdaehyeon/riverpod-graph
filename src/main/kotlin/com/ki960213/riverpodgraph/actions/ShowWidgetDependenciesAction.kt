package com.ki960213.riverpodgraph.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.indexing.FileBasedIndex
import com.ki960213.riverpodgraph.analysis.RefExtensionDependency
import com.ki960213.riverpodgraph.analysis.RefExtensionScanner
import com.ki960213.riverpodgraph.analysis.WidgetDependencyAnalyzer
import com.ki960213.riverpodgraph.index.RiverpodProviderIndex
import com.ki960213.riverpodgraph.index.RiverpodProviderIndexValue
import com.ki960213.riverpodgraph.model.RiverpodProviderDeclaration
import com.ki960213.riverpodgraph.ui.WidgetDependencyDialog

class ShowWidgetDependenciesAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val file = e.getData(CommonDataKeys.PSI_FILE) ?: return
        if (!file.isRelevantDartFile()) {
            return
        }

        val declarations = providerDeclarations(e.project)
        val providerNames = declarations.map { it.providerName }.toSet()
            .ifEmpty { fallbackProviderNames(file.text) }
        val filePath = file.virtualFile?.path ?: file.name
        val extensionDependencies = extensionDependencies(e.project, providerNames, filePath, file.text)
        val result = WidgetDependencyAnalyzer.analyze(
            filePath = filePath,
            content = file.text,
            providerNames = providerNames,
            depthLimit = 5,
            caretOffset = e.getData(CommonDataKeys.EDITOR)?.caretModel?.offset,
            declarations = declarations,
            extensionDependencies = extensionDependencies,
        )
        WidgetDependencyDialog(e.project, result).show()
    }

    override fun update(e: AnActionEvent) {
        val relevant = e.getData(CommonDataKeys.PSI_FILE)?.isRelevantDartFile() == true
        e.presentation.isEnabled = relevant
        e.presentation.isVisible = relevant
    }

    private fun PsiFile.isRelevantDartFile(): Boolean {
        val fileName = virtualFile?.name ?: name
        return fileName.endsWith(".dart") && !fileName.endsWith(".g.dart")
    }

    private fun providerDeclarations(project: Project?): List<RiverpodProviderDeclaration> {
        if (project == null) {
            return emptyList()
        }

        return withAvailableIndex {
            ReadAction.compute<List<RiverpodProviderDeclaration>, RuntimeException> {
                val index = FileBasedIndex.getInstance()
                val scope = GlobalSearchScope.projectScope(project)
                val values = index.getAllKeys(RiverpodProviderIndex.NAME, project)
                    .flatMap { key -> index.getValues(RiverpodProviderIndex.NAME, key, scope) }

                providerDeclarationsFromIndexValues(values)
            }
        }.orEmpty()
    }

    private fun providerDeclarationsFromIndexValues(
        values: Collection<RiverpodProviderIndexValue>,
    ): List<RiverpodProviderDeclaration> = values
        .map { it.toDeclaration() }
        .sortedWith(
            compareBy<RiverpodProviderDeclaration> { it.filePath }
                .thenBy { it.textOffset }
                .thenBy { it.sourceName }
                .thenBy { it.providerName },
        )
        .distinctBy { declaration ->
            Triple(declaration.providerName, declaration.filePath, declaration.textOffset)
        }

    private fun extensionDependencies(
        project: Project?,
        providerNames: Set<String>,
        currentFilePath: String,
        currentText: String,
    ): List<RefExtensionDependency> {
        if (providerNames.isEmpty()) {
            return emptyList()
        }
        if (project == null) {
            return RefExtensionScanner.scan(currentFilePath, currentText, providerNames)
        }

        return withAvailableIndex {
            ReadAction.compute<List<RefExtensionDependency>, RuntimeException> {
                val scope = GlobalSearchScope.projectScope(project)
                val psiManager = PsiManager.getInstance(project)

                FilenameIndex.getAllFilesByExt(project, "dart", scope)
                    .asSequence()
                    .filter { file -> file.name.endsWith(".dart") && !file.name.endsWith(".g.dart") }
                    .flatMap { file ->
                        ProgressManager.checkCanceled()
                        val psiFile = psiManager.findFile(file) ?: return@flatMap emptySequence()
                        val text = psiFile.text
                        if (!mayContainRefExtension(text, providerNames)) {
                            emptySequence()
                        } else {
                            RefExtensionScanner.scan(
                                filePath = file.path,
                                content = text,
                                providerNames = providerNames,
                            ).asSequence()
                        }
                    }
                    .distinct()
                    .toList()
            }
        } ?: RefExtensionScanner.scan(currentFilePath, currentText, providerNames)
    }

    private fun mayContainRefExtension(text: String, providerNames: Set<String>): Boolean {
        if (!text.contains("extension")) {
            return false
        }

        return providerNames.any { providerName ->
            ProgressManager.checkCanceled()
            text.contains(providerName)
        }
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

    internal companion object {
        fun fallbackProviderNames(content: String): Set<String> =
            providerRegex.findAll(codeOnly(content, dartCodeMask(content))).map { it.value }.toSet()

        private val providerRegex = Regex("""[A-Za-z_${'$'}][\w${'$'}]*Provider""")

        private fun dartCodeMask(content: String): BooleanArray {
            val codeMask = BooleanArray(content.length) { true }
            var index = 0
            while (index < content.length) {
                when {
                    content.startsWith("//", index) -> {
                        val end = content.indexOf('\n', index + 2).takeIf { it != -1 } ?: content.length
                        codeMask.markIgnored(index, end)
                        index = end
                    }
                    content.startsWith("/*", index) -> {
                        val end = blockCommentEnd(content, index)
                        codeMask.markIgnored(index, end)
                        index = end
                    }
                    content[index] == '\'' || content[index] == '"' -> {
                        val end = stringEnd(content, index)
                        codeMask.markIgnored(index, end)
                        index = end
                    }
                    else -> index++
                }
            }

            return codeMask
        }

        private fun blockCommentEnd(content: String, start: Int): Int {
            var depth = 0
            var index = start
            while (index < content.length) {
                when {
                    content.startsWith("/*", index) -> {
                        depth++
                        index += 2
                    }
                    content.startsWith("*/", index) -> {
                        depth--
                        index += 2
                        if (depth == 0) {
                            return index
                        }
                    }
                    else -> index++
                }
            }

            return content.length
        }

        private fun stringEnd(content: String, start: Int): Int {
            val quote = content[start]
            val triple = content.startsWith("$quote$quote$quote", start)
            val raw = start > 0 &&
                (content[start - 1] == 'r' || content[start - 1] == 'R') &&
                (start == 1 || !isIdentifierPart(content[start - 2]))
            var index = start + if (triple) 3 else 1

            while (index < content.length) {
                if (!raw && content[index] == '\\') {
                    index += 2
                    continue
                }
                if (triple && content.startsWith("$quote$quote$quote", index)) {
                    return index + 3
                }
                if (!triple && content[index] == quote) {
                    return index + 1
                }
                index++
            }

            return content.length
        }

        private fun codeOnly(content: String, codeMask: BooleanArray): String = buildString(content.length) {
            for (index in content.indices) {
                append(if (codeMask[index]) content[index] else ' ')
            }
        }

        private fun BooleanArray.markIgnored(start: Int, end: Int) {
            for (index in start until end.coerceAtMost(size)) {
                this[index] = false
            }
        }

        private fun isIdentifierPart(char: Char): Boolean =
            char == '_' || char == '$' || char.isLetterOrDigit()
    }
}
