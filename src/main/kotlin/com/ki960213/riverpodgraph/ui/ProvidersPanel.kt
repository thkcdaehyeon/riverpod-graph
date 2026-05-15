package com.ki960213.riverpodgraph.ui

import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.treeStructure.Tree
import com.ki960213.riverpodgraph.model.RiverpodProviderDeclaration
import java.awt.BorderLayout
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

class ProvidersPanel : JPanel(BorderLayout()) {
    private val root = DefaultMutableTreeNode("Providers")
    private val treeModel = DefaultTreeModel(root)
    private val tree = Tree(treeModel)

    init {
        add(ScrollPaneFactory.createScrollPane(tree), BorderLayout.CENTER)
    }

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

    private fun collectProviderRows(): List<String> = (0 until root.childCount).flatMap { fileIndex ->
        val fileNode = root.getChildAt(fileIndex) as DefaultMutableTreeNode
        (0 until fileNode.childCount).map { providerIndex ->
            fileNode.getChildAt(providerIndex).toString()
        }
    }

    companion object {
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
