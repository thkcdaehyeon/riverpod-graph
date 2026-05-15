package com.ki960213.riverpodgraph.index

import com.intellij.util.indexing.DataIndexer
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FileBasedIndexExtension
import com.intellij.util.indexing.FileContent
import com.intellij.util.indexing.ID
import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.EnumeratorStringDescriptor
import com.intellij.util.io.KeyDescriptor

class RiverpodProviderIndex : FileBasedIndexExtension<String, String>() {
    override fun getName(): ID<String, String> = NAME
    override fun getIndexer(): DataIndexer<String, String, FileContent> = DataIndexer { emptyMap() }
    override fun getInputFilter(): FileBasedIndex.InputFilter = FileBasedIndex.InputFilter { false }
    override fun getKeyDescriptor(): KeyDescriptor<String> = EnumeratorStringDescriptor.INSTANCE
    override fun getValueExternalizer(): DataExternalizer<String> = EnumeratorStringDescriptor.INSTANCE
    override fun getVersion(): Int = 1
    override fun dependsOnFileContent(): Boolean = true

    companion object {
        val NAME: ID<String, String> = ID.create("com.ki960213.riverpodgraph.provider.index")
    }
}
