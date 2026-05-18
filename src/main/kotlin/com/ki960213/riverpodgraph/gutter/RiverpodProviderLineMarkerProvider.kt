package com.ki960213.riverpodgraph.gutter

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.PsiElement
import com.ki960213.riverpodgraph.activation.RiverpodActivationService
import java.util.*

/** 활성 Dart 파일의 Riverpod 프로바이더 어노테이션에 거터 마커를 추가합니다. */
class RiverpodProviderLineMarkerProvider : LineMarkerProvider {
    /** Riverpod 어노테이션 요소의 거터 마커를 반환합니다. */
    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        if (!isRiverpodAnnotationElement(element)) return null
        val file = element.containingFile ?: return null
        if (!RiverpodActivationService.getInstance(element.project).isFileActive(file)) return null

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

    /** PSI 요소가 거터 마커를 붙일 Riverpod 어노테이션인지 판별합니다. */
    private fun isRiverpodAnnotationElement(element: PsiElement): Boolean {
        val text = element.text
        if (!isRiverpodAnnotationText(text)) return false
        if (isCommentOrString(element)) return false
        if (hasCompleteAnnotationParent(element)) return false

        return true
    }

    /** 텍스트가 지원하는 Riverpod 어노테이션 표기인지 확인합니다. */
    private fun isRiverpodAnnotationText(text: String): Boolean {
        if (text.length > MAX_ANNOTATION_TEXT_LENGTH || text.any { it == '\n' || it == '\r' }) return false

        return text == "@riverpod" ||
                text == "@Riverpod" ||
                (text.startsWith("@Riverpod(") && text.endsWith(")"))
    }

    /** 요소가 주석이나 문자열 안에 있어 어노테이션으로 처리하면 안 되는지 확인합니다. */
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

    /** PSI 토큰 타입 이름으로 주석 또는 문자열 토큰 여부를 확인합니다. */
    private fun hasCommentOrStringToken(element: PsiElement?): Boolean {
        val tokenName = element?.node?.elementType?.toString()?.uppercase(Locale.US) ?: return false
        return tokenName.contains("COMMENT") || tokenName.contains("STRING")
    }

    /** 부모 요소가 전체 어노테이션을 이미 포함하는 경우 중복 마커를 막습니다. */
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
