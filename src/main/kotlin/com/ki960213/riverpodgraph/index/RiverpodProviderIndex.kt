package com.ki960213.riverpodgraph.index

import com.ki960213.riverpodgraph.model.RiverpodProviderDeclaration
import com.ki960213.riverpodgraph.parser.RiverpodAnnotationParser
import com.intellij.util.indexing.DataIndexer
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FileBasedIndexExtension
import com.intellij.util.indexing.FileContent
import com.intellij.util.indexing.ID
import com.intellij.util.io.EnumeratorStringDescriptor
import com.intellij.util.io.KeyDescriptor

class RiverpodProviderIndex : FileBasedIndexExtension<String, RiverpodProviderIndexValue>() {
    override fun getName(): ID<String, RiverpodProviderIndexValue> = NAME

    override fun getIndexer(): DataIndexer<String, RiverpodProviderIndexValue, FileContent> = DataIndexer { input ->
        val path = input.file.path
        if (!path.endsWith(".dart") || path.endsWith(".g.dart")) {
            return@DataIndexer emptyMap()
        }

        val values = mutableMapOf<String, RiverpodProviderIndexValue>()
        for (declaration in RiverpodAnnotationParser.parse(path, input.contentAsText.toString())) {
            val value = declaration.toIndexValue()
            values[declaration.providerName] = value
            declaration.generatedSuperclassName?.let { values[it] = value }
            values[declaration.sourceName] = value
        }
        values
    }

    override fun getInputFilter(): FileBasedIndex.InputFilter = FileBasedIndex.InputFilter { file ->
        !file.fileType.isBinary &&
            file.name.endsWith(".dart") &&
            !file.name.endsWith(".g.dart")
    }

    override fun getKeyDescriptor(): KeyDescriptor<String> = EnumeratorStringDescriptor.INSTANCE
    override fun getValueExternalizer() = RiverpodProviderIndexValue.Externalizer
    override fun getVersion(): Int = 2
    override fun dependsOnFileContent(): Boolean = true

}

private fun RiverpodProviderDeclaration.toIndexValue(): RiverpodProviderIndexValue = RiverpodProviderIndexValue(
    kind = kind,
    sourceName = sourceName,
    providerName = providerName,
    generatedSuperclassName = generatedSuperclassName,
    returnType = returnType,
    familySignature = familySignature,
    keepAlive = keepAlive,
    isPrivate = isPrivate,
    filePath = filePath,
    textOffset = textOffset,
    line = line,
)

val NAME: ID<String, RiverpodProviderIndexValue> = ID.create("com.ki960213.riverpodgraph.provider.index")