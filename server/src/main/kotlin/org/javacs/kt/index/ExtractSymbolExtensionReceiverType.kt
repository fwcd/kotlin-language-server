package org.javacs.kt.index

import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.descriptors.impl.DeclarationDescriptorVisitorEmptyBodies
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe

object ExtractSymbolExtensionReceiverType : DeclarationDescriptorVisitorEmptyBodies<FqName?, Unit>() {
    private fun convert(desc: ReceiverParameterDescriptor): FqName? = desc.value.type.constructor.declarationDescriptor?.fqNameSafe

    override fun visitFunctionDescriptor(desc: FunctionDescriptor, nothing: Unit?) = desc.extensionReceiverParameter?.let(this::convert)

    override fun visitVariableDescriptor(desc: VariableDescriptor, nothing: Unit?) = desc.extensionReceiverParameter?.let(this::convert)
}
