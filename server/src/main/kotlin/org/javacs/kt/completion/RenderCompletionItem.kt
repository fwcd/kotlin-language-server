package org.javacs.kt.completion

import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.CompletionItemKind
import org.eclipse.lsp4j.InsertTextFormat.PlainText
import org.eclipse.lsp4j.InsertTextFormat.Snippet
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.builtins.isFunctionType
import org.jetbrains.kotlin.renderer.ClassifierNamePolicy
import org.jetbrains.kotlin.renderer.DescriptorRenderer
import org.jetbrains.kotlin.renderer.ParameterNameRenderingPolicy
import org.jetbrains.kotlin.types.ErrorUtils
import org.jetbrains.kotlin.types.UnresolvedType

val DECL_RENDERER = DescriptorRenderer.withOptions {
    withDefinedIn = false
    modifiers = emptySet()
    classifierNamePolicy = ClassifierNamePolicy.SHORT
    parameterNameRenderingPolicy = ParameterNameRenderingPolicy.ONLY_NON_SYNTHESIZED
    typeNormalizer = {
        when (it) {
            is UnresolvedType ->  ErrorUtils.createErrorTypeWithCustomDebugName(it.presentableName)
            else -> it
        }
    }
}

private val GOOD_IDENTIFIER = Regex("[a-zA-Z]\\w*")

class RenderCompletionItem(val snippetsEnabled: Boolean) : DeclarationDescriptorVisitor<CompletionItem, Unit> {
    private val result = CompletionItem()

    private val functionInsertFormat
        get() = if (snippetsEnabled) Snippet else PlainText

    private fun escape(id: String): String =
        if (id.matches(GOOD_IDENTIFIER)) id
        else "`$id`"

    private fun setDefaults(declaration: DeclarationDescriptor) {
        result.label = declaration.label()
        result.filterText = declaration.label()
        result.insertText = escape(declaration.label()!!)
        result.insertTextFormat = PlainText
        result.detail = DECL_RENDERER.render(declaration)
    }

    override fun visitPropertySetterDescriptor(desc: PropertySetterDescriptor, nothing: Unit?): CompletionItem {
        setDefaults(desc)

        result.kind = CompletionItemKind.Field

        return result
    }

    override fun visitConstructorDescriptor(desc: ConstructorDescriptor, nothing: Unit?): CompletionItem {
        setDefaults(desc)

        result.kind = CompletionItemKind.Constructor
        result.insertText = functionInsertText(desc)
        result.insertTextFormat = functionInsertFormat

        return result
    }

    override fun visitReceiverParameterDescriptor(desc: ReceiverParameterDescriptor, nothing: Unit?): CompletionItem {
        setDefaults(desc)

        result.kind = CompletionItemKind.Variable

        return result
    }

    override fun visitPackageViewDescriptor(desc: PackageViewDescriptor, nothing: Unit?): CompletionItem {
        setDefaults(desc)

        result.kind = CompletionItemKind.Module

        return result
    }

    override fun visitFunctionDescriptor(desc: FunctionDescriptor, nothing: Unit?): CompletionItem {
        setDefaults(desc)

        result.kind = CompletionItemKind.Function
        result.insertText = functionInsertText(desc)
        result.insertTextFormat = functionInsertFormat

        return result
    }

    private fun functionInsertText(desc: FunctionDescriptor): String {
        val name = escape(desc.label()!!)

        return if (snippetsEnabled) {
            val parameters = desc.valueParameters
            val hasTrailingLambda = parameters.lastOrNull()?.type?.isFunctionType ?: false

            if (hasTrailingLambda) {
                val parenthesizedParams = parameters.dropLast(1).ifEmpty { null }?.let { "(${valueParametersSnippet(it)})" } ?: ""
                "$name$parenthesizedParams { \${${parameters.size}:${parameters.last().name}} }"
            } else {
                "$name(${valueParametersSnippet(parameters)})"
            }
        } else {
            name
        }
    }

    private fun valueParametersSnippet(parameters: List<ValueParameterDescriptor>) = parameters
        .asSequence()
        .filterNot { it.declaresDefaultValue() }
        .mapIndexed { index, vpd -> "\${${index + 1}:${vpd.name}}" }
        .joinToString()

    override fun visitModuleDeclaration(desc: ModuleDescriptor, nothing: Unit?): CompletionItem {
        setDefaults(desc)

        result.kind = CompletionItemKind.Module

        return result
    }

    override fun visitClassDescriptor(desc: ClassDescriptor, nothing: Unit?): CompletionItem {
        setDefaults(desc)

        result.kind = when (desc.kind) {
            ClassKind.INTERFACE -> CompletionItemKind.Interface
            ClassKind.ENUM_CLASS -> CompletionItemKind.Enum
            ClassKind.ENUM_ENTRY -> CompletionItemKind.EnumMember
            else -> CompletionItemKind.Class
        }

        return result
    }

    override fun visitPackageFragmentDescriptor(desc: PackageFragmentDescriptor, nothing: Unit?): CompletionItem {
        setDefaults(desc)

        result.kind = CompletionItemKind.Module

        return result
    }

    override fun visitValueParameterDescriptor(desc: ValueParameterDescriptor, nothing: Unit?): CompletionItem {
        setDefaults(desc)

        result.kind = CompletionItemKind.Variable

        return result
    }

    override fun visitTypeParameterDescriptor(desc: TypeParameterDescriptor, nothing: Unit?): CompletionItem {
        setDefaults(desc)

        result.kind = CompletionItemKind.Variable

        return result
    }

    override fun visitScriptDescriptor(desc: ScriptDescriptor, nothing: Unit?): CompletionItem {
        setDefaults(desc)

        result.kind = CompletionItemKind.Module

        return result
    }

    override fun visitTypeAliasDescriptor(desc: TypeAliasDescriptor, nothing: Unit?): CompletionItem {
        setDefaults(desc)

        result.kind = CompletionItemKind.Variable

        return result
    }

    override fun visitPropertyGetterDescriptor(desc: PropertyGetterDescriptor, nothing: Unit?): CompletionItem {
        setDefaults(desc)

        result.kind = CompletionItemKind.Field

        return result
    }

    override fun visitVariableDescriptor(desc: VariableDescriptor, nothing: Unit?): CompletionItem {
        setDefaults(desc)

        result.kind = CompletionItemKind.Variable

        return result
    }

    override fun visitPropertyDescriptor(desc: PropertyDescriptor, nothing: Unit?): CompletionItem {
        setDefaults(desc)

        result.kind = CompletionItemKind.Field

        return result
    }
}

private fun DeclarationDescriptor.label(): String? {
    return when {
        this is ConstructorDescriptor -> this.containingDeclaration.name.identifier
        this.name.isSpecial -> null
        else -> this.name.identifier
    }
}
