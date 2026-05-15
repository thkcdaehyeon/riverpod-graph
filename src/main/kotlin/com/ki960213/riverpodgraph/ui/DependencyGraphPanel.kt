package com.ki960213.riverpodgraph.ui

import java.awt.BorderLayout
import java.awt.Component
import java.awt.Container
import javax.swing.JPanel

class DependencyGraphPanel : JPanel(BorderLayout()) {
    var selectedProvider: String? = null
        private set

    fun showProvider(providerName: String) {
        selectedProvider = providerName
    }

    companion object {
        fun findIn(component: Component?): DependencyGraphPanel? {
            if (component == null) {
                return null
            }
            if (component is DependencyGraphPanel) {
                return component
            }
            if (component is Container) {
                for (child in component.components) {
                    findIn(child)?.let { return it }
                }
            }

            return null
        }
    }
}
