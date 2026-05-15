package com.ki960213.riverpodgraph.ui

import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.treeStructure.Tree
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Container
import javax.swing.JTabbedPane
import javax.swing.JPanel
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
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater { showProvider(providerName) }
            return
        }

        selectedProvider = providerName
        root.removeAllChildren()
        root.add(DefaultMutableTreeNode(providerName))
        treeModel.reload()
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
