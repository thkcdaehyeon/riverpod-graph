package com.ki960213.riverpodgraph.search

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReferenceBase
import com.ki960213.riverpodgraph.resolution.RiverpodProviderResolver

/** Riverpod 프로바이더 사용 위치에서 원본 선언으로 향하는 PSI 참조입니다. */
class RiverpodReference(
    element: PsiElement,
    rangeInElement: TextRange,
    /** 이 참조가 해석하는 프로바이더 이름입니다. */
    val providerName: String,
) : PsiReferenceBase<PsiElement>(element, rangeInElement, true) {
    /** 이 사용 위치를 인덱싱된 Riverpod 프로바이더 원본 요소로 해석합니다. */
    override fun resolve(): PsiElement? = RiverpodProviderResolver(element.project).findSourceElement(providerName)
}
