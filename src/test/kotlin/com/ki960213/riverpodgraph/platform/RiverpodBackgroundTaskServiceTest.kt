package com.ki960213.riverpodgraph.platform

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class RiverpodBackgroundTaskServiceTest : BasePlatformTestCase() {
    fun `test background task service is available from project container`() {
        assertNotNull(project.getService(RiverpodBackgroundTaskService::class.java))
    }
}
