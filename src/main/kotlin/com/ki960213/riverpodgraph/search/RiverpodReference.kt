package com.ki960213.riverpodgraph.search

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReferenceBase
import com.ki960213.riverpodgraph.resolution.RiverpodProviderResolver

class RiverpodReference(
    element: PsiElement,
    rangeInElement: TextRange,
    val providerName: String,
) : PsiReferenceBase<PsiElement>(element, rangeInElement, true) {
    override fun resolve(): PsiElement? = RiverpodProviderResolver(element.project).findSourceElement(providerName)
}
