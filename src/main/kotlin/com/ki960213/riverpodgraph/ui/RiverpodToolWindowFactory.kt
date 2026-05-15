package com.ki960213.riverpodgraph.ui

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.ui.content.ContentFactory
import com.intellij.util.indexing.FileBasedIndex
import com.ki960213.riverpodgraph.activation.RiverpodActivationService
import com.ki960213.riverpodgraph.index.RiverpodProviderIndex
import com.ki960213.riverpodgraph.index.RiverpodProviderIndexValue
import com.ki960213.riverpodgraph.model.RiverpodProviderDeclaration
import javax.swing.JTabbedPane

class RiverpodToolWindowFactory : ToolWindowFactory {
    override suspend fun isApplicableAsync(project: Project): Boolean =
        RiverpodActivationService.getInstance(project).isProjectActive()

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        if (toolWindow.contentManager.contentCount > 0) {
            return
        }

        val providersPanel = ProvidersPanel()
        providersPanel.setProviders(loadProviders(project))

        val tabs = JTabbedPane().apply {
            addTab("Providers", providersPanel)
            addTab("Dependencies", DependencyGraphPanel())
        }
        val content = ContentFactory.getInstance().createContent(
            tabs,
            "",
            false,
        )
        toolWindow.contentManager.addContent(content)
    }

    companion object {
        internal fun loadProviders(project: Project): List<RiverpodProviderDeclaration> =
            ReadAction.compute<List<RiverpodProviderDeclaration>, RuntimeException> {
                val index = FileBasedIndex.getInstance()
                val scope = GlobalSearchScope.projectScope(project)
                val values = index.getAllKeys(RiverpodProviderIndex.NAME, project)
                    .flatMap { key -> index.getValues(RiverpodProviderIndex.NAME, key, scope) }

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
    }
}
