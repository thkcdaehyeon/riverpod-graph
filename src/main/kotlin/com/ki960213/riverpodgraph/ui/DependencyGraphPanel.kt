package com.ki960213.riverpodgraph.ui

import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.treeStructure.Tree
import com.ki960213.riverpodgraph.model.RiverpodDependencyEdge
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Container
import java.util.*
import javax.swing.JPanel
import javax.swing.JTabbedPane
import javax.swing.SwingUtilities
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

class DependencyGraphPanel : JPanel(BorderLayout()) {
    private val root = DefaultMutableTreeNode("Dependencies")
    private val treeModel = DefaultTreeModel(root)
    private val tree = Tree(treeModel)

    var selectedProvider: String? = null
        private set

    init {
        add(ScrollPaneFactory.createScrollPane(tree), BorderLayout.CENTER)
    }

    fun showProvider(providerName: String) {
        showProviderGraph(providerName, emptyList())
    }

    fun showProviderGraph(providerName: String, edges: List<RiverpodDependencyEdge>) {
        val edgeSnapshot = edges.toList()
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeAndWait { showProviderGraph(providerName, edgeSnapshot) }
            return
        }

        selectedProvider = providerName
        root.removeAllChildren()

        val providerNode = DefaultMutableTreeNode(providerName)
        edgeSnapshot
            .filter { it.fromProvider == providerName }
            .sortedWith(
                compareBy<RiverpodDependencyEdge> { it.toProvider }
                    .thenBy { it.usageKind.name }
                    .thenBy { it.marker?.label.orEmpty() },
            )
            .forEach { edge ->
                providerNode.add(DefaultMutableTreeNode(edgeRow(edge)))
            }

        root.add(providerNode)
        treeModel.reload()
        tree.expandRow(0)
        tree.expandRow(1)
    }

    internal fun dependencyRows(): List<String> {
        if (SwingUtilities.isEventDispatchThread()) {
            return collectDependencyRows()
        }

        var rows = emptyList<String>()
        SwingUtilities.invokeAndWait {
            rows = collectDependencyRows()
        }
        return rows
    }

    private fun collectDependencyRows(): List<String> {
        if (root.childCount == 0) {
            return emptyList()
        }

        val providerNode = root.getChildAt(0) as DefaultMutableTreeNode
        return (0 until providerNode.childCount).map { index ->
            providerNode.getChildAt(index).toString()
        }
    }

    private fun edgeRow(edge: RiverpodDependencyEdge): String {
        val usageLabel = edge.usageKind.name.lowercase(Locale.US).replace('_', ' ')
        val markerSuffix = edge.marker?.let { " [${it.label}]" }.orEmpty()
        return "${edge.toProvider} [$usageLabel]$markerSuffix"
    }

    companion object {
        fun findIn(component: Component?): DependencyGraphPanel? = when (component) {
            null -> null
            is DependencyGraphPanel -> component
            is JTabbedPane -> (0 until component.tabCount).firstNotNullOfOrNull { index ->
                findIn(component.getComponentAt(index))
            }

            is Container -> component.components.firstNotNullOfOrNull(::findIn)
            else -> null
        }

        fun findTabbedPaneContaining(
            component: Component?,
            target: DependencyGraphPanel? = findIn(component),
        ): JTabbedPane? {
            val graphPanel = target ?: return null

            return when (component) {
                null -> null
                is JTabbedPane -> {
                    if ((0 until component.tabCount).any { index ->
                            componentContains(component.getComponentAt(index), graphPanel)
                        }
                    ) {
                        component
                    } else {
                        findInChildren(component, graphPanel)
                    }
                }

                is Container -> findInChildren(component, graphPanel)
                else -> null
            }
        }

        fun selectTabContaining(
            component: Component?,
            target: DependencyGraphPanel? = findIn(component),
        ): Boolean {
            if (!SwingUtilities.isEventDispatchThread()) {
                var selected = false
                SwingUtilities.invokeAndWait {
                    selected = selectTabContaining(component, target)
                }
                return selected
            }

            val graphPanel = target ?: return false
            val tabbedPane = findTabbedPaneContaining(component, graphPanel) ?: return false
            val tabIndex = (0 until tabbedPane.tabCount).firstOrNull { index ->
                componentContains(tabbedPane.getComponentAt(index), graphPanel)
            } ?: return false

            tabbedPane.selectedIndex = tabIndex
            return true
        }

        private fun findInChildren(component: Container, target: DependencyGraphPanel): JTabbedPane? =
            component.components.firstNotNullOfOrNull { child -> findTabbedPaneContaining(child, target) }

        private fun componentContains(component: Component, target: Component): Boolean {
            if (component === target) {
                return true
            }

            return component is Container && component.components.any { child -> componentContains(child, target) }
        }
    }
}
