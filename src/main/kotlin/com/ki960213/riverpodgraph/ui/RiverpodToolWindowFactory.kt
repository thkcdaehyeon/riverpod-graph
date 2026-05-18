package com.ki960213.riverpodgraph.ui

import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.platform.util.progress.reportRawProgress
import com.intellij.ui.content.ContentFactory
import com.ki960213.riverpodgraph.activation.RiverpodActivationService
import com.ki960213.riverpodgraph.activation.RiverpodActiveSourceScope
import com.ki960213.riverpodgraph.model.RiverpodProviderDeclaration
import com.ki960213.riverpodgraph.platform.launchRiverpodBackgroundTask
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

        val toolWindowContent = RiverpodToolWindowContent()
        val content = ContentFactory.getInstance().createContent(
            toolWindowContent.component,
            "",
            false,
        )
        toolWindow.contentManager.addContent(content)
        loadProvidersInBackground(project, toolWindowContent)
    }

}

@Suppress("UnstableApiUsage")
/** 백그라운드 작업에서 프로바이더 인덱스를 읽어 프로바이더 패널에 반영합니다. */
private fun loadProvidersInBackground(project: Project, toolWindowContent: RiverpodToolWindowContent) {
    project.launchRiverpodBackgroundTask("Load Riverpod Providers") {
        val providers = reportRawProgress { reporter ->
            reporter.text("Loading Riverpod providers")
            val providers = loadProviders(project)
            reporter.fraction(1.0)
            providers
        }
        withContext(Dispatchers.EDT) {
            if (!project.isDisposed) {
                toolWindowContent.setProviders(providers)
            }
        }
    }
}

internal fun loadProviders(project: Project): List<RiverpodProviderDeclaration> =
    RiverpodActiveSourceScope.getInstance(project).providerDeclarations()
