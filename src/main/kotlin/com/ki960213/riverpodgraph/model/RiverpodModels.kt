package com.ki960213.riverpodgraph.model

/** 파서가 인식하는 Riverpod 프로바이더 선언 유형입니다. */
enum class RiverpodProviderKind {
    /** Riverpod 어노테이션이 붙은 함수에서 생성된 프로바이더입니다. */
    FUNCTION,

    /** Riverpod notifier 클래스에서 생성된 프로바이더입니다. */
    NOTIFIER_CLASS,
}

/** Dart 소스의 Riverpod 프로바이더 선언에서 파싱한 메타데이터입니다. */
data class RiverpodProviderDeclaration(
    /** 프로바이더를 생성하는 데 사용된 선언 형태입니다. */
    val kind: RiverpodProviderKind,

    /** Dart 소스의 원본 함수 또는 클래스 이름입니다. */
    val sourceName: String,

    /** 생성된 프로바이더 심볼 이름입니다. */
    val providerName: String,

    /** notifier 클래스의 생성된 상위 클래스 이름이며, 함수인 경우 null입니다. */
    val generatedSuperclassName: String?,

    /** 소스에서 파싱한 프로바이더 선언 반환 타입입니다. */
    val returnType: String,

    /** family 프로바이더에서 사용하는 매개변수 시그니처입니다. */
    val familySignature: String,

    /** 프로바이더 어노테이션이 keepAlive 동작을 요청하는지 여부입니다. */
    val keepAlive: Boolean,

    /** 프로바이더 소스 심볼이 private인지 여부입니다. */
    val isPrivate: Boolean,

    /** 선언을 포함하는 Dart 파일의 경로입니다. */
    val filePath: String,

    /** 선언이 시작되는 텍스트 오프셋입니다. */
    val textOffset: Int,

    /** 선언이 나타나는 1부터 시작하는 줄 번호입니다. */
    val line: Int,
)

/** Riverpod 코드에서 프로바이더를 참조할 수 있는 방식입니다. */
enum class RiverpodUsageKind {
    /** ref.watch를 통해 관찰되는 프로바이더입니다. */
    WATCH,

    /** ref.read를 통해 접근되는 프로바이더입니다. */
    READ,

    /** ref.listen을 통해 구독되는 프로바이더입니다. */
    LISTEN,

    /** ref.invalidate를 통해 무효화되는 프로바이더입니다. */
    INVALIDATE,

    /** ref.refresh를 통해 새로고침되는 프로바이더입니다. */
    REFRESH,

    /** override에서 사용되는 프로바이더입니다. */
    OVERRIDE,

    /** 생성된 프로바이더 API에서 접근되는 프로바이더 notifier입니다. */
    NOTIFIER,

    /** 생성된 프로바이더 API에서 접근되는 프로바이더 future입니다. */
    FUTURE,

    /** select로 생성된 프로바이더 선택입니다. */
    SELECT,

    /** ref 헬퍼 밖에서 직접 호출된 프로바이더입니다. */
    DIRECT_CALL,

    /** ref의 확장 멤버를 통해 도달한 프로바이더입니다. */
    EXTENSION_MEMBER,
}

/** Dart 소스에서 파싱한 프로바이더 참조 발생 위치입니다. */
data class RiverpodProviderUsage(
    /** 참조된 프로바이더 심볼 이름입니다. */
    val providerName: String,

    /** 참조에 사용된 Riverpod 접근 방식입니다. */
    val kind: RiverpodUsageKind,

    /** 사용 위치를 포함하는 Dart 파일의 경로입니다. */
    val filePath: String,

    /** 사용 위치가 시작되는 텍스트 오프셋입니다. */
    val textOffset: Int,

    /** 사용 위치가 나타나는 1부터 시작하는 줄 번호입니다. */
    val line: Int,

    /** 사용 위치에 붙는 선택적 문맥 마커입니다. */
    val marker: RiverpodMarker? = null,
)

/** 프로바이더 사용과 의존성 간선을 설명하는 문맥 마커입니다. */
enum class RiverpodMarker(
    /** 마커에 표시되는 사람이 읽기 쉬운 레이블입니다. */
    val label: String,
) {
    /** 사용 위치가 조건 분기 안에 있습니다. */
    CONDITIONAL("conditional"),

    /** 사용 위치가 반복문 안에 있습니다. */
    LOOP("loop"),

    /** 사용 위치가 콜백 안에 있습니다. */
    CALLBACK("callback"),

    /** 사용 위치가 ref 확장을 통해 연결됩니다. */
    REF_EXTENSION("ref extension"),

    /** 의존성 간선이 순환에 참여합니다. */
    CYCLE("cycle"),
}

/** 두 Riverpod 프로바이더 사이의 방향성 의존성입니다. */
data class RiverpodDependencyEdge(
    /** 다른 프로바이더에 의존하는 프로바이더입니다. */
    val fromProvider: String,

    /** 의존하는 프로바이더가 참조하는 프로바이더입니다. */
    val toProvider: String,

    /** 이 간선을 만든 사용 방식입니다. */
    val usageKind: RiverpodUsageKind,

    /** 의존성 간선에 대한 선택적 문맥 마커입니다. */
    val marker: RiverpodMarker? = null,
)
