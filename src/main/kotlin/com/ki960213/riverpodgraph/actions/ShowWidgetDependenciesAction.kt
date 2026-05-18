package com.ki960213.riverpodgraph.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.platform.util.progress.reportRawProgress
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.indexing.FileBasedIndex
import com.ki960213.riverpodgraph.activation.RiverpodActivationService
import com.ki960213.riverpodgraph.analysis.RefExtensionDependency
import com.ki960213.riverpodgraph.analysis.RefExtensionScanner
import com.ki960213.riverpodgraph.analysis.WidgetDependencyAnalyzer
import com.ki960213.riverpodgraph.analysis.WidgetDependencyResult
import com.ki960213.riverpodgraph.index.RIVERPOD_PROVIDER_INDEX_NAME
import com.ki960213.riverpodgraph.index.RiverpodProviderIndexValue
import com.ki960213.riverpodgraph.model.RiverpodProviderDeclaration
import com.ki960213.riverpodgraph.platform.launchRiverpodBackgroundTask
import com.ki960213.riverpodgraph.platform.smartCancellableReadAction
import com.ki960213.riverpodgraph.ui.WidgetDependencyDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** 현재 편집기 컨텍스트의 위젯에 대한 프로바이더 의존성을 표시합니다. */
class ShowWidgetDependenciesAction : AnAction() {

    /** 액션 업데이트를 백그라운드 스레드에서 실행합니다. */
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    /** 현재 Dart 파일을 분석하고 위젯 의존성 대화상자를 엽니다. */
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file = e.getData(CommonDataKeys.PSI_FILE) ?: return
        if (!file.isRelevantDartFile()) {
            return
        }
        if (!RiverpodActivationService.getInstance(project).isFileActive(file)) {
            return
        }

        val caretOffset = e.getData(CommonDataKeys.EDITOR)?.caretModel?.offset
        project.launchRiverpodBackgroundTask("Analyze Riverpod Widget Dependencies") {
            val cancellationContext = coroutineContext

            @Suppress("UnstableApiUsage")
            val result = reportRawProgress { reporter ->
                reporter.text("Analyzing Riverpod widget dependencies")
                reporter.details(file.virtualFile?.path ?: file.name)
                val result = analyzeWidgetDependencies(project, file, caretOffset) {
                    cancellationContext.ensureActive()
                }
                reporter.fraction(1.0)
                result
            } ?: return@launchRiverpodBackgroundTask
            withContext(Dispatchers.EDT) {
                if (!project.isDisposed) {
                    WidgetDependencyDialog(project, result).show()
                }
            }
        }
    }

    /** Riverpod 프로젝트의 활성 Dart 파일에서 이 액션을 활성화합니다. */
    override fun update(e: AnActionEvent) {
        val project = e.project
        val file = e.getData(CommonDataKeys.PSI_FILE)
        val relevant = project != null &&
                file?.isRelevantDartFile() == true &&
                RiverpodActivationService.getInstance(project).isFileActive(file)
        e.presentation.isEnabled = relevant
        e.presentation.isVisible = relevant
    }

    private fun PsiFile.isRelevantDartFile(): Boolean {
        val fileName = virtualFile?.name ?: name
        return fileName.endsWith(".dart") && !fileName.endsWith(".g.dart")
    }

    private fun analyzeWidgetDependencies(
        project: Project,
        file: PsiFile,
        caretOffset: Int?,
        checkCanceled: () -> Unit,
    ): WidgetDependencyResult? = smartCancellableReadAction(project) {
        if (!file.isValid) {
            return@smartCancellableReadAction null
        }

        val filePath = file.virtualFile?.path ?: file.name
        val content = file.text
        val declarations = withAvailableIndex { providerDeclarationsInReadAction(project) }.orEmpty()
        val providerNames = declarations.map { it.providerName }.toSet()
            .ifEmpty { fallbackProviderNames(content) }
        val extensionDependencies = withAvailableIndex {
            extensionDependenciesInReadAction(project, providerNames, checkCanceled)
        } ?: RefExtensionScanner.scan(filePath, content, providerNames)

        WidgetDependencyAnalyzer.analyze(
            filePath = filePath,
            content = content,
            providerNames = providerNames,
            depthLimit = 5,
            caretOffset = caretOffset,
            declarations = declarations,
            extensionDependencies = extensionDependencies,
        )
    }

    private fun providerDeclarationsInReadAction(project: Project): List<RiverpodProviderDeclaration> {
        val index = FileBasedIndex.getInstance()
        val scope = GlobalSearchScope.projectScope(project)
        val values = index.getAllKeys(RIVERPOD_PROVIDER_INDEX_NAME, project)
            .flatMap { key -> index.getValues(RIVERPOD_PROVIDER_INDEX_NAME, key, scope) }

        return providerDeclarationsFromIndexValues(values)
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

    private fun extensionDependenciesInReadAction(
        project: Project,
        providerNames: Set<String>,
        checkCanceled: () -> Unit,
    ): List<RefExtensionDependency> {
        if (providerNames.isEmpty()) {
            return emptyList()
        }

        val scope = GlobalSearchScope.projectScope(project)
        val psiManager = PsiManager.getInstance(project)

        return FilenameIndex.getAllFilesByExt(project, "dart", scope)
            .asSequence()
            .filter { file -> file.name.endsWith(".dart") && !file.name.endsWith(".g.dart") }
            .flatMap { file ->
                checkCanceled()
                val psiFile = psiManager.findFile(file) ?: return@flatMap emptySequence()
                val text = psiFile.text
                if (!mayContainRefExtension(text, providerNames, checkCanceled)) {
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

    private fun mayContainRefExtension(
        text: String,
        providerNames: Set<String>,
        checkCanceled: () -> Unit,
    ): Boolean {
        if (!text.contains("extension")) {
            return false
        }

        return providerNames.any { providerName ->
            checkCanceled()
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
