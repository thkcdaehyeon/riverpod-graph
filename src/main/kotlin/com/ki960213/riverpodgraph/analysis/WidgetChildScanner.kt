package com.ki960213.riverpodgraph.analysis

import com.ki960213.riverpodgraph.dart.codeOnly
import com.ki960213.riverpodgraph.model.RiverpodMarker

/** Widget build 범위 안에서 자식 위젯 생성자 후보와 문맥 마커를 찾습니다. */
internal object WidgetChildScanner {
    /** 지정 범위의 Dart 코드에서 자식 위젯 생성자 후보를 반환합니다. */
    fun scan(
        content: String,
        codeMask: BooleanArray,
        scanRange: IntRange,
        widgetName: String,
    ): List<WidgetChildCandidate> {
        if (scanRange.first > scanRange.last) {
            return emptyList()
        }

        val code = codeOnly(content, codeMask)
        return constructorRegex.findAll(code, scanRange.first)
            .takeWhile { match -> match.range.first <= scanRange.last }
            .filter { match -> match.groupValues[1] != widgetName }
            .filter { match -> match.groupValues[1] !in excludedConstructors }
            .filterNot { match -> isWidgetConstructorDeclaration(code, widgetName, match.range.first) }
            .map { match ->
                WidgetChildCandidate(
                    name = match.groupValues[1],
                    marker = markerFor(code, match.range.first),
                    textOffset = match.range.first,
                )
            }
            .distinctBy { it.name to it.textOffset }
            .toList()
    }

    /** 생성자 호출 주변 컨텍스트를 보고 반복, 콜백, 조건 마커를 결정합니다. */
    private fun markerFor(code: String, offset: Int): RiverpodMarker? {
        val prefix = code.substring(markerContextStart(code, offset), offset)
        return when {
            loopRegex.containsMatchIn(prefix) -> RiverpodMarker.LOOP
            callbackRegex.containsMatchIn(prefix) -> RiverpodMarker.CALLBACK
            conditionalRegex.containsMatchIn(prefix) || ternaryRegex.containsMatchIn(prefix) -> RiverpodMarker.CONDITIONAL
            else -> null
        }
    }

    /** 마커 판단에 사용할 직전 문장 또는 인자 구간의 시작 오프셋을 찾습니다. */
    private fun markerContextStart(code: String, offset: Int): Int {
        var index = offset - 1
        while (index >= 0) {
            if (code[index] == ',' || code[index] == ';') {
                return index + 1
            }
            index--
        }

        return 0
    }

    /** 위젯 이름 매치가 생성자 호출이 아니라 클래스 선언부인지 확인합니다. */
    private fun isWidgetConstructorDeclaration(code: String, widgetName: String, offset: Int): Boolean {
        if (!code.startsWith(widgetName, offset)) {
            return false
        }

        val prefix = code.substring(0, offset).takeLast(120)
        return Regex("""\bclass\s+${Regex.escape(widgetName)}\b""").containsMatchIn(prefix)
    }

    private val constructorRegex =
        Regex($$"""\b([A-Z][_$A-Za-z0-9]*)\s*(?:\.\s*[A-Za-z_][_$A-Za-z0-9]*)?\s*\(""")
    private val loopRegex = Regex("""\bfor\s*\(|\.(?:map|forEach)\s*(?:<[^(){};]*>)?\(|\bforEach\s*\(""")
    private val callbackRegex = Regex("""\b(?:builder|itemBuilder)\s*:|=>""")
    private val conditionalRegex = Regex("""\bif\s*\(""")
    private val ternaryRegex = Regex("""\?[^?:]*$""")
    private val excludedConstructors = setOf(
        "Align",
        "AppBar",
        "AspectRatio",
        "Builder",
        "BuildContext",
        "Center",
        "CircularProgressIndicator",
        "Column",
        "ConstrainedBox",
        "Container",
        "Consumer",
        "ConsumerState",
        "ConsumerStatefulWidget",
        "ConsumerWidget",
        "CustomScrollView",
        "DateTime",
        "Divider",
        "Duration",
        "Enum",
        "Error",
        "Exception",
        "Expanded",
        "Flexible",
        "Future",
        "FutureBuilder",
        "GestureDetector",
        "GridView",
        "HookConsumerWidget",
        "Icon",
        "Image",
        "Iterable",
        "ListTile",
        "ListView",
        "List",
        "Map",
        "Object",
        "Padding",
        "Positioned",
        "ProviderScope",
        "RegExp",
        "Row",
        "SafeArea",
        "Scaffold",
        "Set",
        "SingleChildScrollView",
        "SizedBox",
        "SliverList",
        "Spacer",
        "Stack",
        "State",
        "StatefulHookConsumerWidget",
        "StatefulWidget",
        "StatelessWidget",
        "Stream",
        "StreamBuilder",
        "String",
        "Text",
        "Uri",
        "Widget",
        "WidgetRef",
        "Wrap",
    )
}
