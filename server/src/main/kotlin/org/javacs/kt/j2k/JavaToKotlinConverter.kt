package org.javacs.kt.j2k

import com.intellij.lang.java.JavaLanguage
import com.intellij.psi.PsiFileFactory
import com.intellij.openapi.project.Project
// import org.jetbrains.kotlin.j2k.JavaToKotlinTranslator
import org.javacs.kt.LOG
import org.javacs.kt.compiler.Compiler
import org.javacs.kt.compiler.CompilationKind
import org.javacs.kt.util.nonNull

fun convertJavaToKotlin(javaCode: String, compiler: Compiler): String {
    val psiFactory = compiler.psiFileFactoryFor(CompilationKind.DEFAULT)
    val javaAST = psiFactory.createFileFromText("snippet.java", JavaLanguage.INSTANCE, javaCode)
    LOG.info("Parsed {} to {}", javaCode, javaAST)

	return JavaElementConverter().also(javaAST::accept).translatedKotlinCode ?: run {
        LOG.warn("Could not translate code")
        ""
    }
}
