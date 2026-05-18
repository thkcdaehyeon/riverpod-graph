package com.ki960213.riverpodgraph.analysis

import com.ki960213.riverpodgraph.dart.codeOnly
import com.ki960213.riverpodgraph.dart.dartCodeMask
import com.ki960213.riverpodgraph.dart.findMatchingPair

/**
 * Riverpod Ref 확장 멤버가 읽는 프로바이더를 설명합니다.
 */
data class RefExtensionDependency(
    /** 의존성 소스로 사용되는 정규화된 확장 멤버 이름입니다. */
    val memberId: String,

    /** 확장에 선언된 Dart 수신자 타입입니다. */
    val receiverType: String,

    /** 확장 멤버가 참조하는 프로바이더 목록입니다. */
    val providerNames: List<String>,

    /** 확장 멤버가 포함된 Dart 파일입니다. */
    val filePath: String,

    /** 파일 안에서 확장 멤버 이름의 오프셋입니다. */
    val textOffset: Int,

    /** 프로바이더 이름별로 묶은 참조 프로바이더 오프셋입니다. */
    val providerOffsetsByProvider: Map<String, List<Int>> = emptyMap(),
)

/**
 * Ref 확장 멤버 뒤에 숨겨진 Riverpod 프로바이더 의존성을 찾습니다.
 */
object RefExtensionScanner {
    /**
     * [providerNames]의 프로바이더를 읽는 Ref 확장 멤버를 [content]에서 스캔합니다.
     */
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

    /** 확장 본문을 멤버 단위로 나누고 각 멤버의 프로바이더 의존성을 수집합니다. */
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

    /** 멤버 표현식 범위에서 발견한 프로바이더 참조로 Ref 확장 의존성 모델을 만듭니다. */
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

    /** 확장 멤버 선언부에서 getter 또는 메서드 이름과 오프셋을 추출합니다. */
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

    /** 표현식 범위 안에서 ref/this 또는 암시적 Ref 호출로 참조된 프로바이더를 찾습니다. */
    private fun providerMatchesInExpression(
        code: String,
        expressionStart: Int,
        expressionEnd: Int,
        providerNames: Set<String>,
    ): List<ProviderMatch> {
        val matches = mutableListOf<ProviderMatch>()

        for (providerName in providerNames) {
            val explicitReceiverRegex = Regex(
                $$"""(?<![._$A-Za-z0-9])(?:ref|this)\s*\.\s*(?:watch|read|listen|invalidate|refresh)\s*(?:<[^(){};]*>)?\s*\(\s*$${
                    Regex.escape(
                        providerName
                    )
                }(?![_$A-Za-z0-9])""",
            )
            val bareCallRegex = Regex(
                $$"""(?<![._$A-Za-z0-9])(?:watch|read|listen|invalidate|refresh)\s*(?:<[^(){};]*>)?\s*\(\s*$${
                    Regex.escape(
                        providerName
                    )
                }(?![_$A-Za-z0-9])""",
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

    /** 지정한 정규식으로 표현식 범위 안의 프로바이더 이름과 오프셋을 수집합니다. */
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

    /** 암시적 호출 후보 앞에 공백을 사이에 둔 멤버 접근 점이 있는지 확인합니다. */
    private fun hasSpacedMemberAccessBefore(code: String, expressionStart: Int, matchStart: Int): Boolean {
        var index = matchStart - 1
        while (index >= expressionStart && code[index].isWhitespace()) {
            index--
        }
        return index >= expressionStart && code[index] == '.'
    }

    /** 괄호와 중괄호 깊이를 고려해 현재 표현식 문장의 끝 세미콜론을 찾습니다. */
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

    /** 확장 수신자 타입의 불필요한 공백을 제거해 비교 가능한 형태로 정규화합니다. */
    private fun normalizeReceiverType(receiverType: String): String =
        receiverType
            .replace(Regex("""\s*\.\s*"""), ".")
            .replace(Regex("""\s+\?"""), "?")
            .trim()

    /** 확장 이름이 없을 때 수신자 타입에서 멤버 ID 접두사를 만듭니다. */
    private fun String.memberIdPrefix(): String =
        removeSuffix("?")
            .substringBefore("<")
            .trim()
            .substringAfterLast(".")

    /** 확장 수신자 타입이 Ref 계열인지 확인합니다. */
    private fun isRefReceiver(receiverType: String): Boolean {
        val baseName = receiverType
            .removeSuffix("?")
            .substringBefore("<")
            .trim()
            .substringAfterLast(".")
        return baseName == "Ref" || baseName.endsWith("Ref")
    }

    /** 현재 스캔 위치가 괄호, 대괄호, 중괄호 안쪽이 아닌 최상위인지 판단합니다. */
    private fun isTopLevel(parenDepth: Int, bracketDepth: Int, braceDepth: Int): Boolean =
        parenDepth == 0 && bracketDepth == 0 && braceDepth == 0

    private data class MemberName(
        val name: String,
        val offset: Int,
    )

    private data class ProviderMatch(
        val providerName: String,
        val offset: Int,
    )

    private val extensionRegex = Regex(
        $$"""\bextension(?:\s+([_$A-Za-z][_$A-Za-z0-9]*)(?:\s*<[^{};]*>)?|\s*<[^{};]*>)?\s+on\s+((?:[_$A-Za-z][_$A-Za-z0-9]*\s*\.\s*)*[_$A-Za-z][_$A-Za-z0-9]*(?:\s*<[^{};]*>)?\s*\??)\s*\{""",
    )
    private val getterNameRegex = Regex($$"""\bget\s+([_$A-Za-z][_$A-Za-z0-9]*)\s*$""")
    private val methodNameRegex = Regex(
        $$"""([_$A-Za-z][_$A-Za-z0-9]*)\s*(?:<[^(){};]*>\s*)?\([^{};]*\)\s*(?:async\*?|sync\*)?\s*$""",
    )
}
