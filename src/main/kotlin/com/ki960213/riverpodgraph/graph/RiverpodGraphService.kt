package com.ki960213.riverpodgraph.graph

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiTreeChangeAdapter
import com.intellij.psi.PsiTreeChangeEvent
import com.intellij.util.Alarm
import com.ki960213.riverpodgraph.activation.RiverpodActivationService
import com.ki960213.riverpodgraph.activation.RiverpodActiveSourceScope
import com.ki960213.riverpodgraph.model.RiverpodProviderDeclaration
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference

enum class RiverpodProviderLoadStatus {
    INACTIVE,
    INDEXING,
    EMPTY,
    READY,
}

data class RiverpodProviderSnapshot(
    val status: RiverpodProviderLoadStatus,
    val declarations: List<RiverpodProviderDeclaration>,
)

@Service(Service.Level.PROJECT)
class RiverpodGraphService(private val project: Project) {
    private val providerCache = AtomicReference<List<RiverpodProviderDeclaration>?>(null)
    private val invalidationAlarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, project)
    private val invalidationListeners = CopyOnWriteArrayList<() -> Unit>()

    init {
        PsiManager.getInstance(project).addPsiTreeChangeListener(
            object : PsiTreeChangeAdapter() {
                override fun childrenChanged(event: PsiTreeChangeEvent) = scheduleInvalidate()
                override fun childAdded(event: PsiTreeChangeEvent) = scheduleInvalidate()
                override fun childRemoved(event: PsiTreeChangeEvent) = scheduleInvalidate()
                override fun childReplaced(event: PsiTreeChangeEvent) = scheduleInvalidate()
                override fun propertyChanged(event: PsiTreeChangeEvent) = scheduleInvalidate()
            },
            project,
        )
    }

    fun providerSnapshot(): RiverpodProviderSnapshot {
        if (!RiverpodActivationService.getInstance(project).isProjectActive()) {
            return RiverpodProviderSnapshot(RiverpodProviderLoadStatus.INACTIVE, emptyList())
        }
        if (DumbService.isDumb(project)) {
            return RiverpodProviderSnapshot(RiverpodProviderLoadStatus.INDEXING, emptyList())
        }

        val declarations = providerDeclarations()
        val status = if (declarations.isEmpty()) {
            RiverpodProviderLoadStatus.EMPTY
        } else {
            RiverpodProviderLoadStatus.READY
        }
        return RiverpodProviderSnapshot(status, declarations)
    }

    fun providerDeclarations(): List<RiverpodProviderDeclaration> {
        if (DumbService.isDumb(project)) return emptyList()
        providerCache.get()?.let { return it }

        val rebuilt = RiverpodActiveSourceScope.getInstance(project).providerDeclarations()
        providerCache.compareAndSet(null, rebuilt)
        return providerCache.get() ?: rebuilt
    }

    internal fun providerDeclarationsInReadAction(): List<RiverpodProviderDeclaration> {
        if (DumbService.isDumb(project)) return emptyList()
        providerCache.get()?.let { return it }

        val rebuilt = RiverpodActiveSourceScope.getInstance(project).providerDeclarationsInReadAction()
        providerCache.compareAndSet(null, rebuilt)
        return providerCache.get() ?: rebuilt
    }

    fun invalidate() {
        providerCache.set(null)
        notifyInvalidated()
    }

    private fun notifyInvalidated() {
        invalidationListeners.forEach { listener -> listener() }
    }

    fun addInvalidationListener(listener: () -> Unit) {
        invalidationListeners += listener
    }

    private fun scheduleInvalidate() {
        providerCache.set(null)
        invalidationAlarm.cancelAllRequests()
        invalidationAlarm.addRequest({ notifyInvalidated() }, DEBOUNCE_MS)
    }

    companion object {
        private const val DEBOUNCE_MS = 200

        fun getInstance(project: Project): RiverpodGraphService = project.service()
    }
}
