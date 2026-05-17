package com.ki960213.riverpodgraph.platform

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project

internal fun <T> cancellableReadAction(action: () -> T): T =
    ReadAction.nonBlocking<T> { action() }
        .executeSynchronously()

internal fun <T> smartCancellableReadAction(project: Project, action: () -> T): T =
    ReadAction.nonBlocking<T> { action() }
        .inSmartMode(project)
        .executeSynchronously()
