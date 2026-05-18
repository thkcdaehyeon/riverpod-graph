package com.ki960213.riverpodgraph.search

import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.util.Processor
import com.ki960213.riverpodgraph.activation.RiverpodActivationService
import com.ki960213.riverpodgraph.activation.RiverpodActiveSourceScope
import com.ki960213.riverpodgraph.analysis.ProviderUsageScanner
import com.ki960213.riverpodgraph.analysis.ProviderUsageScope
import com.ki960213.riverpodgraph.analysis.RefExtensionDependency
import com.ki960213.riverpodgraph.analysis.RefExtensionScanner
import com.ki960213.riverpodgraph.dart.isDartIdentifierPart
import com.ki960213.riverpodgraph.model.RiverpodProviderDeclaration

/** Dart 파일에서 인덱싱된 Riverpod 프로바이더 참조를 검색합니다. */
class RiverpodReferencesSearchExecutor : QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters>(true) {
    /** IntelliJ 참조 검색 쿼리에 Riverpod 프로바이더 사용 위치를 내보냅니다. */
    override fun processQuery(
        queryParameters: ReferencesSearch.SearchParameters,
        consumer: Processor<in PsiReference>
    ) {
        val element = queryParameters.elementToSearch
        val project = element.project
        val activationService = RiverpodActivationService.getInstance(project)
        val elementFile = element.containingFile
        if (elementFile != null) {
            if (!activationService.isFileActive(elementFile)) {
                return
            }
        } else if (!activationService.isProjectActive()) {
            return
        }

        val activeScope = RiverpodActiveSourceScope.getInstance(project)
        val searchScope = queryParameters.effectiveSearchScope
        val indexScope = searchScope as? GlobalSearchScope ?: GlobalSearchScope.projectScope(project)
        val providerTarget = providerTargetFor(activeScope, element, indexScope) ?: return
        val psiManager = PsiManager.getInstance(project)
        val extensionDependencies = extensionDependenciesFor(activeScope, psiManager, providerTarget, indexScope)
        val extensionDependenciesByFile = extensionDependencies.groupBy { it.filePath }
        val extensionMemberNames = extensionDependencies.mapTo(linkedSetOf()) { it.memberName() }

        for (file in dartFiles(activeScope, searchScope)) {
            ProgressManager.checkCanceled()
            val psiFile = psiManager.findFile(file) ?: continue
            val text = psiFile.text
            if (!mayContainUsage(text, providerTarget, extensionMemberNames)) {
                continue
            }

            val emittedOffsets = mutableSetOf<Int>()
            for (dependency in extensionDependenciesByFile[file.path].orEmpty()) {
                ProgressManager.checkCanceled()
                for (offset in dependency.referenceOffsetsFor(providerTarget.providerName)) {
                    ProgressManager.checkCanceled()
                    if (!emitReference(psiFile, offset, providerTarget.providerName, emittedOffsets, consumer)) {
                        return
                    }
                }
            }

            val usages = ProviderUsageScanner.scan(
                filePath = file.path,
                content = text,
                usageScope = ProviderUsageScope(
                    providerNames = setOf(providerTarget.providerName),
                    declarations = providerTarget.declarations,
                    extensionDependencies = extensionDependencies,
                ),
            )
            for (usage in usages) {
                ProgressManager.checkCanceled()
                if (!emitReference(psiFile, usage.textOffset, usage.providerName, emittedOffsets, consumer)) {
                    return
                }
            }
        }
    }

    /** 검색할 심볼에 대응하는 프로바이더 이름과 선언 정보를 인덱스에서 구성합니다. */
    private fun providerTargetFor(
        activeScope: RiverpodActiveSourceScope,
        element: PsiElement,
        scope: GlobalSearchScope,
    ): ProviderTarget? {
        for (symbol in symbolCandidates(element)) {
            val declarations = withAvailableIndex { activeScope.providerDeclarations(symbol, scope) }.orEmpty()
            if (declarations.isNotEmpty()) {
                return declarations.toProviderTarget()
            }
        }

        val declarationAtElement = declarationAtElement(activeScope, element, scope) ?: return null
        val declarations = withAvailableIndex {
            activeScope.providerDeclarations(declarationAtElement.providerName, scope)
        }.orEmpty()
            .ifEmpty { listOf(declarationAtElement) }

        return declarations.toProviderTarget()
    }

    /** 검색 대상 PSI가 식별자 leaf가 아닐 때도 원본 Provider 선언을 찾아냅니다. */
    private fun declarationAtElement(
        activeScope: RiverpodActiveSourceScope,
        element: PsiElement,
        scope: GlobalSearchScope,
    ): RiverpodProviderDeclaration? {
        val filePath = element.containingFile?.virtualFile?.path ?: element.containingFile?.name ?: return null
        val elementRange = element.textRange ?: return null

        return withAvailableIndex { activeScope.providerDeclarations(scope) }.orEmpty()
            .firstOrNull { declaration ->
                declaration.filePath == filePath && elementRange.contains(declaration.textOffset)
            }
    }

    /** PSI 이름과 짧은 텍스트에서 Provider lookup 후보 심볼을 만듭니다. */
    private fun symbolCandidates(element: PsiElement): List<String> = buildList {
        (element as? PsiNamedElement)?.name?.takeIf { IDENTIFIER_REGEX.matches(it) }?.let(::add)
        element.text.takeIf { IDENTIFIER_REGEX.matches(it) }?.let(::add)
    }.distinct()

    /** 같은 providerName을 가리키는 선언 목록을 검색 대상 모델로 변환합니다. */
    private fun List<RiverpodProviderDeclaration>.toProviderTarget(): ProviderTarget {
        val providerName = first().providerName
        return ProviderTarget(
            providerName = providerName,
            declarations = filter { declaration -> declaration.providerName == providerName },
        )
    }

    /** 검색 범위에 포함된 활성 Riverpod Dart 소스 파일을 나열합니다. */
    private fun dartFiles(
        activeScope: RiverpodActiveSourceScope,
        searchScope: SearchScope,
    ): Sequence<VirtualFile> {
        return activeScope.activeDartFiles(searchScope).asSequence()
    }

    /** 프로바이더를 감싼 ref 확장 멤버 의존성을 프로젝트 범위에서 수집합니다. */
    private fun extensionDependenciesFor(
        activeScope: RiverpodActiveSourceScope,
        psiManager: PsiManager,
        providerTarget: ProviderTarget,
        indexScope: GlobalSearchScope,
    ): List<RefExtensionDependency> {
        val dependencies = mutableListOf<RefExtensionDependency>()
        for (file in dartFiles(activeScope, indexScope)) {
            ProgressManager.checkCanceled()
            val psiFile = psiManager.findFile(file) ?: continue
            val text = psiFile.text
            if (!mayContainProviderSource(text, providerTarget)) {
                continue
            }

            dependencies += RefExtensionScanner.scan(
                filePath = file.path,
                content = text,
                providerNames = setOf(providerTarget.providerName),
            )
        }

        return dependencies.distinct()
    }

    /** 파일 텍스트가 직접 사용 또는 확장 멤버 사용 후보인지 빠르게 확인합니다. */
    private fun mayContainUsage(
        text: String,
        providerTarget: ProviderTarget,
        extensionMemberNames: Set<String>,
    ): Boolean =
        mayContainProviderSource(text, providerTarget) ||
                mayContainExtensionMember(text, extensionMemberNames)

    /** 파일 텍스트에 프로바이더 이름이나 직접 호출 가능한 원본 이름이 있는지 확인합니다. */
    private fun mayContainProviderSource(text: String, providerTarget: ProviderTarget): Boolean =
        text.contains(providerTarget.providerName) ||
                providerTarget.sourceNames.any { text.contains(it) }

    /** 파일 텍스트에 ref 확장 멤버 이름 중 하나가 포함되는지 확인합니다. */
    private fun mayContainExtensionMember(text: String, extensionMemberNames: Set<String>): Boolean {
        for (memberName in extensionMemberNames) {
            ProgressManager.checkCanceled()
            if (text.contains(memberName)) {
                return true
            }
        }

        return false
    }

    /** ref 확장 의존성의 멤버 식별자에서 실제 멤버 이름만 추출합니다. */
    private fun RefExtensionDependency.memberName(): String = memberId.substringAfterLast('.')

    /** 지정 프로바이더에 대해 참조로 보고할 텍스트 오프셋 목록을 반환합니다. */
    private fun RefExtensionDependency.referenceOffsetsFor(providerName: String): List<Int> {
        val providerOffsets = providerOffsetsByProvider[providerName].orEmpty()
        if (providerOffsets.isNotEmpty()) {
            return providerOffsets
        }

        return if (providerName in providerNames) listOf(textOffset) else emptyList()
    }

    /** 중복 오프셋을 건너뛰고 IntelliJ 참조 검색 소비자에게 참조를 전달합니다. */
    private fun emitReference(
        psiFile: PsiFile,
        textOffset: Int,
        providerName: String,
        emittedOffsets: MutableSet<Int>,
        consumer: Processor<in PsiReference>,
    ): Boolean {
        if (!emittedOffsets.add(textOffset)) {
            return true
        }

        val usageElement = psiFile.findElementAt(textOffset) ?: return true
        val reference = RiverpodReference(
            element = usageElement,
            rangeInElement = referenceRangeInElement(usageElement, textOffset),
            providerName = providerName,
        )
        return consumer.process(reference)
    }

    /** 실제 참조 식별자만 가리키도록 파일 offset을 PSI 요소 내부 범위로 변환합니다. */
    private fun referenceRangeInElement(element: PsiElement, textOffset: Int): TextRange {
        val relativeOffset = textOffset - element.textRange.startOffset
        if (relativeOffset !in 0 until element.textLength) {
            return TextRange(0, element.textLength)
        }

        var end = relativeOffset
        val text = element.text
        while (end < text.length && isDartIdentifierPart(text[end])) {
            end++
        }

        return TextRange(relativeOffset, end.coerceAtLeast(relativeOffset + 1))
    }

    /** 인덱스가 아직 준비되지 않은 경우 실패 대신 null을 반환합니다. */
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

    private data class ProviderTarget(
        val providerName: String,
        val declarations: List<RiverpodProviderDeclaration>,
    ) {
        val sourceNames: Set<String> = declarations.mapTo(linkedSetOf()) { declaration -> declaration.sourceName }
    }

}

internal val IDENTIFIER_REGEX = Regex($$"""[_$A-Za-z][_$A-Za-z0-9]*""")