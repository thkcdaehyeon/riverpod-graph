package com.ki960213.riverpodgraph.analysis

import com.ki960213.riverpodgraph.model.RiverpodMarker
import com.ki960213.riverpodgraph.model.RiverpodProviderDeclaration

data class WidgetDependencyResult(
    val widgetName: String,
    val providerNames: List<String>,
    val childWidgets: List<WidgetChildCandidate>,
)

data class WidgetChildCandidate(
    val name: String,
    val marker: RiverpodMarker?,
    val textOffset: Int,
)

object WidgetDependencyAnalyzer {
    fun analyze(
        filePath: String,
        content: String,
        providerNames: Set<String>,
        depthLimit: Int,
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
            providerNames = scannedProviderNames,
            directCallSourceNamesByProvider = directCallSourceNamesByProvider(scannedProviderNames, declarations),
            declarationOffsetsByProvider = declarationOffsetsByProvider(filePath, declarations),
            extensionDependencies = extensionDependencies,
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

    private fun findStateBuildRange(
        content: String,
        codeMask: BooleanArray,
        code: String,
        widgetName: String,
    ): IntRange? {
        val stateClassRegex = Regex(
            """\bclass\s+[A-Za-z_${'$'}][A-Za-z0-9_${'$'}]*\s+extends\s+""" +
                """(?:ConsumerState|State|HookConsumerState)\s*<\s*${Regex.escape(widgetName)}\s*>""",
        )
        val stateMatch = stateClassRegex.find(code) ?: return null
        val stateRange = classRange(content, codeMask, stateMatch.range.first) ?: return null
        return findBuildRange(content, codeMask, stateRange)
    }

    private fun classRange(content: String, codeMask: BooleanArray, classOffset: Int): IntRange? {
        val bodyStart = findNextCodeChar(content, codeMask, '{', classOffset) ?: return null
        val bodyEnd = findMatchingPair(content, codeMask, bodyStart, '{', '}') ?: return null
        return bodyStart..bodyEnd
    }

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
            content[bodyStart] == '{' -> bodyStart..(findMatchingPair(content, codeMask, bodyStart, '{', '}') ?: return null)
            else -> null
        }
    }

    private fun markerFor(code: String, offset: Int): RiverpodMarker? {
        val prefix = code.substring(markerContextStart(code, offset), offset)
        return when {
            loopRegex.containsMatchIn(prefix) -> RiverpodMarker.LOOP
            callbackRegex.containsMatchIn(prefix) -> RiverpodMarker.CALLBACK
            conditionalRegex.containsMatchIn(prefix) || ternaryRegex.containsMatchIn(prefix) -> RiverpodMarker.CONDITIONAL
            else -> null
        }
    }

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

    private fun directCallSourceNamesByProvider(
        providerNames: Set<String>,
        declarations: List<RiverpodProviderDeclaration>,
    ): Map<String, Set<String>> {
        val declaredNames = declarations
            .filter { it.providerName in providerNames }
            .groupBy { it.providerName }
            .mapValues { (_, declarations) -> declarations.map { it.sourceName }.toSet() }

        return providerNames.associateWith { providerName ->
            declaredNames[providerName] ?: setOf(providerName.removeSuffix("Provider"))
        }
    }

    private fun declarationOffsetsByProvider(
        filePath: String,
        declarations: List<RiverpodProviderDeclaration>,
    ): Map<String, Set<Int>> = declarations
        .filter { it.filePath == filePath }
        .groupBy { it.providerName }
        .mapValues { (_, declarations) -> declarations.map { it.textOffset }.toSet() }

    private fun isWidgetConstructorDeclaration(code: String, widgetName: String, offset: Int): Boolean {
        if (!code.startsWith(widgetName, offset)) {
            return false
        }

        val prefix = code.substring(0, offset).takeLast(120)
        return Regex("""\bclass\s+${Regex.escape(widgetName)}\b""").containsMatchIn(prefix)
    }

    private fun fallbackWidgetName(filePath: String): String =
        filePath.substringAfterLast('/').removeSuffix(".dart").ifBlank { "Widget" }

    private fun dartCodeMask(content: String): BooleanArray {
        val codeMask = BooleanArray(content.length) { true }
        var index = 0
        while (index < content.length) {
            when {
                content.startsWith("//", index) -> {
                    val end = content.indexOf('\n', index + 2).takeIf { it != -1 } ?: content.length
                    codeMask.markIgnored(index, end)
                    index = end
                }
                content.startsWith("/*", index) -> {
                    val end = blockCommentEnd(content, index)
                    codeMask.markIgnored(index, end)
                    index = end
                }
                content[index] == '\'' || content[index] == '"' -> {
                    val end = stringEnd(content, index)
                    codeMask.markIgnored(index, end)
                    index = end
                }
                else -> index++
            }
        }

        return codeMask
    }

    private fun blockCommentEnd(content: String, start: Int): Int {
        var depth = 0
        var index = start
        while (index < content.length) {
            when {
                content.startsWith("/*", index) -> {
                    depth++
                    index += 2
                }
                content.startsWith("*/", index) -> {
                    depth--
                    index += 2
                    if (depth == 0) {
                        return index
                    }
                }
                else -> index++
            }
        }

        return content.length
    }

    private fun stringEnd(content: String, start: Int): Int {
        val quote = content[start]
        val triple = content.startsWith("$quote$quote$quote", start)
        val raw = start > 0 &&
            (content[start - 1] == 'r' || content[start - 1] == 'R') &&
            (start == 1 || !isIdentifierPart(content[start - 2]))
        var index = start + if (triple) 3 else 1

        while (index < content.length) {
            if (!raw && content[index] == '\\') {
                index += 2
                continue
            }
            if (triple && content.startsWith("$quote$quote$quote", index)) {
                return index + 3
            }
            if (!triple && content[index] == quote) {
                return index + 1
            }
            index++
        }

        return content.length
    }

    private fun findMatchingPair(
        content: String,
        codeMask: BooleanArray,
        start: Int,
        open: Char,
        close: Char,
    ): Int? {
        var depth = 0
        var index = start
        while (index < content.length) {
            if (!codeMask[index]) {
                index++
                continue
            }

            when (content[index]) {
                open -> depth++
                close -> {
                    depth--
                    if (depth == 0) {
                        return index
                    }
                }
            }
            index++
        }

        return null
    }

    private fun findNextCodeChar(
        content: String,
        codeMask: BooleanArray,
        char: Char,
        start: Int,
    ): Int? {
        var index = start
        while (index < content.length) {
            if (codeMask[index] && content[index] == char) {
                return index
            }
            index++
        }

        return null
    }

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

    private fun skipIgnorable(content: String, codeMask: BooleanArray, start: Int): Int {
        var index = start
        while (index < content.length && (!codeMask[index] || content[index].isWhitespace())) {
            index++
        }
        return index
    }

    private fun codeOnly(content: String, codeMask: BooleanArray): String = buildString(content.length) {
        for (index in content.indices) {
            append(if (codeMask[index]) content[index] else ' ')
        }
    }

    private fun BooleanArray.markIgnored(start: Int, end: Int) {
        for (index in start until end.coerceAtMost(size)) {
            this[index] = false
        }
    }

    private fun isIdentifierPart(char: Char): Boolean =
        char == '_' || char == '$' || char.isLetterOrDigit()

    private val widgetClassRegex = Regex(
        """\bclass\s+([_${'$'}A-Za-z][_${'$'}A-Za-z0-9]*)\s+extends\s+""" +
            """(ConsumerWidget|ConsumerStatefulWidget|HookConsumerWidget|StatefulHookConsumerWidget|""" +
            """StatelessWidget|StatefulWidget)\b""",
    )
    private val buildMethodRegex = Regex("""\bWidget\s+build\s*\(""")
    private val constructorRegex = Regex("""\b([A-Z][_${'$'}A-Za-z0-9]*)\s*(?:\.\s*[A-Za-z_][_${'$'}A-Za-z0-9]*)?\s*\(""")
    private val loopRegex = Regex("""(?:\bfor\s*\(|\.(?:map|forEach)\s*(?:<[^(){};]*>)?\(|\bforEach\s*\()""")
    private val callbackRegex = Regex("""(?:\b(?:builder|itemBuilder)\s*:|=>)""")
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
