package com.ki960213.riverpodgraph.index

import com.intellij.util.indexing.*
import com.intellij.util.io.EnumeratorStringDescriptor
import com.intellij.util.io.KeyDescriptor
import com.ki960213.riverpodgraph.files.isRiverpodDartSourceFile
import com.ki960213.riverpodgraph.model.RiverpodProviderDeclaration
import com.ki960213.riverpodgraph.parser.RiverpodAnnotationParser

/** Dart 파일의 Riverpod 프로바이더 선언을 저장하는 IntelliJ 파일 기반 인덱스입니다. */
class RiverpodProviderIndex : FileBasedIndexExtension<String, RiverpodProviderIndexValue>() {
    /** 인덱스 식별자를 반환합니다. */
    override fun getName(): ID<String, RiverpodProviderIndexValue> = RIVERPOD_PROVIDER_INDEX_NAME

    /** Dart 파일의 Riverpod 프로바이더 선언에 대한 인덱스 항목을 만듭니다. */
    override fun getIndexer(): DataIndexer<String, RiverpodProviderIndexValue, FileContent> = DataIndexer { input ->
        val path = input.file.path
        if (!input.file.isRiverpodDartSourceFile()) return@DataIndexer emptyMap()

        val values = mutableMapOf<String, RiverpodProviderIndexValue>()
        for (declaration in RiverpodAnnotationParser.parse(path, input.contentAsText.toString())) {
            val value = declaration.toIndexValue()
            values[declaration.providerName] = value
            declaration.generatedSuperclassName?.let { values[it] = value }
            values[declaration.sourceName] = value
        }
        values
    }

    /** 인덱싱 대상을 생성되지 않은 Dart 소스 파일로 제한합니다. */
    override fun getInputFilter(): FileBasedIndex.InputFilter = FileBasedIndex.InputFilter { file ->
        file.isRiverpodDartSourceFile()
    }

    /** 인덱싱된 프로바이더 키의 직렬화를 제공합니다. */
    override fun getKeyDescriptor(): KeyDescriptor<String> = EnumeratorStringDescriptor.INSTANCE

    /** 인덱싱된 프로바이더 값의 직렬화를 제공합니다. */
    override fun getValueExternalizer() = RiverpodProviderIndexValue.Externalizer

    /** 현재 인덱스 스키마 버전을 반환합니다. */
    override fun getVersion(): Int = 3

    /** 인덱스 값이 파일 내용에서 파생됨을 나타냅니다. */
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

/** Riverpod 프로바이더 선언을 위한 인덱스 식별자입니다. */
val RIVERPOD_PROVIDER_INDEX_NAME: ID<String, RiverpodProviderIndexValue> = ID.create("com.ki960213.riverpodgraph.provider.index")
