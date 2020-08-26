/*
 * Source: https://github.com/JetBrains/kotlin-eclipse/blob/d66b9915a6803ac7adb955af6a8dae76f28ed7e5/kotlin-eclipse-core/src/org/jetbrains/kotlin/core/model/KotlinNullableNotNullManager.kt
 * Licensed under http://www.apache.org/licenses/LICENSE-2.0
 */
package org.javacs.kt.util

import org.jetbrains.kotlin.com.intellij.codeInsight.NullabilityAnnotationInfo
import org.jetbrains.kotlin.com.intellij.codeInsight.NullableNotNullManager
import org.jetbrains.kotlin.com.intellij.openapi.project.Project
import org.jetbrains.kotlin.com.intellij.psi.PsiAnnotation
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.com.intellij.psi.PsiModifierListOwner

class KotlinNullableNotNullManager(project: Project) : NullableNotNullManager(project) {
    private val nullablesList = mutableListOf<String>()
    private val notNullsList = mutableListOf<String>()

    override fun getNullables(): List<String> = nullablesList

    override fun setInstrumentedNotNulls(names: MutableList<String>) {}

    override fun getInstrumentedNotNulls(): List<String> = emptyList()

    override fun setNullables(vararg annotations: String) {
        nullablesList.clear()
        nullablesList.addAll(annotations)
    }

    override fun getDefaultNotNull(): String = "NotNull"

    override fun getNotNulls(): List<String> = notNullsList

    override fun getDefaultNullable(): String = "Nullable"

    override fun setDefaultNotNull(defaultNotNull: String) {
    }

    override fun setNotNulls(vararg annotations: String) {
        notNullsList.clear()
        notNullsList.addAll(annotations)
    }

    //    For now we get unresolved psi elements and as a result annotations qualified names are short
    init {
        setNotNulls("NotNull")
        setNullables("Nullable")
    }

    override fun setDefaultNullable(defaultNullable: String) {
    }

    override fun hasHardcodedContracts(element: PsiElement): Boolean = false

    override fun isNotNull(owner: PsiModifierListOwner, checkBases: Boolean): Boolean {
        val notNullAnnotations = notNulls.toSet()
        return owner.modifierList?.annotations?.any { annotation ->
            annotation.qualifiedName in notNullAnnotations
        } ?: false
    }

    override fun isNullable(owner: PsiModifierListOwner, checkBases: Boolean) = !isNotNull(owner, checkBases)
}
