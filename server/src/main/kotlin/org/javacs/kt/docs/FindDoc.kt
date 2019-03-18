package org.javacs.kt.docs

import org.javacs.kt.util.preOrderTraversal
import org.jetbrains.kotlin.descriptors.DeclarationDescriptorWithSource
import org.jetbrains.kotlin.kdoc.parser.KDocKnownTag
import org.jetbrains.kotlin.kdoc.psi.impl.KDocTag
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtPrimaryConstructor
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.kotlin.resolve.DescriptorToSourceUtils

fun findDoc(declaration: DeclarationDescriptorWithSource): KDocTag? {
    val source = DescriptorToSourceUtils.descriptorToDeclaration(declaration)?.navigationElement

    return when (source) {
        is KtParameter -> {
            var container = source.parents.filterIsInstance<KtDeclaration>().firstOrNull() ?: return null
            if (container is KtPrimaryConstructor)
                container = container.parents.filterIsInstance<KtDeclaration>().firstOrNull() ?: return null
            val doc = container.docComment ?: return null
            val descendants = doc.preOrderTraversal()
            val tags = descendants.filterIsInstance<KDocTag>()
            val params = tags.filter { it.knownTag == KDocKnownTag.PARAM }
            val matchName = params.filter { it.getSubjectName() == declaration.name.toString() }

            return matchName.firstOrNull()
        }
        is KtPrimaryConstructor -> {
            val container = source.parents.filterIsInstance<KtDeclaration>().firstOrNull() ?: return null
            val doc = container.docComment ?: return null
            doc.findSectionByTag(KDocKnownTag.CONSTRUCTOR) ?: doc.getDefaultSection()
        }
        is KtDeclaration -> {
            val doc = source.docComment ?: return null
            doc.getDefaultSection()
        }
        else -> null
    }
}