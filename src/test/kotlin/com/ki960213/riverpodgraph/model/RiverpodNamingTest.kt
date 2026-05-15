package com.ki960213.riverpodgraph.model

import org.junit.Assert.assertEquals
import org.junit.Test

class RiverpodNamingTest {
    @Test
    fun `function provider keeps original lower camel name`() {
        assertEquals("userProvider", RiverpodNaming.providerForFunction("user"))
        assertEquals("_privateUserProvider", RiverpodNaming.providerForFunction("_privateUser"))
        assertEquals("userProvider", RiverpodNaming.providerForFunction("User"))
    }

    @Test
    fun `function provider strips default trailing notifier`() {
        assertEquals("fooProvider", RiverpodNaming.providerForFunction("fooNotifier"))
        assertEquals("_fooProvider", RiverpodNaming.providerForFunction("_fooNotifier"))
    }

    @Test
    fun `class provider uses generator lower first naming`() {
        assertEquals("userControllerProvider", RiverpodNaming.providerForClass("UserController"))
        assertEquals("_privateControllerProvider", RiverpodNaming.providerForClass("_PrivateController"))
        assertEquals("uRLCacheProvider", RiverpodNaming.providerForClass("URLCache"))
    }

    @Test
    fun `class provider strips default trailing notifier`() {
        assertEquals("counterProvider", RiverpodNaming.providerForClass("CounterNotifier"))
        assertEquals("_privateProvider", RiverpodNaming.providerForClass("_PrivateNotifier"))
    }

    @Test
    fun `generated superclass keeps public class name`() {
        assertEquals("_${'$'}" + "UserController", RiverpodNaming.generatedSuperclassForClass("UserController"))
        assertEquals("_${'$'}" + "PrivateController", RiverpodNaming.generatedSuperclassForClass("_PrivateController"))
    }
}
