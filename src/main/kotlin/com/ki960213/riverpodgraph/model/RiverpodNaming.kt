package com.ki960213.riverpodgraph.model

/** Dart 선언에서 생성되는 Riverpod 심볼 이름을 만듭니다. */
object RiverpodNaming {
    /** Riverpod 함수에 대한 생성된 프로바이더 이름을 반환합니다. */
    fun providerForFunction(name: String): String = "${name.providerBaseName()}Provider"

    /** Riverpod notifier 클래스에 대한 생성된 프로바이더 이름을 반환합니다. */
    fun providerForClass(className: String): String = "${className.providerBaseName()}Provider"

    /** notifier 클래스에 기대되는 생성된 상위 클래스 이름을 반환합니다. */
    fun generatedSuperclassForClass(className: String): String = "_${'$'}${className.publicName()}"

    private fun String.providerBaseName(): String {
        val prefix = takeWhile { it == '_' }
        val visible = drop(prefix.length)

        if (visible.isEmpty()) {
            return this
        }

        val baseName = visible.removeSuffix(NOTIFIER_SUFFIX)
        return "$prefix${baseName.lowerFirstAlphabetical()}"
    }

    private fun String.lowerFirstAlphabetical(): String {
        val index = indexOfFirst { it.isLetter() }

        if (index == -1) {
            return this
        }

        return replaceRange(index, index + 1, this[index].lowercase())
    }

    private fun String.publicName(): String = removePrefix("_")

    private const val NOTIFIER_SUFFIX = "Notifier"
}
