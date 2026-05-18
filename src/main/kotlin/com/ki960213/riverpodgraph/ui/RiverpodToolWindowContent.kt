package com.ki960213.riverpodgraph.ui

import com.intellij.ui.components.JBTabbedPane
import com.ki960213.riverpodgraph.graph.RiverpodProviderSnapshot
import com.ki960213.riverpodgraph.model.RiverpodProviderDeclaration
import javax.swing.JComponent

/** Riverpod Graph 도구 창의 탭 구성과 패널 갱신 인터페이스입니다. */
internal class RiverpodToolWindowContent(
    private val providersPanel: ProvidersPanel = ProvidersPanel(),
    dependencyGraphPanel: DependencyGraphPanel = DependencyGraphPanel(),
) {
    private val tabs = JBTabbedPane().apply {
        addTab(PROVIDERS_TAB_TITLE, providersPanel)
        addTab(DEPENDENCIES_TAB_TITLE, dependencyGraphPanel)
    }

    /** IntelliJ ToolWindow content로 등록할 Swing 컴포넌트입니다. */
    val component: JComponent = tabs

    /** 표시 중인 Provider 선언을 Providers 탭에 반영합니다. */
    fun setProviders(declarations: List<RiverpodProviderDeclaration>) {
        providersPanel.setProviders(declarations)
    }

    /** 표시 중인 Provider 상태와 선언을 Providers 탭에 반영합니다. */
    fun setProviderSnapshot(snapshot: RiverpodProviderSnapshot) {
        providersPanel.setSnapshot(snapshot)
    }

    /** 현재 탭 제목을 순서대로 반환합니다. */
    internal fun tabTitles(): List<String> =
        (0 until tabs.tabCount).map { index -> tabs.getTitleAt(index) }

    private companion object {
        const val PROVIDERS_TAB_TITLE = "Providers"
        const val DEPENDENCIES_TAB_TITLE = "Dependencies"
    }
}
