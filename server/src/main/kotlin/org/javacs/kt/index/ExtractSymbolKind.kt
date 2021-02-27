package org.javacs.kt.index

import org.jetbrains.kotlin.descriptors.*

object ExtractSymbolKind : DeclarationDescriptorVisitor<Symbol.Kind, Unit> {
    override fun visitPropertySetterDescriptor(desc: PropertySetterDescriptor, nothing: Unit?) = Symbol.Kind.FIELD

    override fun visitConstructorDescriptor(desc: ConstructorDescriptor, nothing: Unit?) = Symbol.Kind.CONSTRUCTOR

    override fun visitReceiverParameterDescriptor(desc: ReceiverParameterDescriptor, nothing: Unit?) = Symbol.Kind.VARIABLE

    override fun visitPackageViewDescriptor(desc: PackageViewDescriptor, nothing: Unit?) = Symbol.Kind.MODULE

    override fun visitFunctionDescriptor(desc: FunctionDescriptor, nothing: Unit?) = Symbol.Kind.FUNCTION

    override fun visitModuleDeclaration(desc: ModuleDescriptor, nothing: Unit?) = Symbol.Kind.MODULE

    override fun visitClassDescriptor(desc: ClassDescriptor, nothing: Unit?): Symbol.Kind = when (desc.kind) {
        ClassKind.INTERFACE -> Symbol.Kind.INTERFACE
        ClassKind.ENUM_CLASS -> Symbol.Kind.ENUM
        ClassKind.ENUM_ENTRY -> Symbol.Kind.ENUM_MEMBER
        else -> Symbol.Kind.CLASS
    }

    override fun visitPackageFragmentDescriptor(desc: PackageFragmentDescriptor, nothing: Unit?) = Symbol.Kind.MODULE

    override fun visitValueParameterDescriptor(desc: ValueParameterDescriptor, nothing: Unit?) = Symbol.Kind.VARIABLE

    override fun visitTypeParameterDescriptor(desc: TypeParameterDescriptor, nothing: Unit?) = Symbol.Kind.VARIABLE

    override fun visitScriptDescriptor(desc: ScriptDescriptor, nothing: Unit?) = Symbol.Kind.MODULE

    override fun visitTypeAliasDescriptor(desc: TypeAliasDescriptor, nothing: Unit?) = Symbol.Kind.VARIABLE

    override fun visitPropertyGetterDescriptor(desc: PropertyGetterDescriptor, nothing: Unit?) = Symbol.Kind.VARIABLE

    override fun visitVariableDescriptor(desc: VariableDescriptor, nothing: Unit?) = Symbol.Kind.VARIABLE

    override fun visitPropertyDescriptor(desc: PropertyDescriptor, nothing: Unit?) = Symbol.Kind.VARIABLE
}
