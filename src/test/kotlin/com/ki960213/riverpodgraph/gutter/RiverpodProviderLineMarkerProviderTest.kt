package com.ki960213.riverpodgraph.gutter

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.FakePsiElement
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class RiverpodProviderLineMarkerProviderTest : BasePlatformTestCase() {
    private val provider = RiverpodProviderLineMarkerProvider()

    fun testCreatesMarkerForExactRiverpodAnnotations() {
        assertNotNull(provider.getLineMarkerInfo(psi("@riverpod")))
        assertNotNull(provider.getLineMarkerInfo(psi("@Riverpod")))
        assertNotNull(provider.getLineMarkerInfo(psi("@Riverpod(keepAlive: true)")))
    }

    fun testDoesNotCreateMarkerForSimilarAnnotationNames() {
        assertNull(provider.getLineMarkerInfo(psi("@RiverpodFake")))
        assertNull(provider.getLineMarkerInfo(psi("@riverpodFake")))
    }

    fun testDoesNotCreateMarkerForParentOrFileText() {
        assertNull(
            provider.getLineMarkerInfo(
                psi(
                    """
                    @riverpod
                    int counter(Ref ref) => 0;
                    """.trimIndent(),
                ),
            ),
        )
    }

    fun testDoesNotCreateMarkerInsideCommentOrString() {
        assertNull(provider.getLineMarkerInfo(psi("@riverpod", parentText = "// @riverpod")))
        assertNull(provider.getLineMarkerInfo(psi("@Riverpod", parentText = "/* @Riverpod */")))
        assertNull(provider.getLineMarkerInfo(psi("@riverpod", parentText = "'@riverpod'")))
        assertNull(provider.getLineMarkerInfo(psi("@Riverpod", parentText = "\"@Riverpod\"")))
    }

    private fun psi(text: String, parentText: String? = null): PsiElement {
        val containingFile = myFixture.configureByText("scratch.txt", parentText ?: text)
        val parent = parentText?.let { fakeElement(it, 0, null, containingFile) }
        return fakeElement(text, parentText?.indexOf(text)?.takeIf { it >= 0 } ?: 0, parent, containingFile)
    }

    private fun fakeElement(text: String, startOffset: Int, parent: PsiElement?, containingFile: PsiFile): PsiElement =
        object : FakePsiElement() {
            override fun getParent(): PsiElement? = parent
            override fun getContainingFile(): PsiFile = containingFile
            override fun getText(): String = text
            override fun getTextOffset(): Int = startOffset
            override fun getTextRange(): TextRange = TextRange.from(startOffset, text.length)
        }
}
