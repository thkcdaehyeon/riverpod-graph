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
        isFileActiveInReadAction(virtualFile)
    }

    /** 읽기 액션 안에서 가상 파일이 활성 Riverpod 모듈에 속하는지 반환합니다. */
    internal fun isFileActiveInReadAction(virtualFile: VirtualFile): Boolean {
        nearestPubspec(virtualFile)?.let { pubspec ->
            return hasRiverpodAnnotation(pubspec)
        }

        val module = ModuleUtilCore.findModuleForFile(virtualFile, project) ?: return false
        return isModuleActiveInReadAction(module)
    }

    /** 읽기 액션 안에서 모듈 콘텐츠 루트와 그 하위 pubspec을 검사해 Riverpod 활성 여부를 판단합니다. */
    private fun isModuleActiveInReadAction(module: Module): Boolean =
        ModuleRootManager.getInstance(module).contentRoots.any(::hasRiverpodPubspecAtOrBelow)

    /** 파일 자신 또는 부모 디렉터리에서 가장 가까운 pubspec.yaml을 찾습니다. */
    private fun nearestPubspec(file: VirtualFile): VirtualFile? {
        var directory = if (file.isDirectory) file else file.parent
        while (directory != null) {
            directory.findChild(PUBSPEC_YAML)?.let { return it }
            directory = directory.parent
        }

        return null
    }

    /** 지정 디렉터리와 하위 패키지 중 Riverpod pubspec이 하나라도 있는지 확인합니다. */
    private fun hasRiverpodPubspecAtOrBelow(root: VirtualFile): Boolean {
        if (!root.isDirectory) {
            return false
        }

        root.findChild(PUBSPEC_YAML)?.let { pubspec ->
            if (hasRiverpodAnnotation(pubspec)) {
                return true
            }
        }

        return root.children.any { child ->
            child.isDirectory &&
                    child.name !in IGNORED_PACKAGE_SCAN_DIRECTORIES &&
                    hasRiverpodPubspecAtOrBelow(child)
        }
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
        private const val PUBSPEC_YAML = "pubspec.yaml"
        private val IGNORED_PACKAGE_SCAN_DIRECTORIES = setOf(".dart_tool", ".git", "build")

        /** 프로젝트의 Riverpod 활성화 서비스를 반환합니다. */
        fun getInstance(project: Project): RiverpodActivationService = project.service()

        /** 취소 가능한 읽기 액션으로 Riverpod 활성화 검사를 실행합니다. */
        private fun <T> readAction(action: () -> T): T = cancellableReadAction(action)
    }
}
