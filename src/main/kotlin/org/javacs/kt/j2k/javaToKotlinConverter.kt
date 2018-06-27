package org.javacs.kt.j2k

import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.j2k.JavaToKotlinTranslator
import org.javacs.kt.LOG

fun convertJavaToKotlin(environment: KotlinCoreEnvironment, javaCode: String): String {
	LOG.info("Converting to Kotlin: $javaCode")
	return JavaToKotlinTranslator.generateKotlinCode(javaCode, environment.project)
}
