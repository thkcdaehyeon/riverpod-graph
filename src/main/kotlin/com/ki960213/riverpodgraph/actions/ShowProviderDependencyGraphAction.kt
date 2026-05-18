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
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.ki960213.riverpodgraph.activation.RiverpodActivationService
import com.ki960213.riverpodgraph.activation.RiverpodActiveSourceScope
import com.ki960213.riverpodgraph.analysis.ProviderDependencyAnalyzer
import com.ki960213.riverpodgraph.analysis.RefExtensionScanner
import com.ki960213.riverpodgraph.files.isRiverpodDartSourceFile
import com.ki960213.riverpodgraph.graph.RiverpodGraphService
import com.ki960213.riverpodgraph.model.RiverpodDependencyEdge
import com.ki960213.riverpodgraph.model.RiverpodProviderDeclaration
import com.ki960213.riverpodgraph.platform.launchRiverpodBackgroundTask
import com.ki960213.riverpodgraph.platform.smartCancellableReadAction
import com.ki960213.riverpodgraph.resolution.RiverpodProviderResolver
import com.ki960213.riverpodgraph.ui.DependencyGraphPanel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 편집기에서 선택한 Riverpod 프로바이더의 의존성 그래프를 표시합니다. */
class ShowProviderDependencyGraphAction : AnAction() {

    /** 액션 업데이트를 백그라운드 스레드에서 실행합니다. */
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    /** 선택한 프로바이더 그래프를 만들고 Riverpod Graph 도구 창에서 엽니다. */
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file = e.getData(CommonDataKeys.PSI_FILE) ?: return
        if (!file.isRiverpodDartSourceFile()) return
        if (!RiverpodActivationService.getInstance(project).isFileActive(file)) return

        val declaration = declarationFromContext(e) ?: return
        project.launchRiverpodBackgroundTask("Analyze Riverpod Provider Dependencies") {
            val edges = dependencyEdges(project, file, declaration)
            withContext(Dispatchers.EDT) {
                if (!project.isDisposed) {
                    showGraph(project, declaration.providerName, edges)
                }
            }
        }
    }

    /** Riverpod 프로바이더 심볼을 사용할 수 있을 때만 이 액션을 활성화합니다. */
    override fun update(e: AnActionEvent) {
        val file = e.getData(CommonDataKeys.PSI_FILE)
        val project = e.project
        val relevant = project != null &&
                file?.isRiverpodDartSourceFile() == true &&
                RiverpodActivationService.getInstance(project).isFileActive(file) &&
                declarationFromContext(e) != null

        e.presentation.isEnabled = relevant
        e.presentation.isVisible = relevant
    }

    /** 현재 액션 컨텍스트에서 선택된 프로바이더 선언을 찾습니다. */
    private fun declarationFromContext(e: AnActionEvent): RiverpodProviderDeclaration? {
        val project = e.project ?: return null
        val file = e.getData(CommonDataKeys.PSI_FILE) ?: return null
        val editor = e.getData(CommonDataKeys.EDITOR)
        val element = e.getData(CommonDataKeys.PSI_ELEMENT)
        val resolver = RiverpodProviderResolver(project)

        val symbolDeclaration = symbolCandidates(file, editor, element)
            .firstNotNullOfOrNull { symbol -> withAvailableIndex { resolver.findDeclaration(symbol) } }
        if (symbolDeclaration != null) {
            return symbolDeclaration
        }

        val declarations = withAvailableIndex {
            RiverpodActiveSourceScope.getInstance(project).providerDeclarations()
        }.orEmpty()
        return providerDeclarationForContext(
            content = file.text,
            caretOffset = editor?.caretModel?.offset,
            elementRange = element?.textRange?.let { range -> range.startOffset until range.endOffset },
            declarations = declarations,
        )
    }

    /** 도구 창의 그래프 패널에 프로바이더 의존성 그래프를 표시합니다. */
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

    /** 선택된 선언을 포함해 프로젝트 소스에서 프로바이더 의존성 간선을 계산합니다. */
    private fun dependencyEdges(
        project: Project,
        file: PsiFile,
        declaration: RiverpodProviderDeclaration,
    ): List<RiverpodDependencyEdge> = smartCancellableReadAction(project) {
        if (!file.isValid) {
            return@smartCancellableReadAction emptyList()
        }

        val declarations = RiverpodGraphService.getInstance(project)
            .providerDeclarationsInReadAction()
            .withDeclaration(declaration)
        val sourceFiles = providerSourceFilesInReadAction(project, file, declarations)

        providerGraphEdgesForSourceFiles(sourceFiles, declarations)
    }

    /** 현재 목록에 선택된 선언이 없을 때만 추가합니다. */
    private fun List<RiverpodProviderDeclaration>.withDeclaration(
        declaration: RiverpodProviderDeclaration,
    ): List<RiverpodProviderDeclaration> {
        val key = Triple(declaration.providerName, declaration.filePath, declaration.textOffset)
        if (any { Triple(it.providerName, it.filePath, it.textOffset) == key }) {
            return this
        }

        return this + declaration
    }

    /** 그래프 분석에 필요한 프로바이더 소스 파일과 내용을 수집합니다. */
    private fun providerSourceFilesInReadAction(
        project: Project,
        invocationFile: PsiFile,
        declarations: List<RiverpodProviderDeclaration>,
    ): List<ProviderGraphSourceFile> {
        val declarationPaths = declarations.mapTo(linkedSetOf()) { it.filePath }
        val invocationPath = invocationFile.virtualFile?.path ?: invocationFile.name
        val activeFilesByPath = RiverpodActiveSourceScope.getInstance(project)
            .activeDartFilesInReadAction()
            .associateBy { it.path }

        val analysisPaths = (declarationPaths + activeFilesByPath.keys).toCollection(linkedSetOf())
        return analysisPaths.mapNotNull { filePath ->
            val virtualFile = activeFilesByPath[filePath] ?: LocalFileSystem.getInstance().findFileByPath(filePath)
            val psiFile = virtualFile?.let { PsiManager.getInstance(project).findFile(it) }
            val content = psiFile?.text ?: virtualFile?.let { sourceText(project, it) }
                ?: invocationFile.text.takeIf { filePath == invocationPath }
                ?: return@mapNotNull null

            ProviderGraphSourceFile(filePath, content, psiFile ?: invocationFile.takeIf { filePath == invocationPath })
        }
    }

    /** PSI 또는 VFS에서 가상 파일의 텍스트를 안전하게 읽습니다. */
    private fun sourceText(project: Project, virtualFile: VirtualFile): String? =
        try {
            PsiManager.getInstance(project).findFile(virtualFile)?.text ?: VfsUtil.loadText(virtualFile)
        } catch (exception: ProcessCanceledException) {
            throw exception
        } catch (_: Exception) {
            null
        }

    /** 편집기 커서와 PSI 요소에서 프로바이더 심볼 후보를 모읍니다. */
    private fun symbolCandidates(
        file: PsiFile,
        editor: Editor?,
        element: PsiElement?,
    ): List<String> = buildList {
        editor?.let { symbolAt(file.text, it.caretModel.offset) }?.let(::add)
        element?.text?.takeIf { IDENTIFIER_REGEX.matches(it) }?.let(::add)
    }.distinct()

    /** 지정한 오프셋 주변의 프로바이더 심볼 또는 접근 체인의 기본 심볼을 찾습니다. */
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

    /** 프로바이더 접근 체인으로 볼 수 있는 텍스트 범위를 계산합니다. */
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

    /** `future` 같은 접근 수정자 앞에 있는 기본 프로바이더 심볼을 찾습니다. */
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

    /** 인덱스가 아직 준비되지 않은 경우 실패 대신 null을 반환합니다. */
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
    val psiFile: PsiFile? = null,
) {
    /** 텍스트 기반 테스트와 fallback 경로에서 사용하는 기존 생성자입니다. */
    constructor(filePath: String, content: String) : this(filePath, content, null)
}

internal fun providerGraphEdgesForSourceFiles(
    sourceFiles: Collection<ProviderGraphSourceFile>,
    declarations: List<RiverpodProviderDeclaration>,
): List<RiverpodDependencyEdge> {
    val sourcesByPath = sourceFiles.distinctBy { it.filePath }.associateBy { it.filePath }
    val providerNames = declarations.mapTo(linkedSetOf()) { it.providerName }
    val extensionDependencies = sourcesByPath.values.flatMap { source ->
        source.psiFile?.let { psiFile ->
            RefExtensionScanner.scan(
                filePath = source.filePath,
                file = psiFile,
                providerNames = providerNames,
            )
        } ?: RefExtensionScanner.scan(
            filePath = source.filePath,
            content = source.content,
            providerNames = providerNames,
        )
    }.distinct()
    val edges = declarations
        .mapTo(linkedSetOf()) { it.filePath }
        .flatMap { filePath ->
            val source = sourcesByPath[filePath] ?: return@flatMap emptyList()
            source.psiFile?.let { psiFile ->
                ProviderDependencyAnalyzer.analyzeFile(
                    filePath = filePath,
                    file = psiFile,
                    declarations = declarations,
                    extensionDependencies = extensionDependencies,
                )
            } ?: ProviderDependencyAnalyzer.analyzeFile(
                filePath = filePath,
                content = source.content,
                declarations = declarations,
                extensionDependencies = extensionDependencies,
            )
        }

    return ProviderDependencyAnalyzer.markCycles(edges).distinct()
}

internal fun providerDeclarationForContext(
    content: String,
    caretOffset: Int?,
    elementRange: IntRange?,
    declarations: List<RiverpodProviderDeclaration>,
): RiverpodProviderDeclaration? {
    caretOffset?.let { offset ->
        declarations.firstOrNull { declaration ->
            offset in declaration.sourceRange(content.length)
        }?.let { return it }
    }

    elementRange?.let { range ->
        declarations.firstOrNull { declaration ->
            declaration.textOffset in range
        }?.let { return it }
    }

    return null
}

/** 프로바이더 접근 체인을 스캔할 때 Dart 식별자를 찾습니다. */
internal val IDENTIFIER_REGEX = Regex($$"""[_$A-Za-z][_$A-Za-z0-9]*""")

/** 기본 프로바이더 심볼로 다시 해석되는 프로바이더 접근 수정자입니다. */
internal val PROVIDER_MODIFIERS = setOf("future", "notifier", "select")

/** 문자가 프로바이더 접근 체인 안에 나타날 수 있는지 반환합니다. */
internal fun isProviderChainChar(char: Char): Boolean =
    char == '.' || char == '_' || char == '$' || char.isLetterOrDigit() || char.isWhitespace()
