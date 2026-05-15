package com.ki960213.riverpodgraph.ui

import com.ki960213.riverpodgraph.model.RiverpodProviderDeclaration
import com.ki960213.riverpodgraph.model.RiverpodProviderKind
import org.junit.Assert.assertEquals
import org.junit.Test
import javax.swing.SwingUtilities

class ProvidersPanelTest {
    @Test
    fun `formats provider row with signature return type keepAlive path and line`() {
        val declaration = RiverpodProviderDeclaration(
            kind = RiverpodProviderKind.FUNCTION,
            sourceName = "user",
            providerName = "userProvider",
            generatedSuperclassName = null,
            returnType = "Future<User>",
            familySignature = "Ref ref, String id",
            keepAlive = true,
            isPrivate = false,
            filePath = "lib/user.dart",
            textOffset = 0,
            line = 5,
        )

        assertEquals(
            "userProvider(Ref ref, String id) : Future<User> keepAlive - lib/user.dart:5",
            ProvidersPanel.providerRow(declaration),
        )
    }

    @Test
    fun `formats private provider row without signature or keepAlive`() {
        val declaration = declaration(
            providerName = "_secretProvider",
            returnType = "int",
            familySignature = "",
            keepAlive = false,
            isPrivate = true,
            filePath = "lib/secret.dart",
            line = 9,
        )

        assertEquals(
            "private _secretProvider : int - lib/secret.dart:9",
            ProvidersPanel.providerRow(declaration),
        )
    }

    @Test
    fun `setProviders sorts providers by file path then line`() {
        val panel = ProvidersPanel()

        panel.setProviders(
            listOf(
                declaration(providerName = "lateProvider", filePath = "lib/z.dart", line = 1),
                declaration(providerName = "secondProvider", filePath = "lib/a.dart", line = 20),
                declaration(providerName = "firstProvider", filePath = "lib/a.dart", line = 2),
            ),
        )
        SwingUtilities.invokeAndWait {}

        assertEquals(
            listOf(
                "firstProvider : String - lib/a.dart:2",
                "secondProvider : String - lib/a.dart:20",
                "lateProvider : String - lib/z.dart:1",
            ),
            panel.providerRows(),
        )
    }

    private fun declaration(
        providerName: String = "userProvider",
        returnType: String = "String",
        familySignature: String = "",
        keepAlive: Boolean = false,
        isPrivate: Boolean = false,
        filePath: String = "lib/user.dart",
        line: Int = 1,
        textOffset: Int = 0,
    ): RiverpodProviderDeclaration = RiverpodProviderDeclaration(
        kind = RiverpodProviderKind.FUNCTION,
        sourceName = providerName.removeSuffix("Provider"),
        providerName = providerName,
        generatedSuperclassName = null,
        returnType = returnType,
        familySignature = familySignature,
        keepAlive = keepAlive,
        isPrivate = isPrivate,
        filePath = filePath,
        textOffset = textOffset,
        line = line,
    )
}
