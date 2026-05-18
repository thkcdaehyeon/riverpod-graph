package com.ki960213.riverpodgraph.analysis

import com.ki960213.riverpodgraph.model.RiverpodDependencyEdge
import com.ki960213.riverpodgraph.model.RiverpodMarker
import com.ki960213.riverpodgraph.model.RiverpodProviderDeclaration
import com.ki960213.riverpodgraph.model.RiverpodProviderKind

object ProviderDependencyAnalyzer {
    fun analyzeFile(
        filePath: String,
        content: String,
        declarations: List<RiverpodProviderDeclaration>,
    ): List<RiverpodDependencyEdge> {
        val providers = declarations.map { it.providerName }.toSet()
        if (providers.isEmpty() || content.isEmpty()) {
            return emptyList()
        }

        val codeMask = dartCodeMask(content)
        val directCallSourceNamesByProvider = declarations
            .groupBy { it.providerName }
            .mapValues { (_, declarations) -> declarations.map { it.sourceName }.toSet() }
        val declarationOffsetsByProvider = declarations
            .filter { it.filePath == filePath }
            .groupBy { it.providerName }
            .mapValues { (_, declarations) -> declarations.map { it.textOffset }.toSet() }
        val extensionDependencies = RefExtensionScanner.scan(
            filePath = filePath,
            content = content,
            providerNames = providers,
        )

        return declarations
            .filter { it.filePath == filePath && it.textOffset in content.indices }
            .flatMap { declaration ->
                val scanRange = providerRange(content, codeMask, declaration)
                val providerNames = providers - declaration.providerName
                ProviderUsageScanner.scan(
                    filePath = filePath,
                    content = content,
                    providerNames = providerNames,
                    directCallSourceNamesByProvider = directCallSourceNamesByProvider,
                    declarationOffsetsByProvider = declarationOffsetsByProvider,
                    extensionDependencies = extensionDependencies,
                ).filter { it.textOffset in scanRange }
                    .map { usage ->
                        RiverpodDependencyEdge(
                            fromProvider = declaration.providerName,
                            toProvider = usage.providerName,
                            usageKind = usage.kind,
                            marker = usage.marker,
                        )
                    }
            }
            .distinct()
    }

    fun markCycles(edges: List<RiverpodDependencyEdge>): List<RiverpodDependencyEdge> {
        val adjacency = edges.groupBy({ it.fromProvider }, { it.toProvider })
        return edges.map { edge ->
            if (canReach(adjacency, edge.toProvider, edge.fromProvider)) {
                edge.copy(marker = RiverpodMarker.CYCLE)
            } else {
                edge
            }
        }
    }

    private fun providerRange(
        content: String,
        codeMask: BooleanArray,
        declaration: RiverpodProviderDeclaration,
    ): IntRange {
        val start = declaration.textOffset
        val end = when (declaration.kind) {
            RiverpodProviderKind.FUNCTION -> functionEnd(content, codeMask, declaration)
            RiverpodProviderKind.NOTIFIER_CLASS -> classEnd(content, codeMask, start)
        } ?: content.length

        return start until end.coerceAtLeast(start + 1).coerceAtMost(content.length)
    }

    private fun functionEnd(
        content: String,
        codeMask: BooleanArray,
        declaration: RiverpodProviderDeclaration,
    ): Int? {
        val afterName = declaration.textOffset + declaration.sourceName.length
        val parametersStart = skipIgnorable(content, codeMask, afterName)
        if (parametersStart >= content.length || content[parametersStart] != '(' || !codeMask[parametersStart]) {
            return null
        }

        val parametersEnd = findMatchingPair(content, codeMask, parametersStart, '(', ')') ?: return null
        var index = skipIgnorable(content, codeMask, parametersEnd + 1)
        while (index < content.length) {
            if (!codeMask[index]) {
                index++
                continue
            }

            if (content.startsWith("=>", index)) {
                return statementEnd(content, codeMask, index + 2)
            }
            if (content[index] == '{') {
                return findMatchingPair(content, codeMask, index, '{', '}')?.plus(1)
            }
            if (content[index] == ';') {
                return index + 1
            }
            index++
        }

        return null
    }

    private fun classEnd(content: String, codeMask: BooleanArray, start: Int): Int? {
        val bodyStart = findNextCodeChar(content, codeMask, '{', start) ?: return null
        return findMatchingPair(content, codeMask, bodyStart, '{', '}')?.plus(1)
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

    private fun canReach(
        adjacency: Map<String, List<String>>,
        start: String,
        target: String,
        visited: MutableSet<String> = mutableSetOf(),
    ): Boolean {
        if (start == target) {
            return true
        }
        if (!visited.add(start)) {
            return false
        }

        return adjacency[start].orEmpty().any { canReach(adjacency, it, target, visited) }
    }

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

    private fun skipIgnorable(content: String, codeMask: BooleanArray, start: Int): Int {
        var index = start
        while (index < content.length && (!codeMask[index] || content[index].isWhitespace())) {
            index++
        }
        return index
    }

    private fun BooleanArray.markIgnored(start: Int, end: Int) {
        for (index in start until end.coerceAtMost(size)) {
            this[index] = false
        }
    }

    private fun isIdentifierPart(char: Char): Boolean =
        char == '_' || char == '$' || char.isLetterOrDigit()
}
