package com.ki960213.riverpodgraph.model

object RiverpodNaming {
    fun providerForFunction(name: String): String = "${name.providerBaseName()}Provider"

    fun providerForClass(className: String): String = "${className.providerBaseName()}Provider"

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
