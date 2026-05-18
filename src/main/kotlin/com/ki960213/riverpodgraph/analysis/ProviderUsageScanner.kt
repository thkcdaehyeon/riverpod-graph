package com.ki960213.riverpodgraph.analysis

import com.ki960213.riverpodgraph.dart.codeOnly
import com.ki960213.riverpodgraph.dart.dartCodeMask
import com.ki960213.riverpodgraph.dart.dartLineOf
import com.ki960213.riverpodgraph.dart.isDartIdentifierPart
import com.ki960213.riverpodgraph.model.RiverpodMarker
import com.ki960213.riverpodgraph.model.RiverpodProviderDeclaration
import com.ki960213.riverpodgraph.model.RiverpodProviderUsage
import com.ki960213.riverpodgraph.model.RiverpodUsageKind

/**
 * Provider 사용 스캔에 필요한 Riverpod 선언과 확장 의존성 문맥입니다.
 */
data class ProviderUsageScope(
    /** 사용 위치를 찾을 Provider 이름 집합입니다. */
    val providerNames: Set<String>,

    /** Provider 원본 선언 메타데이터입니다. 직접 호출 이름과 선언 위치 제외에 사용됩니다. */
    val declarations: List<RiverpodProviderDeclaration> = emptyList(),

    /** Ref 확장 멤버 뒤에 숨어 있는 Provider 의존성입니다. */
    val extensionDependencies: List<RefExtensionDependency> = emptyList(),
) {
    /** 지정 Provider만 스캔하도록 현재 문맥을 좁힙니다. */
    fun limitedTo(providerNames: Set<String>): ProviderUsageScope =
        copy(providerNames = providerNames)
}

/**
 * Dart 소스 코드에서 Riverpod 프로바이더 참조를 찾습니다.
 */
object ProviderUsageScanner {
    /**
     * [content]에서 [usageScope]에 포함된 Provider 사용 위치를 스캔하고 소스 위치를 반환합니다.
     */
    fun scan(
        filePath: String,
        content: String,
        usageScope: ProviderUsageScope,
    ): List<RiverpodProviderUsage> = scan(
        filePath = filePath,
        content = content,
        providerNames = usageScope.providerNames,
        directCallSourceNamesByProvider = usageScope.directCallSourceNamesByProvider(),
        declarationOffsetsByProvider = usageScope.declarationOffsetsByProvider(filePath),
        extensionDependencies = usageScope.extensionDependencies,
    )

    /**
     * 계산된 usage 문맥으로 Provider 사용 위치를 스캔합니다.
     */
    private fun scan(
        filePath: String,
        content: String,
        providerNames: Set<String>,
        directCallSourceNamesByProvider: Map<String, Set<String>>,
        declarationOffsetsByProvider: Map<String, Set<Int>>,
        extensionDependencies: List<RefExtensionDependency>,
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
        usages += extensionMemberUsages(
            filePath = filePath,
            content = content,
            code = code,
            providerNames = providerNames,
            extensionDependencies = extensionDependencies,
        )

        return usages
            .sortedBy { it.textOffset }
            .distinctBy { Triple(it.providerName, it.textOffset, it.kind) }
    }

    /** ref.watch/read/listen 등으로 직접 참조된 프로바이더 사용을 찾습니다. */
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

    /** overrideWith 계열 호출에서 프로바이더 오버라이드 사용을 찾습니다. */
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

    /** 프로바이더 원본 함수 이름을 직접 호출한 위치를 의존성 사용으로 수집합니다. */
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
            val regex = Regex($$"""(?<![._$A-Za-z0-9])$${Regex.escape(sourceName)}\s*\(""")
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

    /** Ref 확장 멤버 호출을 해당 멤버가 내부에서 읽는 프로바이더 사용으로 변환합니다. */
    private fun extensionMemberUsages(
        filePath: String,
        content: String,
        code: String,
        providerNames: Set<String>,
        extensionDependencies: List<RefExtensionDependency>,
    ): List<RiverpodProviderUsage> {
        if (extensionDependencies.isEmpty()) {
            return emptyList()
        }

        return extensionDependencies.flatMap { dependency ->
            val matchingProviderNames = dependency.providerNames.filter { it in providerNames }
            if (matchingProviderNames.isEmpty()) {
                return@flatMap emptyList()
            }

            val memberName = dependency.memberId.substringAfterLast('.')
            if (memberName.isEmpty()) {
                return@flatMap emptyList()
            }

            val regex =
                Regex($$"""(?<![._$A-Za-z0-9])ref\s*\.\s*$${Regex.escape(memberName)}(?![_$A-Za-z0-9])""")
            regex.findAll(code).flatMap { match ->
                val memberOffset = code.indexOf(memberName, startIndex = match.range.first)
                matchingProviderNames.map { providerName ->
                    usage(
                        providerName = providerName,
                        kind = RiverpodUsageKind.EXTENSION_MEMBER,
                        filePath = filePath,
                        content = content,
                        offset = memberOffset,
                        marker = RiverpodMarker.REF_EXTENSION,
                    )
                }
            }.toList()
        }
    }

    /** 오프셋 앞의 유효 코드가 점이면 멤버 접근으로 판단합니다. */
    private fun isMemberAccess(code: String, offset: Int): Boolean {
        var index = offset - 1
        while (index >= 0 && code[index].isWhitespace()) {
            index--
        }
        return index >= 0 && code[index] == '.'
    }

    /** 프로바이더 이름 뒤의 .notifier, .future, .select 수식자를 사용 종류로 해석합니다. */
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

    /** ref 메서드 이름을 기본 Riverpod 사용 종류로 매핑합니다. */
    private fun refMethodKind(method: String): RiverpodUsageKind = when (method) {
        "watch" -> RiverpodUsageKind.WATCH
        "read" -> RiverpodUsageKind.READ
        "listen" -> RiverpodUsageKind.LISTEN
        "invalidate" -> RiverpodUsageKind.INVALIDATE
        "refresh" -> RiverpodUsageKind.REFRESH
        else -> RiverpodUsageKind.READ
    }

    /** 직접 호출 후보 앞의 텍스트가 함수나 변수 선언부처럼 보이는지 판별합니다. */
    private fun isLikelyDeclarationPrefix(code: String, offset: Int): Boolean {
        val prefix = annotationLineRegex.replace(declarationLookbackPrefix(code, offset), " ").trim()
        if (prefix.isEmpty()) {
            return false
        }
        if (prefix in DIRECT_CALL_PREFIX_KEYWORDS || prefix.split(whitespaceRegex)
                .firstOrNull() in DIRECT_CALL_PREFIX_KEYWORDS
        ) {
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

    /** 선언 여부 판단에 필요한 직전 경계 문자 이후의 접두 텍스트를 추출합니다. */
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

    /** 공통 필드와 줄 번호를 채워 프로바이더 사용 모델을 만듭니다. */
    private fun usage(
        providerName: String,
        kind: RiverpodUsageKind,
        filePath: String,
        content: String,
        offset: Int,
        marker: RiverpodMarker? = null,
    ): RiverpodProviderUsage = RiverpodProviderUsage(
        providerName = providerName,
        kind = kind,
        filePath = filePath,
        textOffset = offset,
        line = dartLineOf(content, offset),
        marker = marker,
    )

    /** [start]부터 연속된 공백을 건너뛴 첫 위치를 반환합니다. */
    private fun skipWhitespace(content: String, start: Int): Int {
        var index = start
        while (index < content.length && content[index].isWhitespace()) {
            index++
        }
        return index
    }

    /** [start]에서 시작하는 Dart 식별자 조각의 끝 위치를 찾습니다. */
    private fun identifierEnd(content: String, start: Int): Int {
        var index = start
        while (index < content.length && isDartIdentifierPart(content[index])) {
            index++
        }
        return index
    }

    private val DIRECT_CALL_PREFIX_KEYWORDS = setOf("return", "await", "yield", "throw")
    private val EXPRESSION_PREFIX_CHARS =
        setOf('=', '(', '[', '{', ',', ':', '?', '+', '-', '*', '/', '%', '!', '&', '|', '^')
    private val DECLARATION_LOOKBACK_BOUNDARIES =
        setOf(';', '{', '}', '=', '(', '[', ',', ':', '?', '+', '-', '*', '/', '%', '!', '&', '|', '^')
    private val declarationPrefixRegex = Regex("""(?:[A-Za-z_$][A-Za-z0-9_$]*|[<>\[\],.?]|\s)+""")
    private val annotationLineRegex = Regex($$"""(?m)^\s*@[_$A-Za-z][_$A-Za-z0-9]*(?:\([^\n]*\))?\s*$""")
    private val whitespaceRegex = Regex("""\s+""")
}

/** 프로바이더별 직접 호출 가능한 원본 선언 이름 목록을 반환합니다. */
private fun ProviderUsageScope.directCallSourceNamesByProvider(): Map<String, Set<String>> {
    val declaredNames = declarations
        .filter { declaration -> declaration.providerName in providerNames }
        .groupBy { declaration -> declaration.providerName }
        .mapValues { (_, declarations) -> declarations.mapTo(linkedSetOf()) { it.sourceName } }

    return providerNames.associateWith { providerName ->
        declaredNames[providerName] ?: setOf(providerName.removeSuffix("Provider"))
    }
}

/** 같은 파일 안의 Provider 선언 offset을 프로바이더 이름별로 반환합니다. */
private fun ProviderUsageScope.declarationOffsetsByProvider(filePath: String): Map<String, Set<Int>> =
    declarations
        .filter { declaration -> declaration.filePath == filePath && declaration.providerName in providerNames }
        .groupBy { declaration -> declaration.providerName }
        .mapValues { (_, declarations) -> declarations.mapTo(linkedSetOf()) { it.textOffset } }
