package com.ki960213.riverpodgraph.analysis

import com.intellij.psi.PsiFile
import com.ki960213.riverpodgraph.dart.*
import com.ki960213.riverpodgraph.model.RiverpodMarker
import com.ki960213.riverpodgraph.model.RiverpodProviderDeclaration

/**
 * 선택된 위젯에서 감지된 프로바이더 및 자식 위젯 의존성입니다.
 */
data class WidgetDependencyResult(
    /** 의존성 스캔 대상으로 선택된 위젯 클래스입니다. */
    val widgetName: String,

    /** 위젯 스캔 범위 안에서 사용된 프로바이더 목록입니다. */
    val providerNames: List<String>,

    /** 스캔 범위 안에서 발견된 자식 위젯 생성자 호출입니다. */
    val childWidgets: List<WidgetChildCandidate>,
)

/**
 * 위젯 의존성 분석 중 발견된 자식 위젯 생성자 호출입니다.
 */
data class WidgetChildCandidate(
    /** 위젯 생성자 클래스 이름입니다. */
    val name: String,

    /** 반복문, 콜백, 조건문에 대한 컨텍스트 표시자입니다. */
    val marker: RiverpodMarker?,

    /** 파일 안에서 생성자 호출의 오프셋입니다. */
    val textOffset: Int,
)

/**
 * Flutter 위젯에서 Riverpod 프로바이더 사용과 자식 위젯 호출을 분석합니다.
 */
object WidgetDependencyAnalyzer {
    /**
     * [caretOffset]으로 선택된 위젯 또는 [content]의 첫 번째 위젯에 대한 의존성을 반환합니다.
     */
    fun analyze(
        filePath: String,
        content: String,
        providerNames: Set<String>,
        caretOffset: Int? = null,
        declarations: List<RiverpodProviderDeclaration> = emptyList(),
        extensionDependencies: List<RefExtensionDependency> = emptyList(),
    ): WidgetDependencyResult {
        if (content.isEmpty()) {
            return WidgetDependencyResult(fallbackWidgetName(filePath), emptyList(), emptyList())
        }

        val codeMask = dartCodeMask(content)
        val code = codeOnly(content, codeMask)
        val widgetContexts = widgetContexts(content, codeMask, code)
        val selectedWidget = selectWidget(widgetContexts, caretOffset, content, codeMask)
        val widgetName = selectedWidget?.name ?: fallbackWidgetName(filePath)
        val scanRange = selectedWidget?.buildRange
            ?: selectedWidget?.classRange
            ?: (findBuildRange(content, codeMask, content.indices) ?: content.indices)
        val scannedProviderNames = (providerNames + declarations.map { it.providerName }).toSet()
        val providers = ProviderUsageScanner.scan(
            filePath = filePath,
            content = content,
            usageScope = ProviderUsageScope(
                providerNames = scannedProviderNames,
                declarations = declarations,
                extensionDependencies = extensionDependencies,
            ),
        )
            .filter { it.textOffset in scanRange }
            .map { it.providerName }
            .distinct()
        val childWidgets = WidgetChildScanner.scan(
            content = content,
            codeMask = codeMask,
            scanRange = scanRange,
            widgetName = widgetName,
        )

        return WidgetDependencyResult(
            widgetName = widgetName,
            providerNames = providers,
            childWidgets = childWidgets,
        )
    }

    /**
     * [caretOffset]으로 선택된 위젯 또는 [file]의 첫 번째 위젯에 대한 의존성을 Dart PSI에서 반환합니다.
     */
    fun analyze(
        filePath: String,
        file: PsiFile,
        providerNames: Set<String>,
        caretOffset: Int? = null,
        declarations: List<RiverpodProviderDeclaration> = emptyList(),
        extensionDependencies: List<RefExtensionDependency> = emptyList(),
    ): WidgetDependencyResult {
        val content = file.text
        if (content.isEmpty()) {
            return WidgetDependencyResult(fallbackWidgetName(filePath), emptyList(), emptyList())
        }

        val codeMask = dartCodeMask(content)
        val code = codeOnly(content, codeMask)
        val widgetContexts = widgetContexts(content, codeMask, code)
        val selectedWidget = selectWidget(widgetContexts, caretOffset, content, codeMask)
        val widgetName = selectedWidget?.name ?: fallbackWidgetName(filePath)
        val scanRange = selectedWidget?.buildRange
            ?: selectedWidget?.classRange
            ?: (findBuildRange(content, codeMask, content.indices) ?: content.indices)
        val scannedProviderNames = (providerNames + declarations.map { it.providerName }).toSet()
        val providers = ProviderUsageScanner.scan(
            filePath = filePath,
            file = file,
            usageScope = ProviderUsageScope(
                providerNames = scannedProviderNames,
                declarations = declarations,
                extensionDependencies = extensionDependencies,
            ),
        )
            .filter { it.textOffset in scanRange }
            .map { it.providerName }
            .distinct()
        val childWidgets = WidgetChildScanner.scan(
            file = file,
            scanRange = scanRange,
            widgetName = widgetName,
        )

        return WidgetDependencyResult(
            widgetName = widgetName,
            providerNames = providers,
            childWidgets = childWidgets,
        )
    }

    private data class WidgetContext(
        val name: String,
        val classRange: IntRange?,
        val buildRange: IntRange?,
    )

    /** 파일 안의 위젯 클래스마다 클래스 범위와 build 범위를 계산합니다. */
    private fun widgetContexts(
        content: String,
        codeMask: BooleanArray,
        code: String,
    ): List<WidgetContext> = widgetClassRegex.findAll(code).map { match ->
        val widgetName = match.groupValues[1]
        val classRange = classRange(content, codeMask, match.range.first)
        WidgetContext(
            name = widgetName,
            classRange = classRange,
            buildRange = classRange?.let { findBuildRange(content, codeMask, it) }
                ?: findStateBuildRange(content, codeMask, code, widgetName),
        )
    }.toList()

    /** 커서 위치가 포함된 위젯을 우선 선택하고 없으면 첫 위젯을 반환합니다. */
    private fun selectWidget(
        widgetContexts: List<WidgetContext>,
        caretOffset: Int?,
        content: String,
        codeMask: BooleanArray,
    ): WidgetContext? {
        if (widgetContexts.isEmpty()) {
            return null
        }
        caretOffset?.let { offset ->
            widgetContextForConstructorAtCaret(widgetContexts, content, codeMask, offset)?.let { return it }
        }
        if (caretOffset == null) {
            return widgetContexts.first()
        }

        return widgetContexts.firstOrNull { context -> caretOffset in (context.buildRange ?: IntRange.EMPTY) }
            ?: widgetContexts.firstOrNull { context -> caretOffset in (context.classRange ?: IntRange.EMPTY) }
            ?: widgetContexts.first()
    }

    /** 같은 파일 안의 위젯 생성자 호출 위에 커서가 있으면 그 위젯 클래스를 선택합니다. */
    private fun widgetContextForConstructorAtCaret(
        widgetContexts: List<WidgetContext>,
        content: String,
        codeMask: BooleanArray,
        caretOffset: Int,
    ): WidgetContext? {
        val identifier = identifierAt(content, codeMask, caretOffset) ?: return null
        if (!isConstructorCall(content, codeMask, identifier.end)) return null

        return widgetContexts.firstOrNull { context -> context.name == identifier.name }
    }

    /** 커서 주변의 Dart 식별자와 범위를 반환합니다. */
    private fun identifierAt(content: String, codeMask: BooleanArray, caretOffset: Int): IdentifierAt? {
        if (content.isEmpty()) return null

        var index = caretOffset.coerceIn(0, content.length - 1)
        if (!codeMask[index] || !isDartIdentifierPart(content[index])) {
            index = (index - 1).coerceAtLeast(0)
        }
        if (!codeMask[index] || !isDartIdentifierPart(content[index])) {
            return null
        }

        var start = index
        while (start > 0 && codeMask[start - 1] && isDartIdentifierPart(content[start - 1])) {
            start--
        }
        var end = index + 1
        while (end < content.length && codeMask[end] && isDartIdentifierPart(content[end])) {
            end++
        }

        return IdentifierAt(content.substring(start, end), end)
    }

    /** 식별자 뒤가 기본 또는 named 생성자 호출인지 확인합니다. */
    private fun isConstructorCall(content: String, codeMask: BooleanArray, identifierEnd: Int): Boolean {
        var index = skipIgnorable(content, codeMask, identifierEnd)
        if (index < content.length && content[index] == '.') {
            index = skipIgnorable(content, codeMask, index + 1)
            if (index >= content.length || !isDartIdentifierPart(content[index])) return false
            while (index < content.length && isDartIdentifierPart(content[index])) {
                index++
            }
            index = skipIgnorable(content, codeMask, index)
        }

        return index < content.length && content[index] == '(' && codeMask[index]
    }

    /** Stateful 계열 위젯의 State 클래스에서 build 메서드 범위를 찾습니다. */
    private fun findStateBuildRange(
        content: String,
        codeMask: BooleanArray,
        code: String,
        widgetName: String,
    ): IntRange? {
        val stateClassRegex = Regex(
            """\bclass\s+[A-Za-z_$][A-Za-z0-9_$]*\s+extends\s+""" +
                    """(?:ConsumerState|State|HookConsumerState)\s*<\s*${Regex.escape(widgetName)}\s*>""",
        )
        val stateMatch = stateClassRegex.find(code) ?: return null
        val stateRange = classRange(content, codeMask, stateMatch.range.first) ?: return null
        return findBuildRange(content, codeMask, stateRange)
    }

    /** 클래스 선언의 중괄호 본문 범위를 반환합니다. */
    private fun classRange(content: String, codeMask: BooleanArray, classOffset: Int): IntRange? {
        val bodyStart = findNextCodeChar(content, codeMask, '{', classOffset) ?: return null
        val bodyEnd = findMatchingPair(content, codeMask, bodyStart, '{', '}') ?: return null
        return bodyStart..bodyEnd
    }

    /** 검색 범위 안에서 Widget build 메서드의 본문 또는 표현식 범위를 찾습니다. */
    private fun findBuildRange(content: String, codeMask: BooleanArray, searchRange: IntRange): IntRange? {
        val code = codeOnly(content, codeMask)
        val buildMatch = buildMethodRegex.find(code, searchRange.first) ?: return null
        if (buildMatch.range.first !in searchRange) {
            return null
        }

        val parametersStart = code.indexOf('(', buildMatch.range.first).takeIf { it >= 0 } ?: return null
        val parametersEnd = findMatchingPair(content, codeMask, parametersStart, '(', ')') ?: return null
        val bodyStart = skipIgnorable(content, codeMask, parametersEnd + 1)
        if (bodyStart !in searchRange) {
            return null
        }

        return when {
            content.startsWith("=>", bodyStart) -> bodyStart until dartStatementEnd(content, codeMask, bodyStart + 2)
            content[bodyStart] == '{' -> bodyStart..(findMatchingPair(content, codeMask, bodyStart, '{', '}')
                ?: return null)

            else -> null
        }
    }

    /** 위젯 클래스를 찾지 못했을 때 파일명에서 표시용 위젯 이름을 만듭니다. */
    private fun fallbackWidgetName(filePath: String): String =
        filePath.substringAfterLast('/').removeSuffix(".dart").ifBlank { "Widget" }

    private data class IdentifierAt(
        val name: String,
        val end: Int,
    )

    private val widgetClassRegex = Regex(
        $$"""\bclass\s+([_$A-Za-z][_$A-Za-z0-9]*)\s+extends\s+""" +
                """(ConsumerWidget|ConsumerStatefulWidget|HookConsumerWidget|StatefulHookConsumerWidget|""" +
                """StatelessWidget|StatefulWidget)\b""",
    )
    private val buildMethodRegex = Regex("""\bWidget\s+build\s*\(""")
}
