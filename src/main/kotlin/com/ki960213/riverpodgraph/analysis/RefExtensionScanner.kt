package com.ki960213.riverpodgraph.analysis

data class RefExtensionDependency(
    val memberId: String,
    val receiverType: String,
    val providerNames: List<String>,
    val filePath: String,
    val textOffset: Int,
    val providerOffsetsByProvider: Map<String, List<Int>> = emptyMap(),
)

object RefExtensionScanner {
    fun scan(
        filePath: String,
        content: String,
        providerNames: Set<String>,
    ): List<RefExtensionDependency> {
        if (providerNames.isEmpty() || content.isEmpty()) {
            return emptyList()
        }

        val codeMask = dartCodeMask(content)
        val code = codeOnly(content, codeMask)
        val dependencies = mutableListOf<RefExtensionDependency>()

        for (match in extensionRegex.findAll(code)) {
            val receiverType = normalizeReceiverType(match.groupValues[2])
            if (!isRefReceiver(receiverType)) {
                continue
            }
            val extensionName = match.groupValues[1].ifEmpty { receiverType.memberIdPrefix() }

            val bodyStart = match.range.last
            val bodyEnd = findMatchingPair(code, codeMask, bodyStart, '{', '}') ?: continue
            dependencies += memberDependencies(
                filePath = filePath,
                code = code,
                codeMask = codeMask,
                providerNames = providerNames,
                extensionName = extensionName,
                receiverType = receiverType,
                bodyStart = bodyStart,
                bodyEnd = bodyEnd,
            )
        }

        return dependencies
            .sortedBy { it.textOffset }
            .distinctBy { Triple(it.memberId, it.filePath, it.textOffset) }
    }

    private fun memberDependencies(
        filePath: String,
        code: String,
        codeMask: BooleanArray,
        providerNames: Set<String>,
        extensionName: String,
        receiverType: String,
        bodyStart: Int,
        bodyEnd: Int,
    ): List<RefExtensionDependency> {
        val dependencies = mutableListOf<RefExtensionDependency>()
        var segmentStart = bodyStart + 1
        var index = segmentStart
        var parenDepth = 0
        var bracketDepth = 0
        var braceDepth = 0

        while (index < bodyEnd) {
            if (!codeMask[index]) {
                index++
                continue
            }

            when (code[index]) {
                '(' -> parenDepth++
                ')' -> if (parenDepth > 0) parenDepth--
                '[' -> bracketDepth++
                ']' -> if (bracketDepth > 0) bracketDepth--
                '{' -> if (isTopLevel(parenDepth, bracketDepth, braceDepth)) {
                    val member = memberName(code, segmentStart, index)
                    val blockEnd = findMatchingPair(code, codeMask, index, '{', '}') ?: bodyEnd
                    if (member != null) {
                        dependency(
                            filePath = filePath,
                            code = code,
                            providerNames = providerNames,
                            extensionName = extensionName,
                            receiverType = receiverType,
                            member = member,
                            range = (index + 1) until blockEnd,
                        )?.let { dependencies += it }
                    }

                    segmentStart = (blockEnd + 1).coerceAtMost(bodyEnd)
                    index = segmentStart
                    parenDepth = 0
                    bracketDepth = 0
                    braceDepth = 0
                    continue
                } else {
                    braceDepth++
                }

                '}' -> if (braceDepth > 0) braceDepth--
                ';' -> if (isTopLevel(parenDepth, bracketDepth, braceDepth)) {
                    segmentStart = index + 1
                }

                '=' -> if (
                    index + 1 < bodyEnd &&
                    code[index + 1] == '>' &&
                    codeMask[index + 1] &&
                    isTopLevel(parenDepth, bracketDepth, braceDepth)
                ) {
                    val member = memberName(code, segmentStart, index)
                    val expressionStart = index + 2
                    val expressionEnd = statementEnd(code, codeMask, expressionStart, bodyEnd)
                    if (member != null) {
                        dependency(
                            filePath = filePath,
                            code = code,
                            providerNames = providerNames,
                            extensionName = extensionName,
                            receiverType = receiverType,
                            member = member,
                            range = expressionStart until expressionEnd,
                        )?.let { dependencies += it }
                    }

                    segmentStart = (expressionEnd + 1).coerceAtMost(bodyEnd)
                    index = segmentStart
                    parenDepth = 0
                    bracketDepth = 0
                    braceDepth = 0
                    continue
                }
            }

            index++
        }

        return dependencies
    }

    private fun dependency(
        filePath: String,
        code: String,
        providerNames: Set<String>,
        extensionName: String,
        receiverType: String,
        member: MemberName,
        range: IntRange,
    ): RefExtensionDependency? {
        val providerMatches = providerMatchesInExpression(
            code = code,
            expressionStart = range.first,
            expressionEnd = range.last + 1,
            providerNames = providerNames,
        )
        if (providerMatches.isEmpty()) {
            return null
        }

        return RefExtensionDependency(
            memberId = "$extensionName.${member.name}",
            receiverType = receiverType,
            providerNames = providerMatches.map { it.providerName }.distinct(),
            filePath = filePath,
            textOffset = member.offset,
            providerOffsetsByProvider = providerMatches
                .groupBy({ it.providerName }, { it.offset })
                .mapValues { (_, offsets) -> offsets.distinct() },
        )
    }

    private fun memberName(code: String, segmentStart: Int, memberBodyStart: Int): MemberName? {
        val prefix = code.substring(segmentStart, memberBodyStart)
        val getterMatch = getterNameRegex.find(prefix)
        if (getterMatch != null) {
            val group = getterMatch.groups[1] ?: return null
            return MemberName(
                name = group.value,
                offset = segmentStart + group.range.first,
            )
        }

        val methodMatch = methodNameRegex.find(prefix) ?: return null
        val group = methodMatch.groups[1] ?: return null
        return MemberName(
            name = group.value,
            offset = segmentStart + group.range.first,
        )
    }

    private fun providerMatchesInExpression(
        code: String,
        expressionStart: Int,
        expressionEnd: Int,
        providerNames: Set<String>,
    ): List<ProviderMatch> {
        val matches = mutableListOf<ProviderMatch>()

        for (providerName in providerNames) {
            val explicitReceiverRegex = Regex(
                """(?<![._${'$'}A-Za-z0-9])(?:ref|this)\s*\.\s*(?:watch|read|listen|invalidate|refresh)\s*(?:<[^(){};]*>)?\s*\(\s*${
                    Regex.escape(
                        providerName
                    )
                }(?![_${'$'}A-Za-z0-9])""",
            )
            val bareCallRegex = Regex(
                """(?<![._${'$'}A-Za-z0-9])(?:watch|read|listen|invalidate|refresh)\s*(?:<[^(){};]*>)?\s*\(\s*${
                    Regex.escape(
                        providerName
                    )
                }(?![_${'$'}A-Za-z0-9])""",
            )
            matches += providerMatches(code, expressionStart, expressionEnd, providerName, explicitReceiverRegex)
            matches += providerMatches(
                code = code,
                expressionStart = expressionStart,
                expressionEnd = expressionEnd,
                providerName = providerName,
                regex = bareCallRegex,
                rejectSpacedMemberAccess = true,
            )
        }

        return matches
            .sortedBy { it.offset }
            .distinct()
    }

    private fun providerMatches(
        code: String,
        expressionStart: Int,
        expressionEnd: Int,
        providerName: String,
        regex: Regex,
        rejectSpacedMemberAccess: Boolean = false,
    ): List<ProviderMatch> =
        regex.findAll(code, expressionStart)
            .takeWhile { it.range.first < expressionEnd }
            .mapNotNull { match ->
                if (rejectSpacedMemberAccess && hasSpacedMemberAccessBefore(code, expressionStart, match.range.first)) {
                    return@mapNotNull null
                }
                val providerOffset = match.range.last - providerName.length + 1
                if (providerOffset in expressionStart until expressionEnd) {
                    ProviderMatch(providerName = providerName, offset = providerOffset)
                } else {
                    null
                }
            }
            .toList()

    private fun hasSpacedMemberAccessBefore(code: String, expressionStart: Int, matchStart: Int): Boolean {
        var index = matchStart - 1
        while (index >= expressionStart && code[index].isWhitespace()) {
            index--
        }
        return index >= expressionStart && code[index] == '.'
    }

    private fun statementEnd(
        code: String,
        codeMask: BooleanArray,
        start: Int,
        limit: Int,
    ): Int {
        var index = start
        var parenDepth = 0
        var bracketDepth = 0
        var braceDepth = 0

        while (index < limit) {
            if (!codeMask[index]) {
                index++
                continue
            }

            when (code[index]) {
                '(' -> parenDepth++
                ')' -> if (parenDepth > 0) parenDepth--
                '[' -> bracketDepth++
                ']' -> if (bracketDepth > 0) bracketDepth--
                '{' -> braceDepth++
                '}' -> if (braceDepth > 0) braceDepth--
                ';' -> if (isTopLevel(parenDepth, bracketDepth, braceDepth)) {
                    return index
                }
            }
            index++
        }

        return limit
    }

    private fun findMatchingPair(
        code: String,
        codeMask: BooleanArray,
        start: Int,
        open: Char,
        close: Char,
    ): Int? {
        var depth = 0
        var index = start
        while (index < code.length) {
            if (!codeMask[index]) {
                index++
                continue
            }

            if (code[index] == open) {
                depth++
            } else if (code[index] == close) {
                depth--
                if (depth == 0) {
                    return index
                }
            }
            index++
        }

        return null
    }

    private fun normalizeReceiverType(receiverType: String): String =
        receiverType
            .replace(Regex("""\s*\.\s*"""), ".")
            .replace(Regex("""\s+\?"""), "?")
            .trim()

    private fun String.memberIdPrefix(): String =
        removeSuffix("?")
            .substringBefore("<")
            .trim()
            .substringAfterLast(".")

    private fun isRefReceiver(receiverType: String): Boolean {
        val baseName = receiverType
            .removeSuffix("?")
            .substringBefore("<")
            .trim()
            .substringAfterLast(".")
        return baseName == "Ref" || baseName.endsWith("Ref")
    }

    private fun isTopLevel(parenDepth: Int, bracketDepth: Int, braceDepth: Int): Boolean =
        parenDepth == 0 && bracketDepth == 0 && braceDepth == 0

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

    private fun isIdentifierPart(char: Char): Boolean =
        char == '_' || char == '$' || char.isLetterOrDigit()

    private data class MemberName(
        val name: String,
        val offset: Int,
    )

    private data class ProviderMatch(
        val providerName: String,
        val offset: Int,
    )

    private val extensionRegex = Regex(
        """\bextension(?:\s+([_${'$'}A-Za-z][_${'$'}A-Za-z0-9]*)(?:\s*<[^{};]*>)?|\s*<[^{};]*>)?\s+on\s+((?:[_${'$'}A-Za-z][_${'$'}A-Za-z0-9]*\s*\.\s*)*[_${'$'}A-Za-z][_${'$'}A-Za-z0-9]*(?:\s*<[^{};]*>)?\s*\??)\s*\{""",
    )
    private val getterNameRegex = Regex("""\bget\s+([_${'$'}A-Za-z][_${'$'}A-Za-z0-9]*)\s*$""")
    private val methodNameRegex = Regex(
        """([_${'$'}A-Za-z][_${'$'}A-Za-z0-9]*)\s*(?:<[^(){};]*>\s*)?\([^{};]*\)\s*(?:async\*?|sync\*)?\s*$""",
    )
}
