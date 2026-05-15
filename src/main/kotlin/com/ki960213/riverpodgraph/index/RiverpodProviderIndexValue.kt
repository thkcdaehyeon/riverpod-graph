package com.ki960213.riverpodgraph.index

import com.intellij.util.io.DataExternalizer
import com.ki960213.riverpodgraph.model.RiverpodProviderDeclaration
import com.ki960213.riverpodgraph.model.RiverpodProviderKind
import java.io.DataInput
import java.io.DataOutput

data class RiverpodProviderIndexValue(
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
) {
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
    )

    object Externalizer : DataExternalizer<RiverpodProviderIndexValue> {
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
        }

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
            )
        }
    }
}
