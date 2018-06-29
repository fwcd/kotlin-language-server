package org.javacs.kt.j2k

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.j2k.JavaToKotlinTranslator
import org.javacs.kt.LOG
import org.javacs.kt.Compiler
import org.javacs.kt.util.nonNull

fun convertJavaToKotlin(javaCode: String, compiler: Compiler): String {
	val project = compiler.environment.project
	LOG.info("Converting to Kotlin: $javaCode with project ${project}")

	return JavaToKotlinTranslator.generateKotlinCode(
		nonNull(javaCode, "No Java code has been provided to the J2K-converter"),
		nonNull(project, "No project is present in the KotlinCoreEnvironment")
	)
}
