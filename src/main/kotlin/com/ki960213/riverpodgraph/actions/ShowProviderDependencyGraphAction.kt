package com.ki960213.riverpodgraph.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.platform.util.progress.reportRawProgress
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.indexing.FileBasedIndex
import com.ki960213.riverpodgraph.activation.RiverpodActivationService
import com.ki960213.riverpodgraph.analysis.ProviderDependencyAnalyzer
import com.ki960213.riverpodgraph.index.RiverpodProviderIndex
import com.ki960213.riverpodgraph.index.RiverpodProviderIndexValue
import com.ki960213.riverpodgraph.model.RiverpodDependencyEdge
import com.ki960213.riverpodgraph.model.RiverpodProviderDeclaration
import com.ki960213.riverpodgraph.platform.launchRiverpodBackgroundTask
import com.ki960213.riverpodgraph.platform.smartCancellableReadAction
import com.ki960213.riverpodgraph.resolution.RiverpodProviderResolver
import com.ki960213.riverpodgraph.ui.DependencyGraphPanel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ShowProviderDependencyGraphAction : AnAction() {

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file = e.getData(CommonDataKeys.PSI_FILE) ?: return
        if (!file.isRelevantDartFile()) {
            return
        }
        if (!RiverpodActivationService.getInstance(project).isFileActive(file)) {
            return
        }

        val declaration = declarationFromContext(e) ?: return
        project.launchRiverpodBackgroundTask("Analyze Riverpod Provider Dependencies") {
            val edges = reportRawProgress { reporter ->
                reporter.text("Analyzing Riverpod provider dependencies")
                reporter.details(declaration.providerName)
                val edges = dependencyEdges(project, file, declaration)
                reporter.fraction(1.0)
                edges
            }
            withContext(Dispatchers.EDT) {
                if (!project.isDisposed) {
                    showGraph(project, declaration.providerName, edges)
                }
            }
        }
    }

    override fun update(e: AnActionEvent) {
        val file = e.getData(CommonDataKeys.PSI_FILE)
        val editor = e.getData(CommonDataKeys.EDITOR)
        val element = e.getData(CommonDataKeys.PSI_ELEMENT)
        val project = e.project
        val relevant = project != null &&
            file?.isRelevantDartFile() == true &&
            RiverpodActivationService.getInstance(project).isFileActive(file) &&
            symbolCandidates(file, editor, element).isNotEmpty()

        e.presentation.isEnabled = relevant
        e.presentation.isVisible = relevant
    }

    private fun declarationFromContext(e: AnActionEvent): RiverpodProviderDeclaration? {
        val project = e.project ?: return null
        val file = e.getData(CommonDataKeys.PSI_FILE) ?: return null
        val editor = e.getData(CommonDataKeys.EDITOR)
        val element = e.getData(CommonDataKeys.PSI_ELEMENT)
        val resolver = RiverpodProviderResolver(project)

        return symbolCandidates(file, editor, element)
            .firstNotNullOfOrNull { symbol -> withAvailableIndex { resolver.findDeclaration(symbol) } }
    }

    private fun showGraph(
        project: Project,
        providerName: String,
        edges: List<RiverpodDependencyEdge>,
    ) {
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID) ?: return

        toolWindow.show {
            DependencyGraphPanel.findIn(toolWindow.component)?.let { graphPanel ->
                graphPanel.showProviderGraph(providerName, edges)
                DependencyGraphPanel.selectTabContaining(toolWindow.component, graphPanel)
            }
        }
    }

    private fun dependencyEdges(
        project: Project,
        file: PsiFile,
        declaration: RiverpodProviderDeclaration,
    ): List<RiverpodDependencyEdge> = smartCancellableReadAction(project) {
        if (!file.isValid) {
            return@smartCancellableReadAction emptyList()
        }

        val declarations = withAvailableIndex {
            providerDeclarationsInReadAction(project)
        }.orEmpty().withDeclaration(declaration)
        val sourceFiles = providerSourceFilesInReadAction(project, file, declarations)

        providerGraphEdgesForSourceFiles(sourceFiles, declarations)
    }

    private fun providerDeclarationsInReadAction(project: Project): List<RiverpodProviderDeclaration> {
        val index = FileBasedIndex.getInstance()
        val scope = GlobalSearchScope.projectScope(project)
        val values = index.getAllKeys(RiverpodProviderIndex.NAME, project)
            .flatMap { key -> index.getValues(RiverpodProviderIndex.NAME, key, scope) }

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

    private fun List<RiverpodProviderDeclaration>.withDeclaration(
        declaration: RiverpodProviderDeclaration,
    ): List<RiverpodProviderDeclaration> {
        val key = Triple(declaration.providerName, declaration.filePath, declaration.textOffset)
        if (any { Triple(it.providerName, it.filePath, it.textOffset) == key }) {
            return this
        }

        return this + declaration
    }

    private fun providerSourceFilesInReadAction(
        project: Project,
        invocationFile: PsiFile,
        declarations: List<RiverpodProviderDeclaration>,
    ): List<ProviderGraphSourceFile> {
        val declarationPaths = declarations.mapTo(linkedSetOf()) { it.filePath }
        val invocationPath = invocationFile.virtualFile?.path ?: invocationFile.name
        val scope = GlobalSearchScope.projectScope(project)
        val indexedFilesByPath = withAvailableIndex {
            FilenameIndex.getAllFilesByExt(project, "dart", scope).associateBy { it.path }
        }.orEmpty()

        return declarationPaths.mapNotNull { filePath ->
            val virtualFile = LocalFileSystem.getInstance().findFileByPath(filePath) ?: indexedFilesByPath[filePath]
            val content = virtualFile?.let { sourceText(project, it) }
                ?: invocationFile.text.takeIf { filePath == invocationPath }
                ?: return@mapNotNull null

            ProviderGraphSourceFile(filePath, content)
        }
    }

    private fun sourceText(project: Project, virtualFile: VirtualFile): String? =
        try {
            PsiManager.getInstance(project).findFile(virtualFile)?.text ?: VfsUtil.loadText(virtualFile)
        } catch (exception: ProcessCanceledException) {
            throw exception
        } catch (_: Exception) {
            null
        }

    private fun symbolCandidates(
        file: PsiFile,
        editor: Editor?,
        element: PsiElement?,
    ): List<String> = buildList {
        editor?.let { symbolAt(file.text, it.caretModel.offset) }?.let(::add)
        element?.text?.takeIf { IDENTIFIER_REGEX.matches(it) }?.let(::add)
    }.distinct()

    private fun symbolAt(content: String, offset: Int): String? {
        val lookupOffset = offset.coerceIn(0, content.length)
        val span = symbolSpan(content, lookupOffset)
        val localContent = content.substring(span)
        val localOffset = lookupOffset - span.first
        val identifiers = IDENTIFIER_REGEX.findAll(localContent).toList()
        val identifierIndex = identifiers.indexOfFirst { match ->
            localOffset in match.range || localOffset - 1 in match.range
        }
        if (identifierIndex < 0) {
            return null
        }

        val identifier = identifiers[identifierIndex]
        if (identifier.value !in PROVIDER_MODIFIERS) {
            return identifier.value
        }

        return providerBaseBeforeModifier(localContent, identifiers, identifierIndex)
    }

    private fun symbolSpan(content: String, offset: Int): IntRange {
        var start = (offset - 1).coerceAtLeast(0)
        while (start > 0 && isProviderChainChar(content[start - 1])) {
            start--
        }

        var end = offset.coerceAtMost(content.length)
        while (end < content.length && isProviderChainChar(content[end])) {
            end++
        }

        return start until end
    }

    private fun providerBaseBeforeModifier(
        content: String,
        identifiers: List<MatchResult>,
        modifierIndex: Int,
    ): String? {
        var current = identifiers[modifierIndex]
        for (index in modifierIndex - 1 downTo 0) {
            val previous = identifiers[index]
            val separator = content.substring(previous.range.last + 1, current.range.first)
            if (separator.trim() != ".") {
                return null
            }

            if (previous.value !in PROVIDER_MODIFIERS) {
                return previous.value
            }

            current = previous
        }

        return null
    }

    private fun PsiFile.isRelevantDartFile(): Boolean {
        val fileName = virtualFile?.name ?: name
        return fileName.endsWith(".dart") && !fileName.endsWith(".g.dart")
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

    private companion object {
        const val TOOL_WINDOW_ID = "Riverpod Graph"

    }
}

internal data class ProviderGraphSourceFile(
    val filePath: String,
    val content: String,
)

internal fun providerGraphEdgesForSourceFiles(
    sourceFiles: Collection<ProviderGraphSourceFile>,
    declarations: List<RiverpodProviderDeclaration>,
): List<RiverpodDependencyEdge> {
    val sourcesByPath = sourceFiles.distinctBy { it.filePath }.associateBy { it.filePath }
    val edges = declarations
        .mapTo(linkedSetOf()) { it.filePath }
        .flatMap { filePath ->
            val source = sourcesByPath[filePath] ?: return@flatMap emptyList()
            ProviderDependencyAnalyzer.analyzeFile(
                filePath = filePath,
                content = source.content,
                declarations = declarations,
            )
        }

    return ProviderDependencyAnalyzer.markCycles(edges).distinct()
}

val IDENTIFIER_REGEX = Regex($$"""[_$A-Za-z][_$A-Za-z0-9]*""")
val PROVIDER_MODIFIERS = setOf("future", "notifier", "select")

fun isProviderChainChar(char: Char): Boolean =
    char == '.' || char == '_' || char == '$' || char.isLetterOrDigit() || char.isWhitespace()
