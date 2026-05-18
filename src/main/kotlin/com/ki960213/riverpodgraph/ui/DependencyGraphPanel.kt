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

/** 선택된 Riverpod 프로바이더와 그 의존성을 표시하는 Swing 패널입니다. */
class DependencyGraphPanel : JPanel(BorderLayout()) {
    private val root = DefaultMutableTreeNode("Dependencies")
    private val treeModel = DefaultTreeModel(root)
    private val tree = Tree(treeModel)

    /** 이 패널에 현재 표시 중인 프로바이더 이름입니다. */
    var selectedProvider: String? = null
        private set

    init {
        add(ScrollPaneFactory.createScrollPane(tree), BorderLayout.CENTER)
    }

    /** 의존성 간선 없이 프로바이더 이름만 표시합니다. */
    fun showProvider(providerName: String) {
        showProviderGraph(providerName, emptyList())
    }

    /** 프로바이더에서 나가는 의존성 간선을 트리에 렌더링합니다. */
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

    /** 현재 트리 루트 아래에 렌더링된 의존성 행 문자열을 순서대로 수집합니다. */
    private fun collectDependencyRows(): List<String> {
        if (root.childCount == 0) {
            return emptyList()
        }

        val providerNode = root.getChildAt(0) as DefaultMutableTreeNode
        return (0 until providerNode.childCount).map { index ->
            providerNode.getChildAt(index).toString()
        }
    }

    /** 의존성 간선을 트리 행에 표시할 텍스트로 변환합니다. */
    private fun edgeRow(edge: RiverpodDependencyEdge): String {
        val usageLabel = edge.usageKind.name.lowercase(Locale.US).replace('_', ' ')
        val markerSuffix = edge.marker?.let { " [${it.label}]" }.orEmpty()
        return "${edge.toProvider} [$usageLabel]$markerSuffix"
    }

    /** 도구 창 UI에서 의존성 그래프 패널을 찾고 선택하는 유틸리티입니다. */
    companion object {
        /** 컴포넌트 트리 안에서 첫 번째 의존성 그래프 패널을 찾습니다. */
        fun findIn(component: Component?): DependencyGraphPanel? = when (component) {
            null -> null
            is DependencyGraphPanel -> component
            is JTabbedPane -> (0 until component.tabCount).firstNotNullOfOrNull { index ->
                findIn(component.getComponentAt(index))
            }

            is Container -> component.components.firstNotNullOfOrNull(::findIn)
            else -> null
        }

        /** 대상 의존성 그래프 패널을 포함하는 탭 패널을 찾습니다. */
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

        /** 대상 의존성 그래프 패널이 들어 있는 탭을 선택합니다. */
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

        /** 컨테이너의 자식 컴포넌트에서 대상 그래프 패널을 포함한 탭 패널을 찾습니다. */
        private fun findInChildren(component: Container, target: DependencyGraphPanel): JTabbedPane? =
            component.components.firstNotNullOfOrNull { child -> findTabbedPaneContaining(child, target) }

        /** 컴포넌트 하위 트리에 대상 컴포넌트가 포함되어 있는지 재귀적으로 확인합니다. */
        private fun componentContains(component: Component, target: Component): Boolean {
            if (component === target) {
                return true
            }

            return component is Container && component.components.any { child -> componentContains(child, target) }
        }
    }
}
