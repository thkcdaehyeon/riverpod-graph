package com.ki960213.riverpodgraph.ui

import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.treeStructure.Tree
import com.ki960213.riverpodgraph.model.RiverpodProviderDeclaration
import java.awt.BorderLayout
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

/** 색인된 Riverpod 프로바이더를 소스 파일별로 묶어 보여주는 Swing 패널입니다. */
class ProvidersPanel : JPanel(BorderLayout()) {
    private val root = DefaultMutableTreeNode("Providers")
    private val treeModel = DefaultTreeModel(root)
    private val tree = Tree(treeModel)

    init {
        add(ScrollPaneFactory.createScrollPane(tree), BorderLayout.CENTER)
    }

    /** 표시 중인 프로바이더 목록을 지정된 선언 목록으로 교체합니다. */
    fun setProviders(declarations: List<RiverpodProviderDeclaration>) {
        val declarationsSnapshot = declarations.toList()
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater { setProviders(declarationsSnapshot) }
            return
        }

        root.removeAllChildren()

        declarationsSnapshot
            .sortedWith(compareBy<RiverpodProviderDeclaration> { it.filePath }.thenBy { it.line })
            .groupBy { it.filePath }
            .forEach { (filePath, providers) ->
                val fileNode = DefaultMutableTreeNode(filePath)
                providers.forEach { declaration ->
                    fileNode.add(DefaultMutableTreeNode(providerRow(declaration)))
                }
                root.add(fileNode)
            }

        treeModel.reload()
    }

    internal fun providerRows(): List<String> {
        if (SwingUtilities.isEventDispatchThread()) {
            return collectProviderRows()
        }

        var rows = emptyList<String>()
        SwingUtilities.invokeAndWait {
            rows = collectProviderRows()
        }
        return rows
    }

    /** 트리에 렌더링된 모든 프로바이더 행 문자열을 파일 순서대로 수집합니다. */
    private fun collectProviderRows(): List<String> = (0 until root.childCount).flatMap { fileIndex ->
        val fileNode = root.getChildAt(fileIndex) as DefaultMutableTreeNode
        (0 until fileNode.childCount).map { providerIndex ->
            fileNode.getChildAt(providerIndex).toString()
        }
    }

    /** 프로바이더 패널에 표시할 행 문자열을 만드는 헬퍼입니다. */
    companion object {
        /** 프로바이더 선언을 프로바이더 트리에 표시할 문자열로 포맷합니다. */
        fun providerRow(declaration: RiverpodProviderDeclaration): String {
            val privatePrefix = if (declaration.isPrivate) "private " else ""
            val signature = declaration.familySignature
                .takeIf { it.isNotBlank() }
                ?.let { "(${it.trim()})" }
                .orEmpty()
            val keepAliveSuffix = if (declaration.keepAlive) " keepAlive" else ""

            return "$privatePrefix${declaration.providerName}$signature : " +
                    "${declaration.returnType}$keepAliveSuffix - ${declaration.filePath}:${declaration.line}"
        }
    }
}
