package com.ki960213.riverpodgraph.dart

/**
 * Dart 소스에서 실제 코드로 취급할 문자 위치를 true로 표시한다.
 *
 * 주석과 문자열은 Riverpod 참조 분석에서 제외해야 하므로 false로 마스킹한다.
 */
internal fun dartCodeMask(content: String): BooleanArray =
    BooleanArray(content.length) { true }.apply {
        content.ignoredDartRanges().forEach(::markIgnored)
    }

/**
 * 원본 오프셋을 유지하면서 코드가 아닌 영역을 공백으로 바꾼다.
 *
 * 정규식 검색 결과의 인덱스를 원본 Dart 파일 인덱스와 그대로 맞추기 위한 변환이다.
 */
internal fun codeOnly(content: String, codeMask: BooleanArray): String =
    content.mapIndexed { index, char -> char.takeIf { codeMask[index] } ?: ' ' }
        .joinToString(separator = "")

/**
 * 코드 영역만 따라가며 open/close 쌍이 닫히는 위치를 찾는다.
 */
internal fun findMatchingPair(
    content: String,
    codeMask: BooleanArray,
    openIndex: Int,
    open: Char,
    close: Char,
): Int? =
    content.codeIndexes(codeMask, openIndex)
        .runningFold(BracketScanState()) { state, index ->
            state.afterReading(content[index], index, open, close)
        }
        .firstOrNull { state -> state.matchingIndex != null }
        ?.matchingIndex

/**
 * start 이후의 코드 영역에서 특정 문자를 찾는다.
 */
internal fun findNextCodeChar(
    content: String,
    codeMask: BooleanArray,
    char: Char,
    start: Int,
): Int? =
    content.codeIndexes(codeMask, start)
        .firstOrNull { index -> content[index] == char }

/**
 * 공백, 주석, 문자열처럼 분석 대상이 아닌 문자를 건너뛰고 다음 코드 위치를 반환한다.
 */
internal fun skipIgnorable(content: String, codeMask: BooleanArray, start: Int): Int =
    (start.coerceAtLeast(0) until content.length)
        .firstOrNull { index -> codeMask[index] && !content[index].isWhitespace() }
        ?: content.length

/**
 * Dart 블록 주석의 끝 오프셋을 계산한다.
 *
 * Dart는 블록 주석 중첩을 허용하므로 delimiter depth가 0으로 돌아오는 지점을 끝으로 본다.
 */
internal fun dartBlockCommentEnd(content: String, start: Int): Int =
    content.blockCommentDelimiters(start)
        .runningFold(BlockCommentScanState()) { state, delimiter -> state.afterReading(delimiter) }
        .firstOrNull { state -> state.closedEnd != null }
        ?.closedEnd
        ?: content.length

/**
 * Dart 문자열 리터럴의 끝 오프셋을 계산한다.
 *
 * 일반 문자열, raw 문자열, 삼중 따옴표 문자열을 같은 모델로 해석한다.
 */
internal fun dartStringEnd(content: String, start: Int): Int =
    DartStringLiteral.from(content, start)
        .let { literal -> content.stringTerminatorCandidates(literal).firstOrNull() }
        ?: content.length

/** Dart 코드 분석에서 제외해야 할 주석과 문자열 범위들을 순서대로 생성한다. */
private fun String.ignoredDartRanges(): Sequence<IntRange> =
    generateSequence(nextIgnoredRangeScan(from = 0)) { previous -> nextIgnoredRangeScan(from = previous.nextIndex) }
        .map { scan -> scan.range }

/** 지정 위치부터 다음 제외 범위를 찾아 다음 탐색 시작점과 함께 반환한다. */
private tailrec fun String.nextIgnoredRangeScan(from: Int): IgnoredRangeScan? =
    when {
        from >= length -> null
        else -> when (val range = ignoredDartRangeAt(from)) {
            null -> nextIgnoredRangeScan(from + 1)
            else -> IgnoredRangeScan(range, nextIndex = range.last + 1)
        }
    }

/** 현재 인덱스에서 시작하는 제외 범위가 있는지 규칙 목록으로 판정한다. */
private fun String.ignoredDartRangeAt(index: Int): IntRange? =
    ignoredRangeRules.firstNotNullOfOrNull { rule -> rule.rangeAt(this, index) }

/** 한 줄 주석이 끝나는 줄바꿈 위치나 파일 끝 위치를 반환한다. */
private fun String.lineCommentEnd(start: Int): Int =
    indexOf('\n', start + 2).takeIf { it != -1 } ?: length

/** 블록 주석 내부의 시작/종료 delimiter를 중첩 계산 순서대로 생성한다. */
private fun String.blockCommentDelimiters(start: Int): Sequence<BlockCommentDelimiter> = sequence {
    var index = start
    while (index < length) {
        val delimiter = blockCommentDelimiterRules.firstNotNullOfOrNull { rule ->
            rule.delimiterAt(this@blockCommentDelimiters, index)
        }

        if (delimiter == null) {
            index++
        } else {
            yield(delimiter)
            index = delimiter.end
        }
    }
}

/** 문자열 본문을 훑으며 리터럴 종료 후보 위치들을 생성한다. */
private fun String.stringTerminatorCandidates(literal: DartStringLiteral): Sequence<Int> =
    generateSequence(literal.bodyStart) { index -> literal.nextScanIndex(this, index) }
        .takeWhile { index -> index < length }
        .mapNotNull { index -> literal.terminatorEndAt(this, index) }

/** 지정 위치부터 코드로 마스킹된 문자 인덱스만 순서대로 제공한다. */
private fun String.codeIndexes(codeMask: BooleanArray, start: Int): Sequence<Int> =
    (start.coerceAtLeast(0) until length).asSequence()
        .filter { index -> codeMask[index] }

/** 제외 범위에 해당하는 마스크 값을 false로 표시한다. */
private fun BooleanArray.markIgnored(range: IntRange) {
    (range.first..range.last.coerceAtMost(lastIndex)).forEach { index ->
        this[index] = false
    }
}

/** 문자가 Dart 문자열을 시작할 수 있는 따옴표인지 확인한다. */
private fun Char.isDartQuote(): Boolean =
    this == '\'' || this == '"'

/** raw 문자열 접두사 판별에 쓰이는 Dart 식별자 문자인지 확인한다. */
private fun isIdentifierPart(char: Char): Boolean =
    char == '_' || char == '$' || char.isLetterOrDigit()

// 주석과 문자열처럼 Dart 코드 분석에서 제외할 범위를 규칙 목록으로 선언한다.
private val ignoredRangeRules = listOf(
    IgnoredRangeRule(
        matches = { index -> startsWith("//", index) },
        endExclusive = { index -> lineCommentEnd(index) },
    ),
    IgnoredRangeRule(
        matches = { index -> startsWith("/*", index) },
        endExclusive = { index -> dartBlockCommentEnd(this, index) },
    ),
    IgnoredRangeRule(
        matches = { index -> this[index].isDartQuote() },
        endExclusive = { index -> dartStringEnd(this, index) },
    ),
)

// 블록 주석 중첩 계산에 필요한 시작/종료 delimiter를 한 곳에 모아둔다.
private val blockCommentDelimiterRules = listOf(
    BlockCommentDelimiterRule(token = "/*", kind = BlockCommentDelimiterKind.OPEN),
    BlockCommentDelimiterRule(token = "*/", kind = BlockCommentDelimiterKind.CLOSE),
)

private data class IgnoredRangeRule(
    val matches: String.(Int) -> Boolean,
    val endExclusive: String.(Int) -> Int,
) {
    fun rangeAt(content: String, index: Int): IntRange? =
        if (content.matches(index)) index until content.endExclusive(index) else null
}

private data class IgnoredRangeScan(
    val range: IntRange,
    val nextIndex: Int,
)

// 괄호 깊이와 매칭 완료 위치를 값으로 누적해 pair 탐색을 표현한다.
private data class BracketScanState(
    val depth: Int = 0,
    val matchingIndex: Int? = null,
) {
    fun afterReading(char: Char, index: Int, open: Char, close: Char): BracketScanState =
        when (char) {
            open -> copy(depth = depth + 1)
            close -> copy(depth = depth - 1, matchingIndex = index.takeIf { depth == 1 })
            else -> this
        }
}

// Dart 문자열의 형태를 모델링해 escape/raw/triple 처리 규칙을 한 타입 안에 모은다.
private data class DartStringLiteral(
    val quote: Char,
    val triple: Boolean,
    val raw: Boolean,
    val bodyStart: Int,
) {
    val delimiter: String = "$quote$quote$quote"

    val terminatorLength: Int = if (triple) delimiter.length else 1

    fun nextScanIndex(content: String, index: Int): Int =
        when {
            isEscapedAt(content, index) -> index + 2
            terminatorEndAt(content, index) != null -> index + terminatorLength
            else -> index + 1
        }

    fun terminatorEndAt(content: String, index: Int): Int? =
        (index + terminatorLength).takeIf { isTerminatorAt(content, index) }

    /** 현재 위치가 이스케이프 시퀀스의 시작인지 확인한다. */
    private fun isEscapedAt(content: String, index: Int): Boolean =
        !raw && content[index] == '\\'

    /** 현재 위치에서 리터럴 종료 delimiter가 시작되는지 확인한다. */
    private fun isTerminatorAt(content: String, index: Int): Boolean =
        when {
            triple -> content.startsWith(delimiter, index)
            else -> content[index] == quote
        }

    companion object {
        fun from(content: String, start: Int): DartStringLiteral {
            val quote = content[start]
            val triple = content.startsWith("$quote$quote$quote", start)
            val raw = start > 0 &&
                    (content[start - 1] == 'r' || content[start - 1] == 'R') &&
                    (start == 1 || !isIdentifierPart(content[start - 2]))

            return DartStringLiteral(
                quote = quote,
                triple = triple,
                raw = raw,
                bodyStart = start + if (triple) 3 else 1,
            )
        }
    }
}

// 블록 주석 delimiter를 읽으면서 중첩 깊이와 종료 위치를 누적한다.
private data class BlockCommentScanState(
    val depth: Int = 0,
    val closedEnd: Int? = null,
) {
    fun afterReading(delimiter: BlockCommentDelimiter): BlockCommentScanState {
        val nextDepth = depth + delimiter.kind.depthDelta
        return copy(
            depth = nextDepth,
            closedEnd = delimiter.end.takeIf { nextDepth == 0 },
        )
    }
}

private data class BlockCommentDelimiterRule(
    val token: String,
    val kind: BlockCommentDelimiterKind,
) {
    fun delimiterAt(content: String, index: Int): BlockCommentDelimiter? =
        BlockCommentDelimiter(kind, end = index + token.length)
            .takeIf { content.startsWith(token, index) }
}

private data class BlockCommentDelimiter(
    val kind: BlockCommentDelimiterKind,
    val end: Int,
)

private enum class BlockCommentDelimiterKind(val depthDelta: Int) {
    OPEN(1),
    CLOSE(-1),
}
