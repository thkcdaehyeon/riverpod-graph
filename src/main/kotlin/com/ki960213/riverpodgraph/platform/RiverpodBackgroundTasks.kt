package com.ki960213.riverpodgraph.platform

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.platform.ide.progress.withBackgroundProgress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Service(Service.Level.PROJECT)
internal class RiverpodBackgroundTaskService(
    private val scope: CoroutineScope,
) {
    fun launch(project: Project, title: String, action: suspend CoroutineScope.() -> Unit) {
        scope.launch(Dispatchers.Default) {
            withBackgroundProgress(project, title) {
                action()
            }
        }
    }
}

internal fun Project.launchRiverpodBackgroundTask(
    title: String,
    action: suspend CoroutineScope.() -> Unit,
) {
    getService(RiverpodBackgroundTaskService::class.java).launch(this, title, action)
}
