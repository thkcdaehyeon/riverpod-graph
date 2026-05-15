package com.ki960213.riverpodgraph.activation

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile

@Service(Service.Level.PROJECT)
class RiverpodActivationService(private val project: Project) {
    fun isProjectActive(): Boolean = readAction {
        ModuleManager.getInstance(project).modules.any(::isModuleActiveInReadAction)
    }

    fun isModuleActive(module: Module): Boolean = readAction {
        isModuleActiveInReadAction(module)
    }

    private fun isModuleActiveInReadAction(module: Module): Boolean {
        return ModuleRootManager.getInstance(module).contentRoots.any { root ->
            val pubspec = root.findChild("pubspec.yaml") ?: return@any false
            hasRiverpodAnnotation(pubspec)
        }
    }

    private fun hasRiverpodAnnotation(pubspec: VirtualFile): Boolean {
        return try {
            PubspecDependencyParser.hasRiverpodAnnotation(VfsUtil.loadText(pubspec))
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        fun getInstance(project: Project): RiverpodActivationService = project.service()

        private fun <T> readAction(action: () -> T): T = ReadAction.compute<T, RuntimeException> { action() }
    }
}
