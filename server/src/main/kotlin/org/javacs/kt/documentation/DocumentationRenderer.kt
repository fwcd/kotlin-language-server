package org.javacs.kt.documentation

import org.javacs.kt.util.findParent
import org.jetbrains.kotlin.kdoc.parser.KDocKnownTag
import org.jetbrains.kotlin.kdoc.psi.impl.KDocTag
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameterList
import org.jetbrains.kotlin.psi.psiUtil.getChildOfType
import org.jetbrains.kotlin.psi.psiUtil.getChildrenOfType

object DocumentationRenderer {

    fun render(function: KtNamedFunction): String = function.render()

    @JvmName("receiverRender")
    fun KtNamedFunction.render(): String {
        val ktClass = findParent<KtClassOrObject>()?.fqName?.asString()
            ?: findParent<KtFile>()?.packageFqName
            ?: ""

        val mainComment = docComment
            ?.getDefaultSection()
            ?.getContent()
            ?.trim()

        val others = docComment
            ?.getDefaultSection()
            ?.getChildrenOfType<KDocTag>()
            ?.groupBy { it.knownTag }
            ?: emptyMap()

        val typeParameters = this.typeParameterList?.text?.let { "$it " } ?: ""

        val params = others[KDocKnownTag.PARAM]
            ?.joinToString(separator = "\n", prefix = "\nParams:\n") { "`${it.getSubjectName()}` ${it.getContent()}" }
            ?: ""

        val samples = others[KDocKnownTag.SAMPLE]
        val receiver = others[KDocKnownTag.RECEIVER]?.firstOrNull()
        val returns = others[KDocKnownTag.RETURN]?.firstOrNull()
            ?.let { "\nReturns: ${it.getContent()}" } ?: ""

        val parameters = getChildOfType<KtParameterList>()
            ?.children
            ?.joinToString(",\n", "(\n", "\n)") { "\t${it.text}" }
            ?: "()"

        val returnType = typeReference?.text
            ?.let { ": $it" }
            ?: ""

        return """$ktClass
$mainComment
```kotlin
fun $typeParameters$name $parameters$returnType
```$params$returns"""
    }
}
