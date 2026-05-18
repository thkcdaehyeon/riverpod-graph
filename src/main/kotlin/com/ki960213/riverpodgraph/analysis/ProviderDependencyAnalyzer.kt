package com.ki960213.riverpodgraph.analysis

import com.ki960213.riverpodgraph.model.RiverpodDependencyEdge
import com.ki960213.riverpodgraph.model.RiverpodMarker
import com.ki960213.riverpodgraph.model.RiverpodProviderDeclaration

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
    ): List<RiverpodDependencyEdge> = analyzeFile(
        filePath = filePath,
        content = content,
        declarations = declarations,
        extensionDependencies = null,
    )

    /**
     * [filePath]에 선언된 프로바이더가 소유한 의존성을 외부 Ref 확장 문맥까지 포함해 분석합니다.
     */
    fun analyzeFile(
        filePath: String,
        content: String,
        declarations: List<RiverpodProviderDeclaration>,
        extensionDependencies: List<RefExtensionDependency>?,
    ): List<RiverpodDependencyEdge> {
        val providers = declarations.map { it.providerName }.toSet()
        if (providers.isEmpty() || content.isEmpty()) {
            return emptyList()
        }

        val resolvedExtensionDependencies = extensionDependencies ?: RefExtensionScanner.scan(
            filePath = filePath,
            content = content,
            providerNames = providers,
        )
        val usageScope = ProviderUsageScope(
            providerNames = providers,
            declarations = declarations,
            extensionDependencies = resolvedExtensionDependencies,
        )

        return declarations
            .filter { it.filePath == filePath && it.textOffset in content.indices }
            .flatMap { declaration ->
                val scanRange = declaration.sourceRange(content.length)
                val providerNames = providers - declaration.providerName
                ProviderUsageScanner.scan(
                    filePath = filePath,
                    content = content,
                    usageScope = usageScope.limitedTo(providerNames),
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
