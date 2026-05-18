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
import com.ki960213.riverpodgraph.dart.codeOnly
import com.ki960213.riverpodgraph.dart.dartCodeMask
import com.ki960213.riverpodgraph.files.isRiverpodDartSourceFile
import com.ki960213.riverpodgraph.index.RIVERPOD_PROVIDER_INDEX_NAME
import com.ki960213.riverpodgraph.index.providerDeclarationsFromIndexValues
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
        if (!file.isRiverpodDartSourceFile()) {
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
                file?.isRiverpodDartSourceFile() == true &&
                RiverpodActivationService.getInstance(project).isFileActive(file)
        e.presentation.isEnabled = relevant
        e.presentation.isVisible = relevant
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
            .filter { file -> file.isRiverpodDartSourceFile() }
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

}

internal fun fallbackProviderNames(content: String): Set<String> =
    providerRegex.findAll(codeOnly(content, dartCodeMask(content))).map { it.value }.toSet()

private val providerRegex = Regex("""[A-Za-z_$][\w$]*Provider""")
