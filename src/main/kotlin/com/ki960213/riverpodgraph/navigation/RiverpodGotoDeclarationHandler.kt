package com.ki960213.riverpodgraph.navigation

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.FakePsiElement
import com.ki960213.riverpodgraph.activation.RiverpodActivationService
import com.ki960213.riverpodgraph.model.RiverpodProviderDeclaration
import com.ki960213.riverpodgraph.parser.RiverpodAnnotationParser
import com.ki960213.riverpodgraph.resolution.RiverpodProviderResolver

class RiverpodGotoDeclarationHandler : GotoDeclarationHandler {
    override fun getGotoDeclarationTargets(sourceElement: PsiElement?, offset: Int, editor: Editor): Array<PsiElement>? {
        val element = sourceElement ?: return null
        val file = element.containingFile ?: return null
        if (!isDartFile(file)) {
            return null
        }
        if (!RiverpodActivationService.getInstance(element.project).isFileActive(file)) {
            return null
        }

        val symbol = symbolAt(element, offset) ?: return null
        if (!isGeneratedRiverpodSymbol(symbol)) {
            return null
        }

        val resolver = RiverpodProviderResolver(element.project)
        val indexedTargets = withAvailableIndex { resolver.findSourceElements(symbol) }.orEmpty()
        val currentFileTargets = resolveInContainingFileText(element, symbol)
        val targets = mergeCurrentFileTargets(
            currentFileTargets = currentFileTargets,
            indexedTargets = indexedTargets,
            containingFile = element.containingFile,
        )

        return targets.takeIf { it.isNotEmpty() }?.toTypedArray()
    }

    private fun resolveInContainingFileText(element: PsiElement, symbol: String): List<PsiElement> {
        val file = element.containingFile ?: return emptyList()
        val filePath = file.virtualFile?.path ?: file.name
        return RiverpodAnnotationParser.parse(filePath, file.text).filter {
            it.providerName == symbol || it.generatedSuperclassName == symbol || it.sourceName == symbol
        }.map { declaration ->
            targetElement(file, declaration)
        }
    }

    private fun targetElement(file: PsiFile, declaration: RiverpodProviderDeclaration): PsiElement {
        val target = file.findElementAt(declaration.textOffset)
        if (target?.text == declaration.sourceName) {
            return target
        }

        return TextOffsetPsiElement(file, declaration.sourceName, declaration.textOffset)
    }

    private fun isDartFile(file: PsiFile): Boolean {
        val fileName = file.virtualFile?.name ?: file.name
        return fileName.endsWith(".dart")
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

    private fun isGeneratedRiverpodSymbol(symbol: String): Boolean =
        symbol.endsWith("Provider") || symbol.startsWith("_${'$'}")

    private fun symbolAt(element: PsiElement, offset: Int): String? {
        val lookupOffset = when {
            element.textRange.containsOffset(offset - 1) -> offset - 1
            element.textRange.containsOffset(offset) -> offset
            else -> element.textRange.startOffset
        }

        return sequenceOf(element, element.parent)
            .filterNotNull()
            .mapNotNull { candidate -> identifierAt(candidate, lookupOffset) }
            .firstOrNull()
    }

    private fun identifierAt(element: PsiElement, offset: Int): String? {
        val textRange = element.textRange
        if (!textRange.containsOffset(offset)) {
            return null
        }

        val text = element.text
        if (IDENTIFIER_REGEX.matches(text)) {
            return text.takeIf { isGeneratedRiverpodSymbol(it) }
        }

        val relativeOffset = offset - textRange.startOffset
        val identifiers = IDENTIFIER_REGEX.findAll(text).toList()
        val identifierIndex = identifiers.indexOfFirst { relativeOffset in it.range }
        if (identifierIndex < 0) {
            return null
        }

        val identifier = identifiers[identifierIndex]
        if (isGeneratedRiverpodSymbol(identifier.value)) {
            return identifier.value
        }

        return providerBaseBeforeModifier(text, identifiers, identifierIndex)
    }

    private fun providerBaseBeforeModifier(
        text: String,
        identifiers: List<MatchResult>,
        modifierIndex: Int,
    ): String? {
        var current = identifiers[modifierIndex]
        if (current.value !in PROVIDER_MODIFIERS) {
            return null
        }

        for (index in modifierIndex - 1 downTo 0) {
            val previous = identifiers[index]
            val separator = text.substring(previous.range.last + 1, current.range.first)
            if (separator.trim() != ".") {
                return null
            }

            if (isGeneratedRiverpodSymbol(previous.value)) {
                return previous.value
            }
            if (previous.value !in PROVIDER_MODIFIERS) {
                return null
            }

            current = previous
        }

        return null
    }

    private companion object {
        val IDENTIFIER_REGEX = Regex("""[_${'$'}A-Za-z][_${'$'}A-Za-z0-9]*""")
        val PROVIDER_MODIFIERS = setOf("future", "notifier", "select")
    }

    private class TextOffsetPsiElement(
        private val file: PsiFile,
        private val sourceName: String,
        private val offset: Int,
    ) : FakePsiElement() {
        override fun getParent(): PsiElement = file
        override fun getContainingFile(): PsiFile = file
        override fun getText(): String = sourceName
        override fun getName(): String = sourceName
        override fun getTextOffset(): Int = offset
        override fun getTextRange(): TextRange = TextRange.from(offset, sourceName.length)
        override fun isValid(): Boolean = file.isValid
        override fun canNavigate(): Boolean = file.virtualFile != null
        override fun canNavigateToSource(): Boolean = canNavigate()

        override fun navigate(requestFocus: Boolean) {
            val virtualFile = file.virtualFile ?: return
            OpenFileDescriptor(file.project, virtualFile, offset).navigate(requestFocus)
        }
    }
}

internal fun mergeCurrentFileTargets(
    currentFileTargets: List<PsiElement>,
    indexedTargets: List<PsiElement>,
    containingFile: PsiFile?,
): List<PsiElement> {
    val sourceFile = containingFile?.virtualFile
    val orderedTargets = buildList {
        addAll(currentFileTargets)
        addAll(
            indexedTargets.sortedBy { target ->
                if (sourceFile != null && target.containingFile?.virtualFile == sourceFile) 0 else 1
            },
        )
    }
    val byLocation = linkedMapOf<TargetLocation, PsiElement>()
    for (target in orderedTargets) {
        byLocation.putIfAbsent(targetLocation(target), target)
    }

    return byLocation.values.toList()
}

private data class TargetLocation(
    val filePath: String?,
    val textRange: TextRange,
    val textOffset: Int,
)

private fun targetLocation(target: PsiElement): TargetLocation =
    TargetLocation(
        filePath = target.containingFile?.virtualFile?.path ?: target.containingFile?.name,
        textRange = target.textRange,
        textOffset = target.textOffset,
    )
