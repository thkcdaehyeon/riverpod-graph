package com.ki960213.riverpodgraph.analysis

import com.ki960213.riverpodgraph.dart.codeOnly
import com.ki960213.riverpodgraph.dart.dartCodeMask
import com.ki960213.riverpodgraph.dart.findMatchingPair
import com.ki960213.riverpodgraph.dart.findNextCodeChar
import com.ki960213.riverpodgraph.dart.skipIgnorable
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
        val selectedWidget = selectWidget(widgetContexts, caretOffset)
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
        val childWidgets = constructorRegex.findAll(code, scanRange.first)
            .takeWhile { it.range.first <= scanRange.last }
            .filter { match -> match.groupValues[1] != widgetName }
            .filter { match -> match.groupValues[1] !in excludedConstructors }
            .filterNot { match -> isWidgetConstructorDeclaration(code, widgetName, match.range.first) }
            .map { match ->
                WidgetChildCandidate(
                    name = match.groupValues[1],
                    marker = markerFor(code, match.range.first),
                    textOffset = match.range.first,
                )
            }
            .distinctBy { it.name to it.textOffset }
            .toList()

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
    private fun selectWidget(widgetContexts: List<WidgetContext>, caretOffset: Int?): WidgetContext? {
        if (widgetContexts.isEmpty()) {
            return null
        }
        if (caretOffset == null) {
            return widgetContexts.first()
        }

        return widgetContexts.firstOrNull { context -> caretOffset in (context.buildRange ?: IntRange.EMPTY) }
            ?: widgetContexts.firstOrNull { context -> caretOffset in (context.classRange ?: IntRange.EMPTY) }
            ?: widgetContexts.first()
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
            content.startsWith("=>", bodyStart) -> bodyStart until statementEnd(content, codeMask, bodyStart + 2)
            content[bodyStart] == '{' -> bodyStart..(findMatchingPair(content, codeMask, bodyStart, '{', '}')
                ?: return null)

            else -> null
        }
    }

    /** 생성자 호출 주변 컨텍스트를 보고 반복, 콜백, 조건 마커를 결정합니다. */
    private fun markerFor(code: String, offset: Int): RiverpodMarker? {
        val prefix = code.substring(markerContextStart(code, offset), offset)
        return when {
            loopRegex.containsMatchIn(prefix) -> RiverpodMarker.LOOP
            callbackRegex.containsMatchIn(prefix) -> RiverpodMarker.CALLBACK
            conditionalRegex.containsMatchIn(prefix) || ternaryRegex.containsMatchIn(prefix) -> RiverpodMarker.CONDITIONAL
            else -> null
        }
    }

    /** 마커 판단에 사용할 직전 문장 또는 인자 구간의 시작 오프셋을 찾습니다. */
    private fun markerContextStart(code: String, offset: Int): Int {
        var index = offset - 1
        while (index >= 0) {
            if (code[index] == ',' || code[index] == ';') {
                return index + 1
            }
            index--
        }

        return 0
    }

    /** 위젯 이름 매치가 생성자 호출이 아니라 클래스 선언부인지 확인합니다. */
    private fun isWidgetConstructorDeclaration(code: String, widgetName: String, offset: Int): Boolean {
        if (!code.startsWith(widgetName, offset)) {
            return false
        }

        val prefix = code.substring(0, offset).takeLast(120)
        return Regex("""\bclass\s+${Regex.escape(widgetName)}\b""").containsMatchIn(prefix)
    }

    /** 위젯 클래스를 찾지 못했을 때 파일명에서 표시용 위젯 이름을 만듭니다. */
    private fun fallbackWidgetName(filePath: String): String =
        filePath.substringAfterLast('/').removeSuffix(".dart").ifBlank { "Widget" }

    /** 표현식 build 본문이나 문장의 세미콜론 다음 위치를 반환합니다. */
    private fun statementEnd(content: String, codeMask: BooleanArray, start: Int): Int {
        var index = start
        while (index < content.length) {
            if (codeMask[index] && content[index] == ';') {
                return index + 1
            }
            index++
        }

        return content.length
    }
    private val widgetClassRegex = Regex(
        $$"""\bclass\s+([_$A-Za-z][_$A-Za-z0-9]*)\s+extends\s+""" +
                """(ConsumerWidget|ConsumerStatefulWidget|HookConsumerWidget|StatefulHookConsumerWidget|""" +
                """StatelessWidget|StatefulWidget)\b""",
    )
    private val buildMethodRegex = Regex("""\bWidget\s+build\s*\(""")
    private val constructorRegex =
        Regex($$"""\b([A-Z][_$A-Za-z0-9]*)\s*(?:\.\s*[A-Za-z_][_$A-Za-z0-9]*)?\s*\(""")
    private val loopRegex = Regex("""\bfor\s*\(|\.(?:map|forEach)\s*(?:<[^(){};]*>)?\(|\bforEach\s*\(""")
    private val callbackRegex = Regex("""\b(?:builder|itemBuilder)\s*:|=>""")
    private val conditionalRegex = Regex("""\bif\s*\(""")
    private val ternaryRegex = Regex("""\?[^?:]*$""")
    private val excludedConstructors = setOf(
        "Align",
        "AppBar",
        "AspectRatio",
        "Builder",
        "BuildContext",
        "Center",
        "CircularProgressIndicator",
        "Column",
        "ConstrainedBox",
        "Container",
        "Consumer",
        "ConsumerState",
        "ConsumerStatefulWidget",
        "ConsumerWidget",
        "CustomScrollView",
        "DateTime",
        "Divider",
        "Duration",
        "Enum",
        "Error",
        "Exception",
        "Expanded",
        "Flexible",
        "Future",
        "FutureBuilder",
        "GestureDetector",
        "GridView",
        "HookConsumerWidget",
        "Icon",
        "Image",
        "Iterable",
        "ListTile",
        "ListView",
        "List",
        "Map",
        "Object",
        "Padding",
        "Positioned",
        "ProviderScope",
        "RegExp",
        "Row",
        "SafeArea",
        "Scaffold",
        "Set",
        "SingleChildScrollView",
        "SizedBox",
        "SliverList",
        "Spacer",
        "Stack",
        "State",
        "StatefulHookConsumerWidget",
        "StatefulWidget",
        "StatelessWidget",
        "Stream",
        "StreamBuilder",
        "String",
        "Text",
        "Uri",
        "Widget",
        "WidgetRef",
        "Wrap",
    )
}
