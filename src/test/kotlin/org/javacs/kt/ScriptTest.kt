package org.javacs.kt

import org.junit.Test

class ScriptTest: LanguageServerTestFixture("script") {
    @Test fun `open script`() {
        open("ExampleScript.kts")
    }
}