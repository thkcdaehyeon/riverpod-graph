package com.ki960213.riverpodgraph.ui

import com.ki960213.riverpodgraph.model.RiverpodDependencyEdge
import com.ki960213.riverpodgraph.model.RiverpodMarker
import com.ki960213.riverpodgraph.model.RiverpodUsageKind
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import javax.swing.JPanel
import javax.swing.JTabbedPane

class DependencyGraphPanelTest : StringSpec({
    "컨테이너 안에 중첩된 패널을 찾는다" {
        val root = JPanel()
        val nested = JPanel()
        val panel = DependencyGraphPanel()

        nested.add(panel)
        root.add(nested)

        ((DependencyGraphPanel.findIn(root)) === (panel)) shouldBe true
    }

    "탭 패널 안에 중첩된 패널을 찾는다" {
        val tabs = JTabbedPane()
        val panel = DependencyGraphPanel()

        tabs.addTab("Dependencies", panel)

        ((DependencyGraphPanel.findIn(tabs)) === (panel)) shouldBe true
    }

    "그래프 패널이 들어있는 의존성 탭을 선택한다" {
        val tabs = JTabbedPane()
        val providersPanel = JPanel()
        val graphPanel = DependencyGraphPanel()

        tabs.addTab("Providers", providersPanel)
        tabs.addTab("Dependencies", graphPanel)
        tabs.selectedComponent = providersPanel

        ((DependencyGraphPanel.findTabbedPaneContaining(tabs, graphPanel)) === (tabs)) shouldBe true
        DependencyGraphPanel.selectTabContaining(tabs, graphPanel)

        ((tabs.selectedComponent) === (graphPanel)) shouldBe true
    }

    "선택한 프로바이더의 outgoing 의존성을 사용 종류와 마커 라벨로 렌더링한다" {
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

        (panel.selectedProvider) shouldBe "profileProvider"
        (panel.dependencyRows()) shouldBe listOf(
            "sessionProvider [extension member] [ref extension]",
            "userProvider [watch] [cycle]",
        )
    }
})
