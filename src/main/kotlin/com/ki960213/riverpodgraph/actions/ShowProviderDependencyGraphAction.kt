package com.ki960213.riverpodgraph.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.ki960213.riverpodgraph.model.RiverpodProviderDeclaration
import com.ki960213.riverpodgraph.resolution.RiverpodProviderResolver
import com.ki960213.riverpodgraph.ui.DependencyGraphPanel

class ShowProviderDependencyGraphAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file = e.getData(CommonDataKeys.PSI_FILE) ?: return
        if (!file.isRelevantDartFile()) {
            return
        }

        val declaration = declarationFromContext(e) ?: return
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID) ?: return

        toolWindow.show(
            Runnable {
                DependencyGraphPanel.findIn(toolWindow.component)?.showProvider(declaration.providerName)
            },
        )
    }

    override fun update(e: AnActionEvent) {
        val file = e.getData(CommonDataKeys.PSI_FILE)
        val editor = e.getData(CommonDataKeys.EDITOR)
        val element = e.getData(CommonDataKeys.PSI_ELEMENT)
        val relevant = e.project != null && file?.isRelevantDartFile() == true &&
            symbolCandidates(file, editor, element).isNotEmpty()

        e.presentation.isEnabled = relevant
        e.presentation.isVisible = relevant
    }

    private fun declarationFromContext(e: AnActionEvent): RiverpodProviderDeclaration? {
        val project = e.project ?: return null
        val file = e.getData(CommonDataKeys.PSI_FILE) ?: return null
        val editor = e.getData(CommonDataKeys.EDITOR)
        val element = e.getData(CommonDataKeys.PSI_ELEMENT)
        val resolver = RiverpodProviderResolver(project)

        return symbolCandidates(file, editor, element)
            .firstNotNullOfOrNull { symbol -> withAvailableIndex { resolver.findDeclaration(symbol) } }
    }

    private fun symbolCandidates(
        file: PsiFile,
        editor: Editor?,
        element: PsiElement?,
    ): List<String> = buildList {
        editor?.let { symbolAt(file.text, it.caretModel.offset) }?.let(::add)
        element?.text?.takeIf { IDENTIFIER_REGEX.matches(it) }?.let(::add)
    }.distinct()

    private fun symbolAt(content: String, offset: Int): String? {
        val lookupOffset = offset.coerceIn(0, content.length)
        val span = symbolSpan(content, lookupOffset)
        val localContent = content.substring(span)
        val localOffset = lookupOffset - span.first
        val identifiers = IDENTIFIER_REGEX.findAll(localContent).toList()
        val identifierIndex = identifiers.indexOfFirst { match ->
            localOffset in match.range || localOffset - 1 in match.range
        }
        if (identifierIndex < 0) {
            return null
        }

        val identifier = identifiers[identifierIndex]
        if (identifier.value !in PROVIDER_MODIFIERS) {
            return identifier.value
        }

        return providerBaseBeforeModifier(localContent, identifiers, identifierIndex)
    }

    private fun symbolSpan(content: String, offset: Int): IntRange {
        var start = (offset - 1).coerceAtLeast(0)
        while (start > 0 && isProviderChainChar(content[start - 1])) {
            start--
        }

        var end = offset.coerceAtMost(content.length)
        while (end < content.length && isProviderChainChar(content[end])) {
            end++
        }

        return start until end
    }

    private fun providerBaseBeforeModifier(
        content: String,
        identifiers: List<MatchResult>,
        modifierIndex: Int,
    ): String? {
        var current = identifiers[modifierIndex]
        for (index in modifierIndex - 1 downTo 0) {
            val previous = identifiers[index]
            val separator = content.substring(previous.range.last + 1, current.range.first)
            if (separator.trim() != ".") {
                return null
            }

            if (previous.value !in PROVIDER_MODIFIERS) {
                return previous.value
            }

            current = previous
        }

        return null
    }

    private fun PsiFile.isRelevantDartFile(): Boolean {
        val fileName = virtualFile?.name ?: name
        return fileName.endsWith(".dart") && !fileName.endsWith(".g.dart")
    }

    private fun <T> withAvailableIndex(action: () -> T): T? {
        return try {
            action()
        } catch (exception: IllegalStateException) {
            if (exception.message?.contains("Index is not created") == true) {
                null
            } else {
                throw exception
            }
        }
    }

    private companion object {
        const val TOOL_WINDOW_ID = "Riverpod Graph"
        val IDENTIFIER_REGEX = Regex("""[_${'$'}A-Za-z][_${'$'}A-Za-z0-9]*""")
        val PROVIDER_MODIFIERS = setOf("future", "notifier", "select")

        fun isProviderChainChar(char: Char): Boolean =
            char == '.' || char == '_' || char == '$' || char.isLetterOrDigit() || char.isWhitespace()
    }
}
