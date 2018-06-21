package org.javacs.kt.javaToKotlin

import java.util.Arrays
import com.intellij.psi.*
import com.intellij.lang.java.JavaLanguage
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.j2k.JavaToKotlinConverter
import org.jetbrains.kotlin.j2k.JavaToKotlinTranslator
import org.jetbrains.kotlin.j2k.ConverterSettings
import org.jetbrains.kotlin.j2k.EmptyJavaToKotlinServices
import org.javacs.kt.LOG

/**
 * Source: https://github.com/JetBrains/kotlin-web-demo/blob/master/versions/1.2.50/src/main/java/org/jetbrains/webdemo/kotlin/impl/converter/WebDemoJavaToKotlinConverter.kt
 * Licensed under Apache License, Version 2.0
 */

/** Converts a Java code snippet to Kotlin */
fun convertJavaToKotlin(environment: KotlinCoreEnvironment, javaCode: String): String {
	LOG.info("Converting to Kotlin: $javaCode")

	val project = environment.project
	val converter = JavaToKotlinConverter(
			project,
			ConverterSettings.defaultSettings,
			EmptyJavaToKotlinServices
	)
	val instance = PsiElementFactory.SERVICE.getInstance(project)
	val javaFile = PsiFileFactory.getInstance(project)
			.createFileFromText("snippet.java", JavaLanguage.INSTANCE, javaCode)

	var inputElements: List<PsiElement>? = if (javaFile.children.any { it is PsiClass}) listOf<PsiElement>(javaFile) else null

	if (inputElements == null) {
		val psiClass = instance.createClassFromText(javaCode, javaFile)
		var errorsFound = false
		for (element in psiClass.children) {
			if (element is PsiErrorElement) {
				errorsFound = true
			}
		}

		if (!errorsFound) {
			inputElements = Arrays.asList(*psiClass.children)
		}
	}

	if (inputElements == null) {
		val codeBlock = instance.createCodeBlockFromText(javaCode, javaFile)
		val childrenWithoutBraces = Arrays.copyOfRange(codeBlock.children, 1, codeBlock.children.size - 1)
		inputElements = Arrays.asList(*childrenWithoutBraces)
	}

	val resultFormConverter = converter.elementsToKotlin(inputElements!!).results
	var textResult = ""
	resultFormConverter
			.asSequence()
			.filterNotNull()
			.forEach { textResult += it.text + "\n" }

	return JavaToKotlinTranslator.prettify(textResult)
}
