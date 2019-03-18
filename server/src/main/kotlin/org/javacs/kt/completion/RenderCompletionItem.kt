package org.javacs.kt.completion

import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.CompletionItemKind.*
import org.eclipse.lsp4j.CompletionItemKind.Function
import org.eclipse.lsp4j.InsertTextFormat.PlainText
import org.eclipse.lsp4j.InsertTextFormat.Snippet
import org.jetbrains.kotlin.descriptors.*
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

class RenderCompletionItem : DeclarationDescriptorVisitor<CompletionItem, Unit> {
    private val result = CompletionItem()

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

        result.kind = Property

        return result
    }

    override fun visitConstructorDescriptor(desc: ConstructorDescriptor, nothing: Unit?): CompletionItem {
        setDefaults(desc)

        result.kind = Constructor
        result.insertText = functionInsertText(desc)
        result.insertTextFormat = Snippet

        return result
    }

    override fun visitReceiverParameterDescriptor(desc: ReceiverParameterDescriptor, nothing: Unit?): CompletionItem {
        setDefaults(desc)

        result.kind = Variable

        return result
    }

    override fun visitPackageViewDescriptor(desc: PackageViewDescriptor, nothing: Unit?): CompletionItem {
        setDefaults(desc)

        result.kind = Module

        return result
    }

    override fun visitFunctionDescriptor(desc: FunctionDescriptor, nothing: Unit?): CompletionItem {
        setDefaults(desc)

        result.kind = Function
        result.insertText = functionInsertText(desc)
        result.insertTextFormat = Snippet

        return result
    }

    private fun functionInsertText(desc: FunctionDescriptor): String {
        val name = escape(desc.label()!!)

        return if (desc.valueParameters.isEmpty())
            "$name()"
        else
            "$name(\$0)"
    }

    override fun visitModuleDeclaration(desc: ModuleDescriptor, nothing: Unit?): CompletionItem {
        setDefaults(desc)

        result.kind = Module

        return result
    }

    override fun visitClassDescriptor(desc: ClassDescriptor, nothing: Unit?): CompletionItem {
        setDefaults(desc)

        result.kind = Class

        return result
    }

    override fun visitPackageFragmentDescriptor(desc: PackageFragmentDescriptor, nothing: Unit?): CompletionItem {
        setDefaults(desc)

        result.kind = Module

        return result
    }

    override fun visitValueParameterDescriptor(desc: ValueParameterDescriptor, nothing: Unit?): CompletionItem {
        setDefaults(desc)

        result.kind = Variable

        return result
    }

    override fun visitTypeParameterDescriptor(desc: TypeParameterDescriptor, nothing: Unit?): CompletionItem {
        setDefaults(desc)

        result.kind = Variable

        return result
    }

    override fun visitScriptDescriptor(desc: ScriptDescriptor, nothing: Unit?): CompletionItem {
        setDefaults(desc)

        result.kind = Module

        return result
    }

    override fun visitTypeAliasDescriptor(desc: TypeAliasDescriptor, nothing: Unit?): CompletionItem {
        setDefaults(desc)

        result.kind = Variable

        return result
    }

    override fun visitPropertyGetterDescriptor(desc: PropertyGetterDescriptor, nothing: Unit?): CompletionItem {
        setDefaults(desc)

        result.kind = Property

        return result
    }

    override fun visitVariableDescriptor(desc: VariableDescriptor, nothing: Unit?): CompletionItem {
        setDefaults(desc)

        result.kind = Variable

        return result
    }

    override fun visitPropertyDescriptor(desc: PropertyDescriptor, nothing: Unit?): CompletionItem {
        setDefaults(desc)

        result.kind = Property

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