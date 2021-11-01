package org.javacs.kt.semantictokens

import org.eclipse.lsp4j.SemanticTokenTypes
import org.eclipse.lsp4j.SemanticTokenModifiers
import org.eclipse.lsp4j.SemanticTokensLegend
import org.eclipse.lsp4j.Range
import org.javacs.kt.CompiledFile
import org.javacs.kt.position.range
import org.javacs.kt.position.offset
import org.javacs.kt.util.preOrderTraversal
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.ConstructorDescriptor
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.descriptors.VariableDescriptor
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtModifierListOwner
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtVariableDeclaration
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtEnumEntry
import org.jetbrains.kotlin.psi.KtStringTemplateEntry
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtSimpleNameStringTemplateEntry
import org.jetbrains.kotlin.psi.KtBlockStringTemplateEntry
import org.jetbrains.kotlin.psi.KtEscapeStringTemplateEntry
import org.jetbrains.kotlin.resolve.BindingContext
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiType
import com.intellij.openapi.util.TextRange

enum class SemanticTokenType(val typeName: String) {
    KEYWORD(SemanticTokenTypes.Keyword),
    VARIABLE(SemanticTokenTypes.Variable),
    FUNCTION(SemanticTokenTypes.Function),
    PROPERTY(SemanticTokenTypes.Property),
    PARAMETER(SemanticTokenTypes.Parameter),
    ENUM_MEMBER(SemanticTokenTypes.EnumMember),
    CLASS(SemanticTokenTypes.Class),
    INTERFACE(SemanticTokenTypes.Interface),
    ENUM(SemanticTokenTypes.Enum),
    TYPE(SemanticTokenTypes.Type),
    STRING(SemanticTokenTypes.String),
    NUMBER(SemanticTokenTypes.Number),
    // Since LSP does not provide a token type for string interpolation
    // entries, we use Variable as a fallback here for now
    INTERPOLATION_ENTRY(SemanticTokenTypes.Variable)
}

enum class SemanticTokenModifier(val modifierName: String) {
    DECLARATION(SemanticTokenModifiers.Declaration),
    DEFINITION(SemanticTokenModifiers.Definition),
    ABSTRACT(SemanticTokenModifiers.Abstract),
    READONLY(SemanticTokenModifiers.Readonly)
}

val semanticTokensLegend = SemanticTokensLegend(
    SemanticTokenType.values().map { it.typeName },
    SemanticTokenModifier.values().map { it.modifierName }
)

data class SemanticToken(val range: Range, val type: SemanticTokenType, val modifiers: Set<SemanticTokenModifier> = setOf())

/**
 * Computes LSP-encoded semantic tokens for the given range in the
 * document. No range means the entire document.
 */
fun encodedSemanticTokens(file: CompiledFile, range: Range? = null): List<Int> =
    encodeTokens(semanticTokens(file, range))

/**
 * Computes semantic tokens for the given range in the document.
 * No range means the entire document.
 */
fun semanticTokens(file: CompiledFile, range: Range? = null): Sequence<SemanticToken> =
    elementTokens(file.parse, file.compile, range)

fun encodeTokens(tokens: Sequence<SemanticToken>): List<Int> {
    val encoded = mutableListOf<Int>()
    var last: SemanticToken? = null

    for (token in tokens) {
        // Tokens must be on a single line
        if (token.range.start.line == token.range.end.line) {
            val length = token.range.end.character - token.range.start.character
            val deltaLine = token.range.start.line - (last?.range?.start?.line ?: 0)
            val deltaStart = token.range.start.character - (last?.takeIf { deltaLine == 0 }?.range?.start?.character ?: 0)

            encoded.add(deltaLine)
            encoded.add(deltaStart)
            encoded.add(length)
            encoded.add(encodeType(token.type))
            encoded.add(encodeModifiers(token.modifiers))

            last = token
        }
    }

    return encoded
}

private fun encodeType(type: SemanticTokenType): Int = type.ordinal

private fun encodeModifiers(modifiers: Set<SemanticTokenModifier>): Int = modifiers
    .map { 1 shl it.ordinal }
    .fold(0, Int::or)

private fun elementTokens(element: PsiElement, bindingContext: BindingContext, range: Range? = null): Sequence<SemanticToken> {
    val file = element.containingFile
    val textRange = range?.let { TextRange(offset(file.text, it.start), offset(file.text, it.end)) }
    return element
        // TODO: Ideally we would like to cut-off subtrees outside our range, but this doesn't quite seem to work
        // .preOrderTraversal { elem -> textRange?.let { it.contains(elem.textRange) } ?: true }
        .preOrderTraversal()
        .filter { elem -> textRange?.let { it.contains(elem.textRange) } ?: true }
        .mapNotNull { elementToken(it, bindingContext) }
}

private fun elementToken(element: PsiElement, bindingContext: BindingContext): SemanticToken? {
    val file = element.containingFile
    val elementRange = range(file.text, element.textRange)

    return when (element) {
        // References (variables, types, functions, ...)

        is KtNameReferenceExpression -> {
            val target = bindingContext[BindingContext.REFERENCE_TARGET, element]
            val tokenType = when (target) {
                is PropertyDescriptor -> SemanticTokenType.PROPERTY
                is VariableDescriptor -> SemanticTokenType.VARIABLE
                is ConstructorDescriptor -> when (target.constructedClass.kind) {
                    ClassKind.ENUM_ENTRY -> SemanticTokenType.ENUM_MEMBER
                    ClassKind.ANNOTATION_CLASS -> SemanticTokenType.TYPE // annotations look nicer this way
                    else -> SemanticTokenType.FUNCTION
                }
                is FunctionDescriptor -> SemanticTokenType.FUNCTION
                is ClassDescriptor -> when (target.kind) {
                    ClassKind.ENUM_ENTRY -> SemanticTokenType.ENUM_MEMBER
                    ClassKind.CLASS -> SemanticTokenType.CLASS
                    ClassKind.OBJECT -> SemanticTokenType.CLASS
                    ClassKind.INTERFACE -> SemanticTokenType.INTERFACE
                    ClassKind.ENUM_CLASS -> SemanticTokenType.ENUM
                    else -> SemanticTokenType.TYPE
                }
                else -> return null
            }
            val isConstant = (target as? VariableDescriptor)?.let { !it.isVar() || it.isConst() } ?: false
            val modifiers = if (isConstant) setOf(SemanticTokenModifier.READONLY) else setOf()

            SemanticToken(elementRange, tokenType, modifiers)
        }

        // Declarations (variables, types, functions, ...)

        is PsiNameIdentifierOwner -> {
            val tokenType = when (element) {
                is KtParameter -> SemanticTokenType.PARAMETER
                is KtProperty -> SemanticTokenType.PROPERTY
                is KtEnumEntry -> SemanticTokenType.ENUM_MEMBER
                is KtVariableDeclaration -> SemanticTokenType.VARIABLE
                is KtClassOrObject -> SemanticTokenType.CLASS
                is KtFunction -> SemanticTokenType.FUNCTION
                else -> return null
            }
            val identifierRange = element.nameIdentifier?.let { range(file.text, it.textRange) } ?: return null
            val modifiers = mutableSetOf(SemanticTokenModifier.DECLARATION)

            if (element is KtVariableDeclaration && (!element.isVar() || element.hasModifier(KtTokens.CONST_KEYWORD)) || element is KtParameter) {
                modifiers.add(SemanticTokenModifier.READONLY)
            }

            if (element is KtModifierListOwner) {
                if (element.hasModifier(KtTokens.ABSTRACT_KEYWORD)) {
                    modifiers.add(SemanticTokenModifier.ABSTRACT)
                }
            }

            SemanticToken(identifierRange, tokenType, modifiers)
        }

        // Literals and string interpolations

        is KtSimpleNameStringTemplateEntry, is KtBlockStringTemplateEntry ->
            SemanticToken(elementRange, SemanticTokenType.INTERPOLATION_ENTRY)
        is KtStringTemplateExpression -> SemanticToken(elementRange, SemanticTokenType.STRING)
        is PsiLiteralExpression -> {
            val tokenType = when (element.type) {
                PsiType.INT, PsiType.LONG, PsiType.DOUBLE -> SemanticTokenType.NUMBER
                PsiType.CHAR -> SemanticTokenType.STRING
                PsiType.BOOLEAN, PsiType.NULL -> SemanticTokenType.KEYWORD
                else -> return null
            }
            SemanticToken(elementRange, tokenType)
        }
        else -> null
    }
}
