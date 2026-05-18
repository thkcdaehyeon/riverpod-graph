package com.ki960213.riverpodgraph.activation

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.ki960213.riverpodgraph.platform.cancellableReadAction

/** riverpod_annotation을 사용하는 모듈에 Riverpod 기능을 활성화하는 프로젝트 수준 서비스입니다. */
@Service(Service.Level.PROJECT)
class RiverpodActivationService(private val project: Project) {
    /** 프로젝트 모듈 중 하나라도 riverpod_annotation을 선언하면 true를 반환합니다. */
    fun isProjectActive(): Boolean = readAction {
        ModuleManager.getInstance(project).modules.any(::isModuleActiveInReadAction)
    }

    /** 모듈 콘텐츠 루트에 Riverpod pubspec이 포함되어 있으면 true를 반환합니다. */
    fun isModuleActive(module: Module): Boolean = readAction {
        isModuleActiveInReadAction(module)
    }

    /** PSI 파일이 활성 Riverpod 모듈에 속하면 true를 반환합니다. */
    fun isFileActive(psiFile: PsiFile): Boolean {
        val virtualFile = psiFile.virtualFile ?: return isProjectActive()
        return isFileActive(virtualFile)
    }

    /** 가상 파일이 활성 Riverpod 모듈에 속하면 true를 반환합니다. */
    fun isFileActive(virtualFile: VirtualFile): Boolean = readAction {
        val module = ModuleUtilCore.findModuleForFile(virtualFile, project) ?: return@readAction false
        isModuleActiveInReadAction(module)
    }

    /** 읽기 액션 안에서 모듈 콘텐츠 루트의 pubspec을 검사해 Riverpod 활성 여부를 판단합니다. */
    private fun isModuleActiveInReadAction(module: Module): Boolean =
        ModuleRootManager.getInstance(module).contentRoots.any { root ->
            val pubspec = root.findChild("pubspec.yaml") ?: return@any false
            hasRiverpodAnnotation(pubspec)
        }

    /** pubspec 파일 내용을 읽어 riverpod_annotation 의존성이 선언되어 있는지 확인합니다. */
    private fun hasRiverpodAnnotation(pubspec: VirtualFile): Boolean {
        return try {
            PubspecDependencyParser.hasRiverpodAnnotation(VfsUtil.loadText(pubspec))
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }

    /** 프로젝트 서비스 인스턴스에 접근하는 진입점입니다. */
    companion object {
        /** 프로젝트의 Riverpod 활성화 서비스를 반환합니다. */
        fun getInstance(project: Project): RiverpodActivationService = project.service()

        /** 취소 가능한 읽기 액션으로 Riverpod 활성화 검사를 실행합니다. */
        private fun <T> readAction(action: () -> T): T = cancellableReadAction(action)
    }
}
