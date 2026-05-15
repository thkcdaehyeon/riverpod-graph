package com.ki960213.riverpodgraph.ui

import com.ki960213.riverpodgraph.model.RiverpodDependencyEdge
import com.ki960213.riverpodgraph.model.RiverpodMarker
import com.ki960213.riverpodgraph.model.RiverpodUsageKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import javax.swing.JTabbedPane
import javax.swing.JPanel

class DependencyGraphPanelTest {
    @Test
    fun `finds panel nested inside container`() {
        val root = JPanel()
        val nested = JPanel()
        val panel = DependencyGraphPanel()

        nested.add(panel)
        root.add(nested)

        assertSame(panel, DependencyGraphPanel.findIn(root))
    }

    @Test
    fun `finds panel nested inside tabbed pane`() {
        val tabs = JTabbedPane()
        val panel = DependencyGraphPanel()

        tabs.addTab("Dependencies", panel)

        assertSame(panel, DependencyGraphPanel.findIn(tabs))
    }

    @Test
    fun `selects dependency tab containing graph panel`() {
        val tabs = JTabbedPane()
        val providersPanel = JPanel()
        val graphPanel = DependencyGraphPanel()

        tabs.addTab("Providers", providersPanel)
        tabs.addTab("Dependencies", graphPanel)
        tabs.selectedComponent = providersPanel

        assertSame(tabs, DependencyGraphPanel.findTabbedPaneContaining(tabs, graphPanel))
        DependencyGraphPanel.selectTabContaining(tabs, graphPanel)

        assertSame(graphPanel, tabs.selectedComponent)
    }

    @Test
    fun `renders selected provider outgoing dependency rows with usage and marker labels`() {
        val panel = DependencyGraphPanel()

        panel.showProviderGraph(
            "profileProvider",
            listOf(
                RiverpodDependencyEdge(
                    fromProvider = "profileProvider",
                    toProvider = "userProvider",
                    usageKind = RiverpodUsageKind.WATCH,
                    marker = RiverpodMarker.CYCLE,
                ),
                RiverpodDependencyEdge(
                    fromProvider = "profileProvider",
                    toProvider = "sessionProvider",
                    usageKind = RiverpodUsageKind.EXTENSION_MEMBER,
                    marker = RiverpodMarker.REF_EXTENSION,
                ),
                RiverpodDependencyEdge(
                    fromProvider = "otherProvider",
                    toProvider = "ignoredProvider",
                    usageKind = RiverpodUsageKind.READ,
                ),
            ),
        )

        assertEquals("profileProvider", panel.selectedProvider)
        assertEquals(
            listOf(
                "sessionProvider [extension member] [ref extension]",
                "userProvider [watch] [cycle]",
            ),
            panel.dependencyRows(),
        )
    }
}
