package com.ki960213.riverpodgraph.ui

import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.platform.util.progress.reportRawProgress
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.content.ContentFactory
import com.intellij.util.indexing.FileBasedIndex
import com.ki960213.riverpodgraph.activation.RiverpodActivationService
import com.ki960213.riverpodgraph.index.NAME
import com.ki960213.riverpodgraph.index.RiverpodProviderIndexValue
import com.ki960213.riverpodgraph.model.RiverpodProviderDeclaration
import com.ki960213.riverpodgraph.platform.launchRiverpodBackgroundTask
import com.ki960213.riverpodgraph.platform.smartCancellableReadAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 활성화된 Riverpod 프로젝트에 Riverpod Graph 도구 창을 생성합니다. */
class RiverpodToolWindowFactory : ToolWindowFactory {
    /** 이 프로젝트에서 도구 창을 사용할 수 있는지 반환합니다. */
    override suspend fun isApplicableAsync(project: Project): Boolean =
        RiverpodActivationService.getInstance(project).isProjectActive()

    /** 도구 창 탭을 구성하고 색인된 프로바이더 로드를 시작합니다. */
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        if (toolWindow.contentManager.contentCount > 0) {
            return
        }

        val providersPanel = ProvidersPanel()

        val tabs = JBTabbedPane().apply {
            addTab("Providers", providersPanel)
            addTab("Dependencies", DependencyGraphPanel())
        }
        val content = ContentFactory.getInstance().createContent(
            tabs,
            "",
            false,
        )
        toolWindow.contentManager.addContent(content)
        loadProvidersInBackground(project, providersPanel)
    }

}

@Suppress("UnstableApiUsage")
private fun loadProvidersInBackground(project: Project, providersPanel: ProvidersPanel) {
    project.launchRiverpodBackgroundTask("Load Riverpod Providers") {
        val providers = reportRawProgress { reporter ->
            reporter.text("Loading Riverpod providers")
            val providers = loadProviders(project)
            reporter.fraction(1.0)
            providers
        }
        withContext(Dispatchers.EDT) {
            if (!project.isDisposed) {
                providersPanel.setProviders(providers)
            }
        }
    }
}

internal fun loadProviders(project: Project): List<RiverpodProviderDeclaration> =
    smartCancellableReadAction(project) {
        val index = FileBasedIndex.getInstance()
        val scope = GlobalSearchScope.projectScope(project)
        val values = index.getAllKeys(NAME, project)
            .flatMap { key -> index.getValues(NAME, key, scope) }

        providerDeclarationsFromIndexValues(values)
    }

internal fun providerDeclarationsFromIndexValues(
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
