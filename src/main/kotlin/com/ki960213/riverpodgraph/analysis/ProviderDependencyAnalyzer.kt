package com.ki960213.riverpodgraph.analysis

import com.ki960213.riverpodgraph.dart.dartCodeMask
import com.ki960213.riverpodgraph.dart.findMatchingPair
import com.ki960213.riverpodgraph.dart.findNextCodeChar
import com.ki960213.riverpodgraph.dart.skipIgnorable
import com.ki960213.riverpodgraph.model.RiverpodDependencyEdge
import com.ki960213.riverpodgraph.model.RiverpodMarker
import com.ki960213.riverpodgraph.model.RiverpodProviderDeclaration
import com.ki960213.riverpodgraph.model.RiverpodProviderKind

/**
 * Dart 파일의 Riverpod 선언과 사용 위치에서 프로바이더 의존성 간선을 만듭니다.
 */
object ProviderDependencyAnalyzer {
    /**
     * [filePath]에 선언된 프로바이더가 소유한 의존성을 [content]에서 분석합니다.
     */
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

    /**
     * 대상 프로바이더에서 원본 프로바이더로 도달할 수 있는 의존성 간선을 표시합니다.
     */
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

    /** 선언 종류에 맞춰 프로바이더 본문을 스캔할 텍스트 범위를 계산합니다. */
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

    /** 함수형 프로바이더 선언의 매개변수 뒤에서 본문이나 표현식의 끝 위치를 찾습니다. */
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

    /** 클래스형 프로바이더 선언의 본문 블록 끝 다음 위치를 찾습니다. */
    private fun classEnd(content: String, codeMask: BooleanArray, start: Int): Int? {
        val bodyStart = findNextCodeChar(content, codeMask, '{', start) ?: return null
        return findMatchingPair(content, codeMask, bodyStart, '{', '}')?.plus(1)
    }

    /** 표현식 본문이나 문장의 세미콜론 다음 위치를 반환합니다. */
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

    /** 의존성 그래프에서 [start]가 [target]까지 도달 가능한지 깊이 우선으로 확인합니다. */
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

}
