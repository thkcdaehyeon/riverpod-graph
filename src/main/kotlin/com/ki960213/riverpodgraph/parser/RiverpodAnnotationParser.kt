package com.ki960213.riverpodgraph.parser

import com.ki960213.riverpodgraph.model.RiverpodNaming
import com.ki960213.riverpodgraph.model.RiverpodProviderDeclaration
import com.ki960213.riverpodgraph.model.RiverpodProviderKind

object RiverpodAnnotationParser {
    fun parse(filePath: String, content: String): List<RiverpodProviderDeclaration> {
        if (filePath.endsWith(".g.dart")) {
            return emptyList()
        }

        val codeMask = dartCodeMask(content)
        return annotationRegex.findAll(content).mapNotNull { match ->
            if (!isCodeRange(codeMask, match.range)) {
                return@mapNotNull null
            }

            val annotationName = match.groupValues[1]
            val annotationEnd = annotationEnd(content, codeMask, match.range.last + 1) ?: return@mapNotNull null
            val declarationOffset = skipMetadata(content, codeMask, annotationEnd.index)
            val keepAlive = annotationName == "Riverpod" && annotationEnd.codeArgs.contains(keepAliveTrueRegex)

            parseClass(filePath, content, codeMask, declarationOffset, keepAlive)
                ?: parseFunction(filePath, content, codeMask, declarationOffset, keepAlive)
        }.toList()
    }

    private fun annotationEnd(content: String, codeMask: BooleanArray, start: Int): AnnotationEnd? {
        val argsStart = skipInlineWhitespace(content, start)
        if (argsStart >= content.length || content[argsStart] != '(' || !codeMask[argsStart]) {
            return AnnotationEnd(index = start, codeArgs = "")
        }

        val argsEnd = findMatchingPair(content, codeMask, argsStart, '(', ')') ?: return null
        return AnnotationEnd(
            index = argsEnd + 1,
            codeArgs = codeSlice(content, codeMask, argsStart + 1, argsEnd),
        )
    }

    private fun parseClass(
        filePath: String,
        content: String,
        codeMask: BooleanArray,
        start: Int,
        keepAlive: Boolean,
    ): RiverpodProviderDeclaration? {
        val match = classHeaderRegex.find(content, start) ?: return null
        if (match.range.first != start) {
            return null
        }

        val classNameGroup = match.groups[1] ?: return null
        val className = classNameGroup.value
        val generatedSuperclassName = RiverpodNaming.generatedSuperclassForClass(className)
        if (match.groupValues[2] != generatedSuperclassName) {
            return null
        }

        val classBodyStart = findNextCodeChar(content, codeMask, '{', match.range.last + 1) ?: return null
        val classBodyEnd = findMatchingPair(content, codeMask, classBodyStart, '{', '}') ?: return null
        val build = parseBuildMethod(content, codeMask, classBodyStart + 1, classBodyEnd) ?: return null

        return RiverpodProviderDeclaration(
            kind = RiverpodProviderKind.NOTIFIER_CLASS,
            sourceName = className,
            providerName = RiverpodNaming.providerForClass(className),
            generatedSuperclassName = generatedSuperclassName,
            returnType = build.returnType,
            familySignature = build.familySignature,
            keepAlive = keepAlive,
            isPrivate = className.startsWith("_"),
            filePath = filePath,
            textOffset = classNameGroup.range.first,
            line = lineOf(content, classNameGroup.range.first),
        )
    }

    private fun parseBuildMethod(
        content: String,
        codeMask: BooleanArray,
        bodyStart: Int,
        bodyEnd: Int,
    ): MethodSignature? {
        var searchStart = bodyStart
        while (searchStart < bodyEnd) {
            val buildOffset = findIdentifier(content, codeMask, "build", searchStart, bodyEnd) ?: return null
            if (!isAtMemberDepthZero(content, codeMask, bodyStart, buildOffset)) {
                searchStart = buildOffset + "build".length
                continue
            }

            val beforeBuild = skipIgnorableBack(content, codeMask, buildOffset - 1)
            if (beforeBuild >= bodyStart && content[beforeBuild] == '.') {
                searchStart = buildOffset + "build".length
                continue
            }

            val parametersStart = skipIgnorable(content, codeMask, buildOffset + "build".length)
            if (parametersStart < bodyEnd && content[parametersStart] == '(' && codeMask[parametersStart]) {
                val parametersEnd = findMatchingPair(content, codeMask, parametersStart, '(', ')') ?: return null
                val returnTypeStart = previousSignatureBoundary(content, codeMask, bodyStart, buildOffset)
                if (containsCodeChar(content, codeMask, returnTypeStart, buildOffset, '=') ||
                    containsCodeArrow(content, codeMask, returnTypeStart, buildOffset)
                ) {
                    searchStart = buildOffset + "build".length
                    continue
                }
                val returnType = cleanType(
                    content.substring(returnTypeStart, buildOffset),
                )
                if (returnType.isNotEmpty()) {
                    return MethodSignature(
                        returnType = returnType,
                        familySignature = cleanSignature(content.substring(parametersStart + 1, parametersEnd)),
                    )
                }
            }
            searchStart = buildOffset + "build".length
        }

        return null
    }

    private fun parseFunction(
        filePath: String,
        content: String,
        codeMask: BooleanArray,
        start: Int,
        keepAlive: Boolean,
    ): RiverpodProviderDeclaration? {
        val signature = findFunctionSignature(content, codeMask, start) ?: return null

        return RiverpodProviderDeclaration(
            kind = RiverpodProviderKind.FUNCTION,
            sourceName = signature.name,
            providerName = RiverpodNaming.providerForFunction(signature.name),
            generatedSuperclassName = null,
            returnType = signature.returnType,
            familySignature = cleanSignature(content.substring(signature.parametersStart + 1, signature.parametersEnd)),
            keepAlive = keepAlive,
            isPrivate = signature.name.startsWith("_"),
            filePath = filePath,
            textOffset = signature.nameStart,
            line = lineOf(content, signature.nameStart),
        )
    }

    private fun findFunctionSignature(content: String, codeMask: BooleanArray, start: Int): FunctionSignature? {
        var index = start
        while (index < content.length) {
            if (!codeMask[index]) {
                index++
                continue
            }

            when (content[index]) {
                '(' -> {
                    functionCandidateAt(content, codeMask, start, index)?.let { return it }
                    index = (findMatchingPair(content, codeMask, index, '(', ')') ?: index) + 1
                    continue
                }

                '{', ';', '=' -> return null
            }

            index++
        }

        return null
    }

    private fun functionCandidateAt(
        content: String,
        codeMask: BooleanArray,
        declarationStart: Int,
        parametersStart: Int,
    ): FunctionSignature? {
        val nameEnd = skipIgnorableBack(content, codeMask, parametersStart - 1)
        if (nameEnd < declarationStart) {
            return null
        }

        val nameStart = identifierStart(content, codeMask, nameEnd)
        if (nameStart > nameEnd || nameStart < declarationStart) {
            return null
        }

        val name = content.substring(nameStart, nameEnd + 1)
        if (name == "Function") {
            return null
        }

        val returnType = cleanType(content.substring(declarationStart, nameStart))
        if (returnType.isEmpty()) {
            return null
        }

        val parametersEnd = findMatchingPair(content, codeMask, parametersStart, '(', ')') ?: return null
        return FunctionSignature(
            name = name,
            nameStart = nameStart,
            parametersStart = parametersStart,
            parametersEnd = parametersEnd,
            returnType = returnType,
        )
    }

    private fun skipMetadata(content: String, codeMask: BooleanArray, start: Int): Int {
        var index = skipIgnorable(content, codeMask, start)
        while (index < content.length && content[index] == '@' && codeMask[index]) {
            index = skipAnnotationName(content, codeMask, index + 1)
            index = skipIgnorable(content, codeMask, index)
            if (index < content.length && content[index] == '(' && codeMask[index]) {
                val argsEnd = findMatchingPair(content, codeMask, index, '(', ')') ?: return index
                index = argsEnd + 1
            }
            index = skipIgnorable(content, codeMask, index)
        }

        return index
    }

    private fun skipAnnotationName(content: String, codeMask: BooleanArray, start: Int): Int {
        var index = start
        while (index < content.length && codeMask[index] && (isIdentifierPart(content[index]) || content[index] == '.')) {
            index++
        }
        return index
    }

    private fun skipInlineWhitespace(content: String, start: Int): Int {
        var index = start
        while (index < content.length && (content[index] == ' ' || content[index] == '\t')) {
            index++
        }
        return index
    }

    private fun skipIgnorable(content: String, codeMask: BooleanArray, start: Int): Int {
        var index = start
        while (index < content.length && (content[index].isWhitespace() || !codeMask[index])) {
            index++
        }
        return index
    }

    private fun skipIgnorableBack(content: String, codeMask: BooleanArray, start: Int): Int {
        var index = start
        while (index >= 0 && (content[index].isWhitespace() || !codeMask[index])) {
            index--
        }
        return index
    }

    private fun identifierStart(content: String, codeMask: BooleanArray, end: Int): Int {
        var index = end
        while (index >= 0 && codeMask[index] && isIdentifierPart(content[index])) {
            index--
        }
        return index + 1
    }

    private fun findIdentifier(
        content: String,
        codeMask: BooleanArray,
        token: String,
        start: Int,
        end: Int,
    ): Int? {
        var index = content.indexOf(token, start)
        while (index != -1 && index < end) {
            val afterIndex = index + token.length
            val tokenInCode = (index until afterIndex).all { codeMask[it] }
            val before = index == 0 || !isIdentifierPart(content[index - 1])
            val after = afterIndex >= content.length || !isIdentifierPart(content[afterIndex])
            if (tokenInCode && before && after) {
                return index
            }
            index = content.indexOf(token, index + 1)
        }

        return null
    }

    private fun previousSignatureBoundary(content: String, codeMask: BooleanArray, min: Int, end: Int): Int {
        var index = end
        while (index > min) {
            val previous = index - 1
            if (codeMask[previous]) {
                when (content[previous]) {
                    '\n', ';', '{', '}' -> return index
                }
            }
            index--
        }

        return min
    }

    private fun isAtMemberDepthZero(content: String, codeMask: BooleanArray, start: Int, end: Int): Boolean {
        var braceDepth = 0
        var index = start
        while (index < end) {
            if (codeMask[index]) {
                when (content[index]) {
                    '{' -> braceDepth++
                    '}' -> braceDepth--
                }
            }
            index++
        }

        return braceDepth == 0
    }

    private fun containsCodeChar(
        content: String,
        codeMask: BooleanArray,
        start: Int,
        end: Int,
        char: Char,
    ): Boolean =
        (start until end).any { codeMask[it] && content[it] == char }

    private fun containsCodeArrow(content: String, codeMask: BooleanArray, start: Int, end: Int): Boolean =
        (start until (end - 1)).any { codeMask[it] && codeMask[it + 1] && content[it] == '=' && content[it + 1] == '>' }

    private fun findNextCodeChar(content: String, codeMask: BooleanArray, char: Char, start: Int): Int? {
        var index = start
        while (index < content.length) {
            if (content[index] == char && codeMask[index]) {
                return index
            }
            index++
        }

        return null
    }

    private fun findMatchingPair(
        content: String,
        codeMask: BooleanArray,
        openIndex: Int,
        open: Char,
        close: Char,
    ): Int? {
        var depth = 0
        var index = openIndex
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

    private fun BooleanArray.markIgnored(start: Int, end: Int) {
        for (index in start until end.coerceAtMost(size)) {
            this[index] = false
        }
    }

    private fun isCodeRange(codeMask: BooleanArray, range: IntRange): Boolean =
        range.all { codeMask[it] }

    private fun codeSlice(content: String, codeMask: BooleanArray, start: Int, end: Int): String =
        buildString(end - start) {
            for (index in start until end) {
                append(if (codeMask[index]) content[index] else ' ')
            }
        }

    private fun cleanType(type: String): String =
        type.trim()
            .replace(annotationInTypeRegex, " ")
            .replace(whitespaceRegex, " ")
            .trim()

    private fun cleanSignature(signature: String): String =
        signature.trim()
            .replace(whitespaceRegex, " ")

    private fun lineOf(content: String, offset: Int): Int =
        content.substring(0, offset).count { it == '\n' } + 1

    private fun isIdentifierPart(char: Char): Boolean =
        char == '_' || char == '$' || char.isLetterOrDigit()

    private data class AnnotationEnd(
        val index: Int,
        val codeArgs: String,
    )

    private data class FunctionSignature(
        val name: String,
        val nameStart: Int,
        val parametersStart: Int,
        val parametersEnd: Int,
        val returnType: String,
    )

    private data class MethodSignature(
        val returnType: String,
        val familySignature: String,
    )

    private const val IDENTIFIER = "[_${'$'}A-Za-z][_${'$'}A-Za-z0-9]*"

    private val annotationRegex = Regex("""@(riverpod|Riverpod)\b""")
    private val keepAliveTrueRegex = Regex("""\bkeepAlive\s*:\s*true\b""")
    private val classHeaderRegex = Regex(
        """(?:(?:abstract|base|final|sealed|interface|mixin)\s+)*class\s+($IDENTIFIER)(?:\s*<[^>{}]*>)?\s+extends\s+($IDENTIFIER)\b""",
    )
    private val annotationInTypeRegex = Regex("""@\w+(?:\([^)]*\))?""")
    private val whitespaceRegex = Regex("""\s+""")
}
