package org.javacs.kt.j2k

import org.javacs.kt.LOG
import com.intellij.psi.*

object JavaTypeConverter : PsiTypeVisitor<String>() {
    override fun visitType(type: PsiType): String {
        return type.presentableText
    }

    override fun visitPrimitiveType(primitiveType: PsiPrimitiveType): String = when (primitiveType.canonicalText) {
        "void" -> "Unit"
        else -> primitiveType.canonicalText.capitalize()
    }

    override fun visitArrayType(arrayType: PsiArrayType): String = when (try {
        arrayType.componentType.canonicalText
    } catch (e: IllegalStateException) {
        LOG.warn("Error while fetching text representation of array type: {}", e)
        "?"
    }) {
        "byte" -> "ByteArray"
        "short" -> "ShortArray"
        "int" -> "IntArray"
        "long" -> "LongArray"
        "char" -> "CharArray"
        "boolean" -> "BooleanArray"
        "float" -> "FloatArray"
        "double" -> "DoubleArray"
        else -> "Array<${arrayType.componentType.accept(this)}>"
    }

    override fun visitClassType(classType: PsiClassType): String {
        val translatedTypeArgs = classType.parameters.asSequence()
            .map { it.accept(this) }
            .joinToString(separator = ", ")
            .let { if (it.isNotEmpty()) "<$it>" else "" }
        return "${platformType(classType.className)}$translatedTypeArgs"
    }

    override fun visitCapturedWildcardType(capturedWildcardType: PsiCapturedWildcardType): String {
        return super.visitCapturedWildcardType(capturedWildcardType) ?: "?"
    }

    override fun visitWildcardType(wildcardType: PsiWildcardType): String =
        if (wildcardType.isSuper()) {
            "in ${wildcardType.bound?.accept(this)}"
        } else if (wildcardType.isExtends()) {
            "out ${wildcardType.bound?.accept(this)}"
        } else {
            super.visitWildcardType(wildcardType) ?: "?"
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
