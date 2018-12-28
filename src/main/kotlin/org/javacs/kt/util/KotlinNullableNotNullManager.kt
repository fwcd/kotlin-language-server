/*
 * Source: https://github.com/JetBrains/kotlin-netbeans/blob/master/src/main/java/org/jetbrains/kotlin/model/KotlinNullableNotNullManager.kt
 * Licensed under http://www.apache.org/licenses/LICENSE-2.0
 */
package org.javacs.kt.util

import com.intellij.openapi.project.Project
import com.intellij.codeInsight.NullableNotNullManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiModifierListOwner

class KotlinNullableNotNullManager(project: Project) : NullableNotNullManager(project) {
	init {
		setNotNulls("NotNull")
		setNullables("Nullable")
	}

	override fun hasHardcodedContracts(element: PsiElement) = false

	override fun isNotNull(owner: PsiModifierListOwner, checkBases: Boolean): Boolean {
		val notNullAnnotations = notNulls.toSet()
		return owner.modifierList?.annotations?.any { annotation ->
			annotation.qualifiedName in notNullAnnotations
		} ?: false
	}

	override fun isNullable(owner: PsiModifierListOwner, checkBases: Boolean) = !isNotNull(owner, checkBases)

	override fun getPredefinedNotNulls() = emptyList<String>()
}
