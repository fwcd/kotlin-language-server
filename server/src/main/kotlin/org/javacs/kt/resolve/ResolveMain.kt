package org.javacs.kt.resolve

import org.jetbrains.kotlin.fileClasses.JvmFileClassUtil
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.javacs.kt.CompiledFile
import org.javacs.kt.LOG
import org.javacs.kt.position.range
import org.javacs.kt.util.partitionAroundLast
import com.intellij.openapi.util.TextRange


fun resolveMain(file: CompiledFile): Map<String,Any> {
    LOG.info("Resolving main function! yay")
    
    val parsedFile = file.parse.copy() as KtFile
    val mainFunction = findTopLevelMainFunction(parsedFile)
    if(null != mainFunction) {
        // the KtFiles name is weird. Full path. This causes the class to have full path in name as well. Correcting to top level only
        parsedFile.name = parsedFile.name.partitionAroundLast("/").second.substring(1)

        return mapOf("mainClass" to JvmFileClassUtil.getFileClassInfoNoResolve(parsedFile).facadeClassFqName.asString(),
                     "range" to range(file.content, mainFunction.second))
    }
    
    return emptyMap()
}

// only one allowed (so invalid syntax files will not show any main methods)
private fun findTopLevelMainFunction(file: KtFile): Pair<String?, TextRange>? = file.declarations.find {
    // TODO: any validations on arguments
    it is KtNamedFunction && "main" == it.name
}?.let {
    // TODO: any better things to return?
    Pair(it.name, it.textRangeInParent)
}
