package com.ki960213.riverpodgraph.gutter

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.PsiElement
import java.util.Locale

class RiverpodProviderLineMarkerProvider : LineMarkerProvider {
    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        if (!isRiverpodAnnotationElement(element)) return null

        return LineMarkerInfo(
            element,
            element.textRange,
            AllIcons.Nodes.Property,
            { "Riverpod provider" },
            null,
            GutterIconRenderer.Alignment.CENTER,
            { "Riverpod provider" },
        )
    }

    private fun isRiverpodAnnotationElement(element: PsiElement): Boolean {
        val text = element.text
        if (!isRiverpodAnnotationText(text)) return false
        if (isCommentOrString(element)) return false
        if (hasCompleteAnnotationParent(element)) return false

        return true
    }

    private fun isRiverpodAnnotationText(text: String): Boolean {
        if (text.length > MAX_ANNOTATION_TEXT_LENGTH || text.any { it == '\n' || it == '\r' }) return false

        return text == "@riverpod" ||
            text == "@Riverpod" ||
            (text.startsWith("@Riverpod(") && text.endsWith(")"))
    }

    private fun isCommentOrString(element: PsiElement): Boolean {
        if (hasCommentOrStringToken(element)) return true
        if (hasCommentOrStringToken(element.parent)) return true

        val parentText = element.parent?.text?.trimStart() ?: return false
        return parentText.startsWith("//") ||
            parentText.startsWith("/*") ||
            parentText.startsWith("'") ||
            parentText.startsWith("\"") ||
            parentText.startsWith("r'") ||
            parentText.startsWith("r\"")
    }

    private fun hasCommentOrStringToken(element: PsiElement?): Boolean {
        val tokenName = element?.node?.elementType?.toString()?.uppercase(Locale.US) ?: return false
        return tokenName.contains("COMMENT") || tokenName.contains("STRING")
    }

    private fun hasCompleteAnnotationParent(element: PsiElement): Boolean {
        val parent = element.parent ?: return false
        if (parent.textRange.startOffset != element.textRange.startOffset) return false
        if (parent.text == element.text) return false

        return isRiverpodAnnotationText(parent.text)
    }

    private companion object {
        const val MAX_ANNOTATION_TEXT_LENGTH = 120
    }
}
