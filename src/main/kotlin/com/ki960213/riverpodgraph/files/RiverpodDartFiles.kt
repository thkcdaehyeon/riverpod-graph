package com.ki960213.riverpodgraph.files

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile

/** Riverpod 분석 대상이 되는 생성되지 않은 Dart 소스 파일 이름인지 반환합니다. */
internal fun isRiverpodDartSourceFileName(fileName: String): Boolean {
    if (!fileName.endsWith(".dart")) return false
    return !fileName.removeSuffix(".dart").contains(".")
}

/** PSI 파일이 Riverpod 분석 대상 Dart 소스 파일인지 반환합니다. */
internal fun PsiFile.isRiverpodDartSourceFile(): Boolean =
    isRiverpodDartSourceFileName(virtualFile?.name ?: name)

/** 가상 파일이 Riverpod 분석 대상 Dart 소스 파일인지 반환합니다. */
internal fun VirtualFile.isRiverpodDartSourceFile(): Boolean =
    !fileType.isBinary && isRiverpodDartSourceFileName(name)
