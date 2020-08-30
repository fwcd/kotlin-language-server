package org.javacs.kt

import org.hamcrest.Matchers.*
import org.junit.Assert.assertThat
import org.junit.Test

class ReferencesTest : SingleFileTestFixture("references", "ReferenceTo.kt") {
    @Test fun `find referencs to foo`() {
        val request = referenceParams(file, 2, 11)
        val references = languageServer.textDocumentService.references(request).get()
        val referenceStrs = references?.map { it.toString() }

        assertThat("Finds references within a file", referenceStrs, hasItem(containsString("ReferenceTo.kt")))
        assertThat("Finds references across files", referenceStrs, hasItem(containsString("ReferenceFrom.kt")))
    }
}

class ReferenceCollectionishTest : SingleFileTestFixture("references", "ReferenceCollectionish.kt") {
    @Test fun `find references to iterator`() {
        val request = referenceParams(file, 2, 21)
        val references = languageServer.textDocumentService.references(request).get()
        val referenceStrs = references?.map { it.toString() }
        val uris = references?.map { it.uri }

        assertThat(uris, hasItem(containsString(file)))
        assertThat("Finds for-loop reference", referenceStrs, hasItem(containsString("line = 8")))
        assertThat("Finds iterator() reference", referenceStrs, hasItem(containsString("line = 9")))
    }

    @Test fun `find references to contains`() {
        val request = referenceParams(file, 3, 21)
        val references = languageServer.textDocumentService.references(request).get()
        val referenceStrs = references?.map { it.toString() }
        val uris = references?.map { it.uri }

        assertThat(uris, hasItem(containsString(file)))
        assertThat("Finds reference using in", referenceStrs, hasItem(containsString("line = 10")))
        assertThat("Finds contains() reference", referenceStrs, hasItem(containsString("line = 11")))
    }

    @Test fun `find references to rangeTo`() {
        val request = referenceParams(file, 4, 21)
        val references = languageServer.textDocumentService.references(request).get()
        val uris = references?.map { it.uri }

        assertThat(uris, hasItem(containsString(file)))
    }
}

class ReferenceComponentsTest : SingleFileTestFixture("references", "ReferenceComponents.kt") {
    @Test fun `find references to component1`() {
        val request = referenceParams(file, 2, 21)
        val references = languageServer.textDocumentService.references(request).get()
        val referenceStrs = references?.map { it.toString() }
        val uris = references?.map { it.uri }

        assertThat(uris, hasItem(containsString(file)))
        assertThat("Finds destructuring reference", referenceStrs, hasItem(containsString("line = 6")))
        assertThat("Finds component1() reference", referenceStrs, hasItem(containsString("line = 7")))
    }
}

class ReferenceConstructorTest : SingleFileTestFixture("references", "ReferenceConstructor.kt") {
    @Test fun `find reference to main constructor`() {
        val request = referenceParams(file, 1, 24)
        val references = languageServer.textDocumentService.references(request).get()
        val referenceStrs = references?.map { it.toString() }

        assertThat("Finds reference to the main constructor", referenceStrs, hasItem(containsString("ReferenceConstructor.kt")))
    }

    @Test fun `find reference to secondary constructor`() {
        val request = referenceParams(file, 2, 10)
        val references = languageServer.textDocumentService.references(request).get()
        val referenceStrs = references?.map { it.toString() }

        assertThat("Finds reference to a secondary constructor", referenceStrs, hasItem(containsString("ReferenceConstructor.kt")))
    }
}

class ReferenceGetSetValueTest : SingleFileTestFixture("references", "ReferenceGetSetValue.kt") {
    @Test fun `find references to getValue`() {
        val request = referenceParams(file, 4, 21)
        val references = languageServer.textDocumentService.references(request).get()
        val referenceStrs = references?.map { it.toString() }
        val uris = references?.map { it.uri }

        assertThat(uris, hasItem(containsString(file)))
        assertThat(referenceStrs, hasItem(containsString("line = 8")))
    }

    @Test fun `find references to setValue`() {
        val request = referenceParams(file, 5, 21)
        val references = languageServer.textDocumentService.references(request).get()
        val referenceStrs = references?.map { it.toString() }
        val uris = references?.map { it.uri }

        assertThat(uris, hasItem(containsString(file)))
        assertThat(referenceStrs, hasItem(containsString("line = 8")))
    }
}

class ReferenceGetterSetterTest : SingleFileTestFixture("references", "ReferenceGetterSetter.kt") {
    @Test fun `find references to get`() {
        val request = referenceParams(file, 2, 19)
        val references = languageServer.textDocumentService.references(request).get()
        val referenceStrs = references?.map { it.toString() }
        val uris = references?.map { it.uri }

        assertThat(uris, hasItem(containsString(file)))
        assertThat(referenceStrs, hasItem(containsString("line = 7")))
    }

    @Test fun `find references to set`() {
        val request = referenceParams(file, 3, 19)
        val references = languageServer.textDocumentService.references(request).get()
        val referenceStrs = references?.map { it.toString() }
        val uris = references?.map { it.uri }

        assertThat(uris, hasItem(containsString(file)))
        assertThat(referenceStrs, hasItem(containsString("line = 8")))
    }
}

class ReferenceInvokeTest : SingleFileTestFixture("references", "ReferenceInvoke.kt") {
    @Test fun `find references to invoke`() {
        val request = referenceParams(file, 2, 21)
        val references = languageServer.textDocumentService.references(request).get()
        val referenceStrs = references?.map { it.toString() }
        val uris = references?.map { it.uri }

        assertThat(uris, hasItem(containsString(file)))
        assertThat(referenceStrs, hasItem(containsString("line = 6")))
    }
}

class ReferenceOperatorTest : SingleFileTestFixture("references", "ReferenceOperator.kt") {
    @Test fun `find references to equals`() {
        val request = referenceParams(file, 2, 30)
        val references = languageServer.textDocumentService.references(request).get()
        val uris = references?.map { it.uri }

        assertThat(uris, hasItem(containsString(file)))
    }

    @Test fun `find references to compareTo`() {
        val request = referenceParams(file, 3, 22)
        val references = languageServer.textDocumentService.references(request).get()
        val uris = references?.map { it.uri }

        assertThat(uris, hasItem(containsString(file)))
    }

    @Test fun `find references to inc`() {
        val request = referenceParams(file, 4, 20)
        val references = languageServer.textDocumentService.references(request).get()
        val uris = references?.map { it.uri }

        assertThat(uris, hasItem(containsString(file)))
    }

    @Test fun `find references to dec`() {
        val request = referenceParams(file, 5, 20)
        val references = languageServer.textDocumentService.references(request).get()
        val uris = references?.map { it.uri }

        assertThat(uris, hasItem(containsString(file)))
    }

    @Test fun `find references to plus`() {
        val request = referenceParams(file, 6, 20)
        val references = languageServer.textDocumentService.references(request).get()
        val uris = references?.map { it.uri }

        assertThat(uris, hasItem(containsString(file)))
    }

    @Test fun `find references to minus`() {
        val request = referenceParams(file, 7, 20)
        val references = languageServer.textDocumentService.references(request).get()
        val uris = references?.map { it.uri }

        assertThat(uris, hasItem(containsString(file)))
    }

    @Test fun `find references to not`() {
        val request = referenceParams(file, 8, 20)
        val references = languageServer.textDocumentService.references(request).get()
        val uris = references?.map { it.uri }

        assertThat(uris, hasItem(containsString(file)))
    }

    @Test fun `find references to equals without operator keyword`() {
        val request = referenceParams(file, 12, 21)
        val references = languageServer.textDocumentService.references(request).get()
        val uris = references?.map { it.uri }

        assertThat(uris, hasItem(containsString(file)))
    }
}

class ReferenceOperatorUsingNameTest : SingleFileTestFixture("references", "ReferenceOperatorUsingName.kt") {
    @Test fun `find references to equals`() {
        val request = referenceParams(file, 2, 30)
        val references = languageServer.textDocumentService.references(request).get()
        val uris = references?.map { it.uri }

        assertThat(uris, hasItem(containsString(file)))
    }

    @Test fun `find references to compareTo`() {
        val request = referenceParams(file, 3, 22)
        val references = languageServer.textDocumentService.references(request).get()
        val uris = references?.map { it.uri }

        assertThat(uris, hasItem(containsString(file)))
    }

    @Test fun `find references to inc`() {
        val request = referenceParams(file, 4, 20)
        val references = languageServer.textDocumentService.references(request).get()
        val uris = references?.map { it.uri }

        assertThat(uris, hasItem(containsString(file)))
    }
}
