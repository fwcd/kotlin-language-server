package org.javacs.kt.docs

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.descriptors.DeclarationDescriptorWithSource
import org.jetbrains.kotlin.kdoc.parser.KDocKnownTag
import org.jetbrains.kotlin.kdoc.psi.impl.KDocTag
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtPrimaryConstructor
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.kotlin.resolve.DescriptorToSourceUtils
import kotlin.coroutines.experimental.buildSequence

fun findDoc(desc: DeclarationDescriptorWithSource): KDocTag? {
    val source = DescriptorToSourceUtils.descriptorToDeclaration(desc)?.navigationElement

    return when (source) {
        is KtParameter -> {
            var container = source.parents.filterIsInstance<KtDeclaration>().firstOrNull() ?: return null
            if (container is KtPrimaryConstructor)
                container = container.parents.filterIsInstance<KtDeclaration>().firstOrNull() ?: return null
            val doc = container.docComment ?: return null
            val descendants = doc.preOrderTraversal()
            val tags = descendants.filterIsInstance<KDocTag>()
            val params = tags.filter { it.knownTag == KDocKnownTag.PARAM }
            val matchName = params.filter { it.getSubjectName() == desc.name.toString() }

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

fun PsiElement.preOrderTraversal(): Sequence<PsiElement> {
    val root = this

    return buildSequence {
        yield(root)

        for (child in root.children) {
            yieldAll(child.preOrderTraversal())
        }
    }
}