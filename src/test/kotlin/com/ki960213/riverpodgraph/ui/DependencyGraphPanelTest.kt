package com.ki960213.riverpodgraph.ui

import org.junit.Assert.assertSame
import org.junit.Test
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
}
