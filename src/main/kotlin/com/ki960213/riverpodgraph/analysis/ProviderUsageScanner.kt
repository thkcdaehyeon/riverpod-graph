package com.ki960213.riverpodgraph.analysis

import com.ki960213.riverpodgraph.model.RiverpodProviderUsage
import com.ki960213.riverpodgraph.model.RiverpodUsageKind

object ProviderUsageScanner {
    fun scan(
        filePath: String,
        content: String,
        providerNames: Set<String>,
        directCallSourceNamesByProvider: Map<String, Set<String>> = providerNames.associateWith {
            setOf(it.removeSuffix("Provider"))
        },
        declarationOffsetsByProvider: Map<String, Set<Int>> = emptyMap(),
    ): List<RiverpodProviderUsage> {
        if (providerNames.isEmpty() || content.isEmpty()) {
            return emptyList()
        }

        val codeMask = dartCodeMask(content)
        val code = codeOnly(content, codeMask)
        val usages = mutableListOf<RiverpodProviderUsage>()

        for (providerName in providerNames) {
            usages += refCallUsages(filePath, content, code, providerName)
            usages += overrideUsages(filePath, content, code, providerName)
            usages += directCallUsages(
                filePath = filePath,
                content = content,
                code = code,
                providerName = providerName,
                sourceNames = directCallSourceNamesByProvider[providerName].orEmpty(),
                declarationOffsets = declarationOffsetsByProvider[providerName].orEmpty(),
            )
        }

        return usages
            .sortedBy { it.textOffset }
            .distinctBy { Triple(it.providerName, it.textOffset, it.kind) }
    }

    private fun refCallUsages(
        filePath: String,
        content: String,
        code: String,
        providerName: String,
    ): List<RiverpodProviderUsage> {
        val regex = Regex(
            """\bref\s*\.\s*(watch|read|listen|invalidate|refresh)\s*(?:<[^(){};]*>)?\s*\(\s*${Regex.escape(providerName)}\b""",
        )

        return regex.findAll(code).map { match ->
            val method = match.groupValues[1]
            val providerOffset = match.range.last - providerName.length + 1
            usage(
                providerName = providerName,
                kind = modifierKind(code, providerOffset + providerName.length) ?: refMethodKind(method),
                filePath = filePath,
                content = content,
                offset = providerOffset,
            )
        }.toList()
    }

    private fun overrideUsages(
        filePath: String,
        content: String,
        code: String,
        providerName: String,
    ): List<RiverpodProviderUsage> {
        val regex = Regex("""\b${Regex.escape(providerName)}\s*\.\s*overrideWith[A-Za-z0-9_]*\s*\(""")

        return regex.findAll(code).map { match ->
            usage(
                providerName = providerName,
                kind = RiverpodUsageKind.OVERRIDE,
                filePath = filePath,
                content = content,
                offset = match.range.first,
            )
        }.toList()
    }

    private fun directCallUsages(
        filePath: String,
        content: String,
        code: String,
        providerName: String,
        sourceNames: Set<String>,
        declarationOffsets: Set<Int>,
    ): List<RiverpodProviderUsage> {
        val exactSourceNames = sourceNames.filter { it.isNotEmpty() }.toSet()
        if (exactSourceNames.isEmpty()) {
            return emptyList()
        }

        return exactSourceNames.flatMap { sourceName ->
            val regex = Regex("""(?<![._${'$'}A-Za-z0-9])${Regex.escape(sourceName)}\s*\(""")
            regex.findAll(code)
                .filterNot { it.range.first in declarationOffsets }
                .filterNot { isMemberAccess(code, it.range.first) }
                .filterNot { isLikelyDeclarationPrefix(code, it.range.first) }
                .map { match ->
                    usage(
                        providerName = providerName,
                        kind = RiverpodUsageKind.DIRECT_CALL,
                        filePath = filePath,
                        content = content,
                        offset = match.range.first,
                    )
                }.toList()
        }
    }

    private fun isMemberAccess(code: String, offset: Int): Boolean {
        var index = offset - 1
        while (index >= 0 && code[index].isWhitespace()) {
            index--
        }
        return index >= 0 && code[index] == '.'
    }

    private fun modifierKind(code: String, offset: Int): RiverpodUsageKind? {
        var index = skipWhitespace(code, offset)
        if (index >= code.length || code[index] != '.') {
            return null
        }

        index = skipWhitespace(code, index + 1)
        val modifierEnd = identifierEnd(code, index)
        val modifier = code.substring(index, modifierEnd)
        return when (modifier) {
            "notifier" -> RiverpodUsageKind.NOTIFIER
            "future" -> RiverpodUsageKind.FUTURE
            "select" -> RiverpodUsageKind.SELECT
            else -> null
        }
    }

    private fun refMethodKind(method: String): RiverpodUsageKind = when (method) {
        "watch" -> RiverpodUsageKind.WATCH
        "read" -> RiverpodUsageKind.READ
        "listen" -> RiverpodUsageKind.LISTEN
        "invalidate" -> RiverpodUsageKind.INVALIDATE
        "refresh" -> RiverpodUsageKind.REFRESH
        else -> RiverpodUsageKind.READ
    }

    private fun isLikelyDeclarationPrefix(code: String, offset: Int): Boolean {
        val prefix = annotationLineRegex.replace(declarationLookbackPrefix(code, offset), " ").trim()
        if (prefix.isEmpty()) {
            return false
        }
        if (prefix in DIRECT_CALL_PREFIX_KEYWORDS || prefix.split(whitespaceRegex).firstOrNull() in DIRECT_CALL_PREFIX_KEYWORDS) {
            return false
        }
        if (prefix.contains(">") && !prefix.contains("<")) {
            return false
        }
        if (prefix.any { it in EXPRESSION_PREFIX_CHARS }) {
            return false
        }

        return declarationPrefixRegex.matches(prefix)
    }

    private fun declarationLookbackPrefix(code: String, offset: Int): String {
        var index = offset - 1
        while (index >= 0) {
            if (code[index] in DECLARATION_LOOKBACK_BOUNDARIES) {
                return code.substring(index + 1, offset)
            }
            index--
        }

        return code.substring(0, offset)
    }

    private fun usage(
        providerName: String,
        kind: RiverpodUsageKind,
        filePath: String,
        content: String,
        offset: Int,
    ): RiverpodProviderUsage = RiverpodProviderUsage(
        providerName = providerName,
        kind = kind,
        filePath = filePath,
        textOffset = offset,
        line = lineOf(content, offset),
    )

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

    private fun skipWhitespace(content: String, start: Int): Int {
        var index = start
        while (index < content.length && content[index].isWhitespace()) {
            index++
        }
        return index
    }

    private fun identifierEnd(content: String, start: Int): Int {
        var index = start
        while (index < content.length && isIdentifierPart(content[index])) {
            index++
        }
        return index
    }

    private fun lineOf(content: String, offset: Int): Int =
        content.substring(0, offset.coerceAtLeast(0).coerceAtMost(content.length)).count { it == '\n' } + 1

    private fun isIdentifierPart(char: Char): Boolean =
        char == '_' || char == '$' || char.isLetterOrDigit()

    private val DIRECT_CALL_PREFIX_KEYWORDS = setOf("return", "await", "yield", "throw")
    private val EXPRESSION_PREFIX_CHARS = setOf('=', '(', '[', '{', ',', ':', '?', '+', '-', '*', '/', '%', '!', '&', '|', '^')
    private val DECLARATION_LOOKBACK_BOUNDARIES = setOf(';', '{', '}', '=', '(', '[', ',', ':', '?', '+', '-', '*', '/', '%', '!', '&', '|', '^')
    private val declarationPrefixRegex = Regex("""(?:[A-Za-z_$][A-Za-z0-9_$]*|[<>\[\],.?]|\s)+""")
    private val annotationLineRegex = Regex("""(?m)^\s*@[_${'$'}A-Za-z][_${'$'}A-Za-z0-9]*(?:\([^\n]*\))?\s*$""")
    private val whitespaceRegex = Regex("""\s+""")
}
