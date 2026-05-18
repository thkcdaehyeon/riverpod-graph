package com.ki960213.riverpodgraph.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.treeStructure.Tree
import com.ki960213.riverpodgraph.analysis.WidgetDependencyResult
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

/** Riverpod 위젯이 사용하는 프로바이더와 자식 위젯을 표시하는 대화상자입니다. */
class WidgetDependencyDialog(
    project: Project?,
    private val result: WidgetDependencyResult,
) : DialogWrapper(project, false) {
    init {
        title = "Riverpod Widget Dependencies"
        init()
    }

    /** 위젯 의존성 분석 결과를 보여주는 트리 뷰를 구성합니다. */
    override fun createCenterPanel(): JComponent {
        val root = DefaultMutableTreeNode(result.widgetName)
        val providers = DefaultMutableTreeNode("Providers")
        val children = DefaultMutableTreeNode("Child Widgets")

        result.providerNames.forEach { providerName ->
            providers.add(DefaultMutableTreeNode(providerName))
        }
        result.childWidgets.forEach { child ->
            val markerSuffix = child.marker?.let { " [${it.label}]" }.orEmpty()
            children.add(DefaultMutableTreeNode("${child.name}$markerSuffix"))
        }

        root.add(providers)
        root.add(children)

        return ScrollPaneFactory.createScrollPane(Tree(DefaultTreeModel(root))).apply {
            preferredSize = Dimension(480, 360)
        }
    }
}
