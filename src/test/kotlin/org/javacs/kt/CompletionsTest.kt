package org.javacs.kt

import org.eclipse.lsp4j.CompletionList
import org.hamcrest.Matchers.*
import org.junit.Assert.assertThat
import org.junit.Test

class InstanceMemberTest: SingleFileTestFixture("completions", "InstanceMember.kt") {
    @Test fun `complete instance members`() {
        val completions = languageServer.textDocumentService.completion(completionParams(file, 3, 15)).get().right!!
        val labels = completions.items.map { it.label }

        assertThat(labels, hasItem("instanceFoo"))
        assertThat(labels, hasItem("extensionFoo"))
        assertThat(labels, hasItem("fooVar"))
        assertThat(labels, not(hasItem("privateInstanceFoo")))
        assertThat(labels, not(hasItem("getFooVar")))
        assertThat(labels, not(hasItem("setFooVar")))

        assertThat("Reports instanceFoo only once", completions.items.filter { it.label == "instanceFoo" }, hasSize(1))
        assertThat("Reports extensionFoo only once", completions.items.filter { it.label == "extensionFoo" }, hasSize(1))
    }

    @Test fun `find count extension function`() {
        val completions = languageServer.textDocumentService.completion(completionParams(file, 8, 14)).get().right!!
        val labels = completions.items.map { it.label }

        assertThat(labels, hasItem("count"))
    }

    @Test fun `complete method reference`() {
        val completions = languageServer.textDocumentService.completion(completionParams(file, 13, 16)).get().right!!
        val labels = completions.items.map { it.label }

        assertThat(labels, hasItem("instanceFoo"))
        // assertThat(labels, not(hasItem("extensionFoo"))) Kotlin will probably implement this later
        assertThat(labels, hasItem("fooVar"))
        assertThat(labels, not(hasItem("privateInstanceFoo")))
        assertThat(labels, not(hasItem("getFooVar")))
        assertThat(labels, not(hasItem("setFooVar")))

        assertThat(completions.items.filter { it.label == "instanceFoo" }.firstOrNull(), hasProperty("insertText", equalTo("instanceFoo")))
    }

    @Test fun `complete unqualified function reference`() {
        val completions = languageServer.textDocumentService.completion(completionParams(file, 17, 8)).get().right!!
        val labels = completions.items.map { it.label }

        assertThat(labels, hasItem("findFunctionReference"))
    }

    @Test fun `complete a function name within a call`() {
        val completions = languageServer.textDocumentService.completion(completionParams(file, 22, 27)).get().right!!
        val labels = completions.items.map { it.label }

        assertThat(labels, hasItem("findFunctionReference"))
        assertThat(labels, not(hasItem("instanceFoo")))
    }
}

class InstanceMembersJava: SingleFileTestFixture("completions", "InstanceMembersJava.kt") {
    @Test fun `convert getFileName to fileName`() {
        val completions = languageServer.textDocumentService.completion(completionParams(file, 4, 14)).get().right!!
        val labels = completions.items.map { it.label }

        assertThat(labels, hasItem("fileName"))
        assertThat(labels, not(hasItem("getFileName")))
    }
}

class FunctionScopeTest: SingleFileTestFixture("completions", "FunctionScope.kt") {
    @Test fun `complete identifiers in function scope`() {
        val completions = languageServer.textDocumentService.completion(completionParams(file, 4, 10)).get().right!!
        val labels = completions.items.map { it.label }

        assertThat(labels, hasItem("anArgument"))
        assertThat(labels, hasItem("aLocal"))
        assertThat(labels, hasItem("aClassVal"))
        assertThat(labels, hasItem("aClassFun"))
        assertThat(labels, hasItem("aCompanionVal"))
        assertThat(labels, hasItem("aCompanionFun"))

        assertThat("Reports anArgument only once", completions.items.filter { it.label == "anArgument" }, hasSize(1))
        assertThat("Reports aLocal only once", completions.items.filter { it.label == "aLocal" }, hasSize(1))
        assertThat("Reports aClassVal only once", completions.items.filter { it.label == "aClassVal" }, hasSize(1))
        assertThat("Reports aClassFun only once", completions.items.filter { it.label == "aClassFun" }, hasSize(1))
        assertThat("Reports aCompanionVal only once", completions.items.filter { it.label == "aCompanionVal" }, hasSize(1))
        assertThat("Reports aCompanionFun only once", completions.items.filter { it.label == "aCompanionFun" }, hasSize(1))
    }
}

class TypesTest: SingleFileTestFixture("completions", "Types.kt") {
    @Test fun `complete a type name`() {
        val completions = languageServer.textDocumentService.completion(completionParams(file, 2, 25)).get().right!!
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
        val completions = languageServer.textDocumentService.completion(completionParams(file, 3, 20)).get().right!!
        val labels = completions.items.map { it.label }

        assertThat(labels, hasItem("bar"))
    }
}

class ConstructorTest: SingleFileTestFixture("completions", "Constructor.kt") {
    @Test fun `complete a constructor`() {
        val completions = languageServer.textDocumentService.completion(completionParams(file, 2, 10)).get().right!!
        val labels = completions.items.map { it.label }

        assertThat(labels, hasItem("SomeConstructor"))
    }
}

class MiddleOfFunctionTest: SingleFileTestFixture("completions", "MiddleOfFunction.kt") {
    @Test fun `complete in the middle of a function`() {
        val completions = languageServer.textDocumentService.completion(completionParams(file, 3, 11)).get().right!!
        val labels = completions.items.map { it.label }

        assertThat(labels, hasItem("subSequence"))
    }
}

class BackquotedFunctionTest: SingleFileTestFixture("completions", "BackquotedFunction.kt") {
    @Test fun `complete with backquotes`() {
        val completions = languageServer.textDocumentService.completion(completionParams(file, 2, 7)).get().right!!
        val insertText = completions.items.map { it.insertText }

        assertThat(insertText, hasItem("`fun that needs backquotes`()"))
    }
}

class CompleteStaticsTest: SingleFileTestFixture("completions", "Statics.kt") {
    @Test fun `java static method`() {
        val completions = languageServer.textDocumentService.completion(completionParams(file, 4, 16)).get().right!!
        val labels = completions.items.map { it.label }

        assertThat(labels, hasItem("isNull"))
    }

    @Test fun `java static method reference`() {
        val completions = languageServer.textDocumentService.completion(completionParams(file, 7, 17)).get().right!!
        val labels = completions.items.map { it.label }

        assertThat(labels, hasItem("isNull"))
    }

    @Test fun `object method`() {
        val completions = languageServer.textDocumentService.completion(completionParams(file, 5, 17)).get().right!!
        val labels = completions.items.map { it.label }

        assertThat(labels, hasItem("objectFun"))
    }

    @Test fun `companion object method`() {
        val completions = languageServer.textDocumentService.completion(completionParams(file, 6, 16)).get().right!!
        val labels = completions.items.map { it.label }

        assertThat(labels, hasItem("companionFun"))
    }
}

class VisibilityTest: SingleFileTestFixture("completions", "Visibility.kt") {
    @Test fun `find tricky visibility members`() {
        val completions = languageServer.textDocumentService.completion(completionParams(file, 3, 10)).get().right!!
        val labels = completions.items.map { it.label }

        assertThat(labels, hasItems("privateThisFun", "protectedThisFun", "publicThisFun", "privateThisCompanionFun", "protectedThisCompanionFun", "publicThisCompanionFun", "privateTopLevelFun"))
        assertThat(labels, hasItems("protectedSuperFun", "publicSuperFun", "protectedSuperCompanionFun", "publicSuperCompanionFun"))
        assertThat(labels, not(hasItems("privateSuperFun", "privateSuperCompanionFun", "publicExtensionFun")))
    }
}

class ImportsTest: SingleFileTestFixture("completions", "Imports.kt") {
    @Test fun `complete import from java-nio-path-P`() {
        val completions = languageServer.textDocumentService.completion(completionParams(file, 1, 23)).get().right!!
        val labels = completions.items.map { it.label }

        assertThat(labels, hasItems("Path", "Paths"))
    }

    @Test fun `complete import from java-nio-`() {
        val completions = languageServer.textDocumentService.completion(completionParams(file, 3, 25)).get().right!!
        val labels = completions.items.map { it.label }

        assertThat(labels, hasItems("MethodHandle"))
    }
}

class DoubleDotTest: SingleFileTestFixture("completions", "DoubleDot.kt") {
    @Test fun `complete nested select`() {
        val completions = languageServer.textDocumentService.completion(completionParams(file, 2, 15)).get().right!!
        val labels = completions.items.map { it.label }

        assertThat(labels, not(hasItem("chars")))
        assertThat(labels, hasItem("anyMatch"))
    }
}

class QuestionDotTest: SingleFileTestFixture("completions", "QuestionDot.kt") {
    @Test fun `complete null-safe select`() {
        val completions = languageServer.textDocumentService.completion(completionParams(file, 2, 8)).get().right!!
        val labels = completions.items.map { it.label }

        assertThat(labels, hasItem("chars"))
    }
}

class OuterDotInnerTest: SingleFileTestFixture("completions", "OuterDotInner.kt") {
    @Test fun `complete as OuterClass-InnerClass`() {
        val completions = languageServer.textDocumentService.completion(completionParams(file, 2, 24)).get().right!!
        val labels = completions.items.map { it.label }

        assertThat(labels, hasItem("InnerClass"))
    }

    @Test fun `complete static OuterClass-InnerClass`() {
        val completions = languageServer.textDocumentService.completion(completionParams(file, 6, 19)).get().right!!
        val labels = completions.items.map { it.label }

        assertThat(labels, hasItem("InnerClass"))
    }
}

class EditCallTest: SingleFileTestFixture("completions", "EditCall.kt") {
    @Test fun `edit existing function`() {
        val completions = languageServer.textDocumentService.completion(completionParams(file, 2, 11)).get().right!!
        val labels = completions.items.map { it.label }

        assertThat(labels, hasItem("println"))
        assertThat(completions.items.filter { it.label == "println" }.firstOrNull(), hasProperty("insertText", equalTo("println")))
    }
}