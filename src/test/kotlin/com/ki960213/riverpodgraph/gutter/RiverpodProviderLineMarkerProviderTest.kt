package com.ki960213.riverpodgraph.gutter

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.FakePsiElement
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class RiverpodProviderLineMarkerProviderTest : BasePlatformTestCase() {
    private val provider = RiverpodProviderLineMarkerProvider()

    override fun setUp() {
        super.setUp()
        addRiverpodPubspec()
    }

    fun `test 정확한 Riverpod 어노테이션에 마커를 만든다`() {
        assertNotNull(provider.getLineMarkerInfo(psi("@riverpod")))
        assertNotNull(provider.getLineMarkerInfo(psi("@Riverpod")))
        assertNotNull(provider.getLineMarkerInfo(psi("@Riverpod(keepAlive: true)")))
    }

    fun `test 비슷한 어노테이션 이름에는 마커를 만들지 않는다`() {
        assertNull(provider.getLineMarkerInfo(psi("@RiverpodFake")))
        assertNull(provider.getLineMarkerInfo(psi("@riverpodFake")))
    }

    fun `test 부모나 파일 텍스트만으로는 마커를 만들지 않는다`() {
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

    fun `test 주석이나 문자열 안에는 마커를 만들지 않는다`() {
        assertNull(provider.getLineMarkerInfo(psi("@riverpod", parentText = "// @riverpod")))
        assertNull(provider.getLineMarkerInfo(psi("@Riverpod", parentText = "/* @Riverpod */")))
        assertNull(provider.getLineMarkerInfo(psi("@riverpod", parentText = "'@riverpod'")))
        assertNull(provider.getLineMarkerInfo(psi("@Riverpod", parentText = "\"@Riverpod\"")))
    }

    /** 테스트용 Dart 조각을 PSI 요소로 구성합니다. */
    private fun psi(text: String, parentText: String? = null): PsiElement {
        val containingFile = myFixture.configureByText("scratch.dart", parentText ?: text)
        val parent = parentText?.let { fakeElement(it, 0, null, containingFile) }
        return fakeElement(text, parentText?.indexOf(text)?.takeIf { it >= 0 } ?: 0, parent, containingFile)
    }

    /** 지정한 텍스트와 위치를 반환하는 가짜 PSI 요소를 만듭니다. */
    private fun fakeElement(text: String, startOffset: Int, parent: PsiElement?, containingFile: PsiFile): PsiElement =
        object : FakePsiElement() {
            override fun getParent(): PsiElement? = parent
            override fun getContainingFile(): PsiFile = containingFile
            override fun getProject(): Project = containingFile.project
            override fun getText(): String = text
            override fun getTextOffset(): Int = startOffset
            override fun getTextRange(): TextRange = TextRange.from(startOffset, text.length)
        }

    /** Riverpod 기능이 활성화되도록 테스트 프로젝트에 pubspec.yaml을 추가합니다. */
    private fun addRiverpodPubspec() {
        myFixture.addFileToProject(
            "pubspec.yaml",
            """
            name: test_app
            dependencies:
              riverpod_annotation: ^3.0.0
            """.trimIndent(),
        )
    }
}
