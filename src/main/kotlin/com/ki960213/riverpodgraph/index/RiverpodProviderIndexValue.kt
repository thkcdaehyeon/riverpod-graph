package com.ki960213.riverpodgraph.index

import com.intellij.util.io.DataExternalizer
import com.ki960213.riverpodgraph.model.RiverpodProviderDeclaration
import com.ki960213.riverpodgraph.model.RiverpodProviderKind
import java.io.DataInput
import java.io.DataOutput

/** IntelliJ 인덱스에 Riverpod 프로바이더 선언으로 저장되는 직렬화 가능한 값입니다. */
data class RiverpodProviderIndexValue(
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

    /** 선언 본문을 포함한 끝 다음 텍스트 오프셋입니다. */
    val textEndOffset: Int? = null,
) {
    /** 이 인덱스 값을 파서 모델로 다시 변환합니다. */
    fun toDeclaration(): RiverpodProviderDeclaration = RiverpodProviderDeclaration(
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
        textEndOffset = textEndOffset,
    )

    /** IntelliJ 저장소를 위해 Riverpod 프로바이더 인덱스 값을 직렬화합니다. */
    object Externalizer : DataExternalizer<RiverpodProviderIndexValue> {
        /** 인덱스 값을 저장소 스트림에 씁니다. */
        override fun save(out: DataOutput, value: RiverpodProviderIndexValue) {
            out.writeUTF(value.kind.name)
            out.writeUTF(value.sourceName)
            out.writeUTF(value.providerName)
            out.writeBoolean(value.generatedSuperclassName != null)
            value.generatedSuperclassName?.let(out::writeUTF)
            out.writeUTF(value.returnType)
            out.writeUTF(value.familySignature)
            out.writeBoolean(value.keepAlive)
            out.writeBoolean(value.isPrivate)
            out.writeUTF(value.filePath)
            out.writeInt(value.textOffset)
            out.writeInt(value.line)
            out.writeBoolean(value.textEndOffset != null)
            value.textEndOffset?.let(out::writeInt)
        }

        /** 저장소 스트림에서 인덱스 값을 읽습니다. */
        override fun read(input: DataInput): RiverpodProviderIndexValue {
            val kind = RiverpodProviderKind.valueOf(input.readUTF())
            val sourceName = input.readUTF()
            val providerName = input.readUTF()
            val generatedSuperclassName = if (input.readBoolean()) input.readUTF() else null
            val returnType = input.readUTF()
            val familySignature = input.readUTF()
            val keepAlive = input.readBoolean()
            val isPrivate = input.readBoolean()
            val filePath = input.readUTF()
            val textOffset = input.readInt()
            val line = input.readInt()
            val textEndOffset = if (input.readBoolean()) input.readInt() else null

            return RiverpodProviderIndexValue(
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
                textEndOffset = textEndOffset,
            )
        }
    }
}

/** 인덱스 값을 표시와 해석에 안정적인 순서의 고유 프로바이더 선언으로 변환합니다. */
internal fun providerDeclarationsFromIndexValues(
    values: Collection<RiverpodProviderIndexValue>,
): List<RiverpodProviderDeclaration> = values
    .map { it.toDeclaration() }
    .sortedWith(
        compareBy<RiverpodProviderDeclaration> { it.filePath }
            .thenBy { it.textOffset }
            .thenBy { it.sourceName }
            .thenBy { it.providerName },
    )
    .distinctBy { declaration ->
        Triple(declaration.providerName, declaration.filePath, declaration.textOffset)
    }
