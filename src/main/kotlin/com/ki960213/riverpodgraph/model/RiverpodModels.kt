package com.ki960213.riverpodgraph.model

enum class RiverpodProviderKind {
    FUNCTION,
    NOTIFIER_CLASS,
}

data class RiverpodProviderDeclaration(
    val kind: RiverpodProviderKind,
    val sourceName: String,
    val providerName: String,
    val generatedSuperclassName: String?,
    val returnType: String,
    val familySignature: String,
    val keepAlive: Boolean,
    val isPrivate: Boolean,
    val filePath: String,
    val textOffset: Int,
    val line: Int,
)

enum class RiverpodUsageKind {
    WATCH,
    READ,
    LISTEN,
    INVALIDATE,
    REFRESH,
    OVERRIDE,
    NOTIFIER,
    FUTURE,
    SELECT,
    DIRECT_CALL,
    EXTENSION_MEMBER,
}

data class RiverpodProviderUsage(
    val providerName: String,
    val kind: RiverpodUsageKind,
    val filePath: String,
    val textOffset: Int,
    val line: Int,
    val marker: RiverpodMarker? = null,
)

enum class RiverpodMarker(val label: String) {
    CONDITIONAL("conditional"),
    LOOP("loop"),
    CALLBACK("callback"),
    REF_EXTENSION("ref extension"),
    CYCLE("cycle"),
}

data class RiverpodDependencyEdge(
    val fromProvider: String,
    val toProvider: String,
    val usageKind: RiverpodUsageKind,
    val marker: RiverpodMarker? = null,
)
