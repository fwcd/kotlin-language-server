package org.javacs.kt

import com.google.gson.Gson
import org.eclipse.lsp4j.ExecuteCommandParams
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.javacs.kt.command.RESOLVE_MAIN

class NoMainResolve : SingleFileTestFixture("resolvemain", "NoMain.kt") {
    @Test
    fun `Should not find any main class info`() {
        val root = testResourcesRoot().resolve(workspaceRoot)
        val executeCommandParams = ExecuteCommandParams(RESOLVE_MAIN, listOf(Gson().toJsonTree(root.resolve(file).toUri().toString(), )))
        
        val commandResult = languageServer.workspaceService.executeCommand(executeCommandParams).get()

        assertNotNull(commandResult)
        val mainInfo = commandResult as Map<String, String>
        assertNull(mainInfo["mainClass"])
        assertEquals(root.toString(), mainInfo["projectRoot"])
    }
}


class SimpleMainResolve : SingleFileTestFixture("resolvemain", "Simple.kt") {
    @Test
    fun `Should resolve correct main class of simple file`() {
        val root = testResourcesRoot().resolve(workspaceRoot)
        val executeCommandParams = ExecuteCommandParams(RESOLVE_MAIN, listOf(Gson().toJsonTree(root.resolve(file).toUri().toString())))
        
        val commandResult = languageServer.workspaceService.executeCommand(executeCommandParams).get()

        assertNotNull(commandResult)
        val mainInfo = commandResult as Map<String, Any>
        assertEquals("test.SimpleKt", mainInfo["mainClass"])
        assertEquals(Range(Position(2, 0), Position(4, 1)), mainInfo["range"])
        assertEquals(root.toString(), mainInfo["projectRoot"])
    }
}


class JvmNameAnnotationMainResolve : SingleFileTestFixture("resolvemain", "JvmNameAnnotation.kt") {
    @Test
    fun `Should resolve correct main class of file annotated with JvmName`() {
        val root = testResourcesRoot().resolve(workspaceRoot)
        val executeCommandParams = ExecuteCommandParams(RESOLVE_MAIN, listOf(Gson().toJsonTree(root.resolve(file).toUri().toString())))
        
        val commandResult = languageServer.workspaceService.executeCommand(executeCommandParams).get()

        assertNotNull(commandResult)
        val mainInfo = commandResult as Map<String, Any>
        assertEquals("com.mypackage.name.Potato", mainInfo["mainClass"])
        assertEquals(Range(Position(5, 0), Position(7, 1)), mainInfo["range"])
        assertEquals(root.toString(), mainInfo["projectRoot"])
    }
}

class CompanionObjectMainResolve : SingleFileTestFixture("resolvemain", "CompanionObject.kt") {
    @Test
    fun `Should resolve correct main class of main function inside companion object`() {
        val root = testResourcesRoot().resolve(workspaceRoot)
        val executeCommandParams = ExecuteCommandParams(RESOLVE_MAIN, listOf(Gson().toJsonTree(root.resolve(file).toUri().toString())))
        
        val commandResult = languageServer.workspaceService.executeCommand(executeCommandParams).get()

        assertNotNull(commandResult)
        val mainInfo = commandResult as Map<String, Any>
        assertEquals("test.my.companion.SweetPotato", mainInfo["mainClass"])
        assertEquals(Range(Position(8, 8), Position(11, 9)), mainInfo["range"])
        assertEquals(root.toString(), mainInfo["projectRoot"])
    }
}
