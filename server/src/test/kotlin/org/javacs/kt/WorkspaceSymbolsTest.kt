package org.javacs.kt

import org.eclipse.lsp4j.ClientCapabilities
import org.eclipse.lsp4j.SymbolCapabilities
import org.eclipse.lsp4j.SymbolKind
import org.eclipse.lsp4j.WorkspaceClientCapabilities
import org.eclipse.lsp4j.WorkspaceSymbolParams
import org.eclipse.lsp4j.WorkspaceSymbolResolveSupportCapabilities
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.hasItem
import org.hamcrest.Matchers.not
import org.junit.Assert.assertThat
import org.junit.Test

class WorkspaceSymbolsTest : SingleFileTestFixture("symbols", "DocumentSymbols.kt") {
    @Test fun `find symbols in OtherFileSymbols`() {
        val found = languageServer.workspaceService.symbol(WorkspaceSymbolParams("")).get().right
        val byKind = found.groupBy({ it.kind }, { it.name })
        val all = found.map { it.name }.toList()

        assertThat(byKind[SymbolKind.Class], hasItem("OtherFileSymbols"))
        assertThat(byKind[SymbolKind.Constructor], hasItem("OtherFileSymbols"))
        assertThat(byKind[SymbolKind.Property], hasItem("otherFileProperty"))
        assertThat(byKind[SymbolKind.Function], hasItem("otherFileFunction"))
        assertThat(all, not(hasItem("aFunctionArg")))
        assertThat(all, not(hasItem("aConstructorArg")))
        assertThat(all, not(hasItem("otherFileLocalVariable")))
    }

    @Test fun `returns location information if resolve is not supported by the client`() {
        languageServer.workspaceService.initialize(clientCapabilities(false))
        val found = languageServer.workspaceService.symbol(WorkspaceSymbolParams("")).get().right
        assertThat(found.all { s -> s.location.isLeft }, equalTo(true))
    }

    @Test fun `returns no location information if resolve is supported by the client`() {
        languageServer.workspaceService.initialize(clientCapabilities(true))
        val found = languageServer.workspaceService.symbol(WorkspaceSymbolParams("")).get().right
        assertThat(found.all { s -> s.location.isRight }, equalTo(true))
    }

    private fun clientCapabilities(resolveSupported: Boolean): ClientCapabilities {
        val properties = if (resolveSupported) listOf("location.range") else emptyList()

        val workspaceClientCapabilities = WorkspaceClientCapabilities()
        val symbolCapabilities = SymbolCapabilities()
        val workspaceSymbolResolveSupportCapabilities = WorkspaceSymbolResolveSupportCapabilities()
        workspaceSymbolResolveSupportCapabilities.properties = properties
        symbolCapabilities.resolveSupport = workspaceSymbolResolveSupportCapabilities
        workspaceClientCapabilities.symbol = symbolCapabilities
        return ClientCapabilities(workspaceClientCapabilities, null, null)
    }
}
