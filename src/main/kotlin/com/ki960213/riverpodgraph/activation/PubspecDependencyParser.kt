package com.ki960213.riverpodgraph.activation

/** pubspec.yaml 내용에서 Riverpod 의존성을 감지합니다. */
object PubspecDependencyParser {
    /** dependencies 또는 dev_dependencies에 riverpod_annotation이 포함되어 있으면 true를 반환합니다. */
    fun hasRiverpodAnnotation(content: String): Boolean {
        var inDependencyBlock = false
        var dependencyEntryIndent: Int? = null

        for (line in content.lines()) {
            val contentLine = line.substringBefore("#").trimEnd()
            val trimmed = contentLine.trim()
            if (trimmed.isEmpty()) {
                continue
            }

            val indent = contentLine.indexOfFirst { !it.isWhitespace() }.let { index ->
                if (index == -1) 0 else index
            }
            val key = yamlKey(trimmed) ?: continue

            if (indent == 0 && (key == "dependencies" || key == "dev_dependencies")) {
                inDependencyBlock = true
                dependencyEntryIndent = null
                continue
            }

            if (inDependencyBlock && indent == 0) {
                inDependencyBlock = false
                dependencyEntryIndent = null
            }

            if (!inDependencyBlock) {
                continue
            }

            if (dependencyEntryIndent == null) {
                dependencyEntryIndent = indent
            }

            if (indent == dependencyEntryIndent && key == "riverpod_annotation") {
                return true
            }
        }

        return false
    }

    /** YAML 행에서 콜론 앞의 키를 추출하고 따옴표를 제거합니다. */
    private fun yamlKey(line: String): String? {
        val separator = line.indexOf(':')
        if (separator == -1) return null

        return unquote(line.substring(0, separator).trim())
    }

    /** 따옴표로 감싼 YAML 키에서 바깥따옴표만 제거합니다. */
    private fun unquote(key: String): String {
        if (key.length < 2) return key

        val first = key.first()
        val last = key.last()
        return if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
            key.substring(1, key.length - 1)
        } else {
            key
        }
    }
}
