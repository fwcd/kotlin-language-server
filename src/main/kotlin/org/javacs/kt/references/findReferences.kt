package org.javacs.kt.references

import org.javacs.kt.SourcePath
import org.javacs.kt.docs.preOrderTraversal
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.resolve.BindingContext
import java.nio.file.Path

fun findReferences(file: Path, offset: Int, sources: SourcePath): Collection<KtNameReferenceExpression> {
    val recover = sources.recover(file, offset) ?: return emptyList()
    val oldDeclaration = recover.exprAt(0)?.parent as? KtNamedDeclaration ?: return emptyList()
    val oldDescriptor = recover.getDeclaration(oldDeclaration) ?: return emptyList()
    val maybes = sources.allSources().filter { mightReference(oldDescriptor, it) }
    val newContext = sources.compileFiles(maybes)
    val newDescriptor = newContext.get(BindingContext.DECLARATION_TO_DESCRIPTOR, oldDeclaration)!!

    return maybes.flatMap { doFindReferences(newDescriptor, it, newContext) }
}

private fun doFindReferences(target: DeclarationDescriptor, file: KtFile, context: BindingContext): Collection<KtNameReferenceExpression> =
        file.preOrderTraversal()
                .filterIsInstance<KtNameReferenceExpression>()
                .filter { isReference(it, target, context) }
                .toList()

private fun isReference(from: KtNameReferenceExpression, to: DeclarationDescriptor, context: BindingContext): Boolean {
    val fromDeclaration = context.get(BindingContext.REFERENCE_TARGET, from)

    return to == fromDeclaration
}

private fun mightReference(target: DeclarationDescriptor, file: KtFile): Boolean =
        file.preOrderTraversal()
                .filterIsInstance<KtNameReferenceExpression>()
                .any { it.getReferencedNameAsName() == target.name }