package org.javacs.kt

import org.hamcrest.Matchers.*
import org.junit.Assert.assertThat
import org.junit.Test

class InstanceMemberTest: SingleFileTestFixture("completions", "InstanceMember.kt") {
    @Test fun `instance members`() {
        val completions = languageServer.textDocumentService.completion(textDocumentPosition(file, 3, 15)).get().right!!
        val labels = completions.items.map { it.label }

        assertThat(labels, hasItem("instanceFoo"))
        assertThat(labels, hasItem("extensionFoo"))
        assertThat(labels, not(hasItem("privateInstanceFoo")))
    }
}

class FunctionScopeTest: SingleFileTestFixture("completions", "FunctionScope.kt") {
    @Test fun `complete identifiers in function scope`() {
        val completions = languageServer.textDocumentService.completion(textDocumentPosition(file, 4, 10)).get().right!!
        val labels = completions.items.map { it.label }

        assertThat(labels, hasItem("anArgument"))
        assertThat(labels, hasItem("aLocal"))
        assertThat(labels, hasItem("aClassVal"))
        assertThat(labels, hasItem("aClassFun"))
        assertThat(labels, hasItem("aCompanionVal"))
        assertThat(labels, hasItem("aCompanionFun"))
    }
}

class TypesTest: SingleFileTestFixture("completions", "Types.kt") {
    @Test fun `complete a type name`() {
        val completions = languageServer.textDocumentService.completion(textDocumentPosition(file, 2, 25)).get().right!!
        val labels = completions.items.map { it.label }

        assertThat(labels, hasItem("SomeInnerClass"))
        assertThat(labels, hasItem("String"))
        assertThat(labels, hasItem("SomeInnerObject"))
        assertThat(labels, hasItem("SomeAlias"))
    }
}

class FillEmptyBodyTest: SingleFileTestFixture("completions", "FillEmptyBody.kt") {
    @Test fun `fill an empty body`() {
        replace(file, 2, 16, "", """"
            Callee.
""")
        val completions = languageServer.textDocumentService.completion(textDocumentPosition(file, 3, 20)).get().right!!
        val labels = completions.items.map { it.label }

        assertThat(labels, hasItem("bar"))
    }
}

class ConstructorTest: SingleFileTestFixture("completions", "Constructor.kt") {
    @Test fun `complete a constructor`() {
        val completions = languageServer.textDocumentService.completion(textDocumentPosition(file, 2, 10)).get().right!!
        val labels = completions.items.map { it.label }

        assertThat(labels, hasItem("SomeConstructor"))
    }
}

class MiddleOfFunctionTest: SingleFileTestFixture("completions", "MiddleOfFunction.kt") {
    @Test fun `complete in the middle of a function`() {
        val completions = languageServer.textDocumentService.completion(textDocumentPosition(file, 3, 11)).get().right!!
        val labels = completions.items.map { it.label }

        assertThat(labels, hasItem("subSequence"))
    }
}

class BackquotedFunctionTest: SingleFileTestFixture("completions", "BackquotedFunction.kt") {
    @Test fun `complete with backquotes`() {
        val completions = languageServer.textDocumentService.completion(textDocumentPosition(file, 2, 7)).get().right!!
        val insertText = completions.items.map { it.insertText }

        assertThat(insertText, hasItem("`fun that needs backquotes`()"))
    }
}

class CompleteStaticsTest: SingleFileTestFixture("completions", "Statics.kt") {
    @Test fun `java static method`() {
        val completions = languageServer.textDocumentService.completion(textDocumentPosition(file, 4, 16)).get().right!!
        val labels = completions.items.map { it.label }

        assertThat(labels, hasItem("isNull"))
    }

    @Test fun `object method`() {
        val completions = languageServer.textDocumentService.completion(textDocumentPosition(file, 5, 17)).get().right!!
        val labels = completions.items.map { it.label }

        assertThat(labels, hasItem("objectFun"))
    }

    @Test fun `companion object method`() {
        val completions = languageServer.textDocumentService.completion(textDocumentPosition(file, 6, 16)).get().right!!
        val labels = completions.items.map { it.label }

        assertThat(labels, hasItem("companionFun"))
    }
}

class VisibilityTest: SingleFileTestFixture("completions", "Visibility.kt") {
    @Test fun `find tricky visibility members`() {
        val completions = languageServer.textDocumentService.completion(textDocumentPosition(file, 3, 10)).get().right!!
        val labels = completions.items.map { it.label }

        assertThat(labels, hasItems("privateThisFun", "protectedThisFun", "publicThisFun", "privateThisCompanionFun", "protectedThisCompanionFun", "publicThisCompanionFun", "privateTopLevelFun"))
        assertThat(labels, hasItems("protectedSuperFun", "publicSuperFun", "protectedSuperCompanionFun", "publicSuperCompanionFun"))
        assertThat(labels, not(hasItems("privateSuperFun", "privateSuperCompanionFun")))
    }
    
    @Test fun `determine visibility after edits`() {
        replace("Visibility.kt", 3, 9, "p", "/* break */ p")

        val completions = languageServer.textDocumentService.completion(textDocumentPosition(file, 3, 10 + 12)).get().right!!
        val labels = completions.items.map { it.label }

        assertThat(labels, hasItems("privateThisFun", "protectedThisFun", "publicThisFun", "privateThisCompanionFun", "protectedThisCompanionFun", "publicThisCompanionFun", "privateTopLevelFun"))
        assertThat(labels, hasItems("protectedSuperFun", "publicSuperFun", "protectedSuperCompanionFun", "publicSuperCompanionFun"))
        assertThat(labels, not(hasItems("privateSuperFun", "privateSuperCompanionFun")))
    }
}