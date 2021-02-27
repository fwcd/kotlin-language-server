package org.javacs.kt.index

import org.jetbrains.kotlin.descriptors.*

object ExtractSymbolVisibility : DeclarationDescriptorVisitor<Symbol.Visibility, Unit> {
    private fun convert(visibility: DescriptorVisibility): Symbol.Visibility = when (visibility.delegate) {
        Visibilities.PrivateToThis -> Symbol.Visibility.PRIAVTE_TO_THIS
        Visibilities.Private -> Symbol.Visibility.PRIVATE
        Visibilities.Internal -> Symbol.Visibility.INTERNAL
        Visibilities.Protected -> Symbol.Visibility.PROTECTED
        Visibilities.Public -> Symbol.Visibility.PUBLIC
        else -> Symbol.Visibility.UNKNOWN
    }

    override fun visitPropertySetterDescriptor(desc: PropertySetterDescriptor, nothing: Unit?) = convert(desc.visibility)

    override fun visitConstructorDescriptor(desc: ConstructorDescriptor, nothing: Unit?) = convert(desc.visibility)

    override fun visitReceiverParameterDescriptor(desc: ReceiverParameterDescriptor, nothing: Unit?) = convert(desc.visibility)

    override fun visitPackageViewDescriptor(desc: PackageViewDescriptor, nothing: Unit?) = Symbol.Visibility.PUBLIC

    override fun visitFunctionDescriptor(desc: FunctionDescriptor, nothing: Unit?) = convert(desc.visibility)

    override fun visitModuleDeclaration(desc: ModuleDescriptor, nothing: Unit?) = Symbol.Visibility.PUBLIC

    override fun visitClassDescriptor(desc: ClassDescriptor, nothing: Unit?) = convert(desc.visibility)

    override fun visitPackageFragmentDescriptor(desc: PackageFragmentDescriptor, nothing: Unit?) = Symbol.Visibility.PUBLIC

    override fun visitValueParameterDescriptor(desc: ValueParameterDescriptor, nothing: Unit?) = convert(desc.visibility)

    override fun visitTypeParameterDescriptor(desc: TypeParameterDescriptor, nothing: Unit?) = Symbol.Visibility.PUBLIC

    override fun visitScriptDescriptor(desc: ScriptDescriptor, nothing: Unit?) = convert(desc.visibility)

    override fun visitTypeAliasDescriptor(desc: TypeAliasDescriptor, nothing: Unit?) = convert(desc.visibility)

    override fun visitPropertyGetterDescriptor(desc: PropertyGetterDescriptor, nothing: Unit?) = convert(desc.visibility)

    override fun visitVariableDescriptor(desc: VariableDescriptor, nothing: Unit?) = convert(desc.visibility)

    override fun visitPropertyDescriptor(desc: PropertyDescriptor, nothing: Unit?) = convert(desc.visibility)
}
