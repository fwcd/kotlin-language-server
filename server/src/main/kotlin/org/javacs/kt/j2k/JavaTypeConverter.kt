package org.javacs.kt.j2k

import org.jetbrains.kotlin.com.intellij.psi.*

object JavaTypeConverter : PsiTypeVisitor<String>() {
    override fun visitType(type: PsiType): String {
        return type.presentableText
    }

    override fun visitPrimitiveType(primitiveType: PsiPrimitiveType): String = when (primitiveType.canonicalText) {
        "void" -> "Unit"
        else -> primitiveType.canonicalText.capitalize()
    }

    override fun visitArrayType(arrayType: PsiArrayType): String {
        return "Array<${arrayType.componentType.accept(this)}>"
    }

    override fun visitClassType(classType: PsiClassType): String = when (classType.className) {
        "Integer" -> "Int"
        "Character" -> "Char"
        else -> super.visitClassType(classType) ?: classType.className
    }

    override fun visitCapturedWildcardType(capturedWildcardType: PsiCapturedWildcardType): String {
        return super.visitCapturedWildcardType(capturedWildcardType) ?: "?"
    }

    override fun visitWildcardType(wildcardType: PsiWildcardType): String {
        return super.visitWildcardType(wildcardType) ?: "?"
    }

    override fun visitEllipsisType(ellipsisType: PsiEllipsisType): String {
        return super.visitEllipsisType(ellipsisType) ?: "?"
    }

    override fun visitDisjunctionType(disjunctionType: PsiDisjunctionType): String {
        return super.visitDisjunctionType(disjunctionType) ?: "?"
    }

    override fun visitIntersectionType(intersectionType: PsiIntersectionType): String {
        return super.visitIntersectionType(intersectionType) ?: "?"
    }

    override fun visitDiamondType(diamondType: PsiDiamondType): String {
        return super.visitDiamondType(diamondType) ?: "?"
    }

    override fun visitLambdaExpressionType(lambdaExpressionType: PsiLambdaExpressionType): String {
        return super.visitLambdaExpressionType(lambdaExpressionType) ?: "?"
    }

    override fun visitMethodReferenceType(methodReferenceType: PsiMethodReferenceType): String {
        return super.visitMethodReferenceType(methodReferenceType)
    }
}
