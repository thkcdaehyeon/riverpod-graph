package com.ki960213.riverpodgraph.test

import com.intellij.testFramework.fixtures.BasePlatformTestCase

abstract class RiverpodFixtureBase : BasePlatformTestCase() {
    override fun getTestDataPath(): String =
        System.getProperty("riverpodgraph.testDataPath")
            ?: "${System.getProperty("user.dir")}/src/test/testData"

    protected fun loadFixture(name: String, destination: String = "") {
        myFixture.copyDirectoryToProject(name, destination)
    }
}
