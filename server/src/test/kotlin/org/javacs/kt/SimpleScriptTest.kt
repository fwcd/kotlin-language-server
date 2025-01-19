package org.javacs.kt

import org.junit.*
import org.junit.Assert.*
import kotlin.script.experimental.api.*
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvm.dependenciesFromCurrentContext
import kotlin.script.experimental.annotations.KotlinScript
import kotlin.script.experimental.jvmhost.BasicJvmScriptingHost
import kotlin.script.experimental.jvmhost.createJvmCompilationConfigurationFromTemplate

@KotlinScript(fileExtension = "simpleScript.kts")
abstract class SimpleScript

private data class CodeSnippet(
    override val text: String,
    override val name: String? = null,
    override val locationId: String? = null
) : SourceCode

private class SnippetRunner {
    val config = createJvmCompilationConfigurationFromTemplate<SimpleScript> {
        jvm {
            dependenciesFromCurrentContext(wholeClasspath = true)
        }
    }

    fun eval(code: SourceCode): ResultWithDiagnostics<EvaluationResult> {
        return BasicJvmScriptingHost().eval(code, config, null)
    }
}

class SimpleScriptTest {
    // TODO: Test a script using the language server instead
    //       of just experimenting with the API
    @Test
    fun basicScript() {
        val runner = SnippetRunner()
        var result = runner.eval(CodeSnippet("val x = 1"))
        assertNotError(result)

        result = runner.eval(CodeSnippet("234 + 32"))
        assertNotError(result)

        val resultValue = (result as ResultWithDiagnostics.Success).value.returnValue as ResultValue.Value
        // TODO:
        // assertThat(resultValue.type, equalTo("Int"))
    }

    private fun assertNotError(result: ResultWithDiagnostics<EvaluationResult>) {
        if (result is ResultWithDiagnostics.Failure) {
            fail("Error(s) while running REPL: ${result.reports}")
        }
    }
}

