import org.javacs.kt.CompiledFile
import org.javacs.kt.docs.preOrderTraversal
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.resolve.BindingContext

fun documentSymbols(file: CompiledFile): Sequence<DeclarationDescriptor> {
    return file.file.preOrderTraversal()
            .mapNotNull {
                when (it) {
                    is KtNamedDeclaration -> file.context.get(BindingContext.DECLARATION_TO_DESCRIPTOR, it)
                    else -> null
                }
            }
}
