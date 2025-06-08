package org.javacs.kt.j2k

import com.intellij.lang.java.JavaLanguage
import org.javacs.kt.LOG
import org.javacs.kt.compiler.Compiler
import org.javacs.kt.compiler.CompilationType

fun convertJavaToKotlin(javaCode: String, compiler: Compiler): String {
    val psiFactory = compiler.psiFileFactoryFor(CompilationType.DEFAULT)
    val javaAST = psiFactory.createFileFromText("snippet.java", JavaLanguage.INSTANCE, javaCode)
    LOG.info("Parsed {} to {}", javaCode, javaAST)

	return JavaElementConverter().also(javaAST::accept).translatedKotlinCode ?: run {
        LOG.warn("Could not translate code")
        ""
    }
}
