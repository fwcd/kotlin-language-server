package org.javacs.kt.resolve

import org.jetbrains.kotlin.fileClasses.JvmFileClassUtil
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.javacs.kt.CompiledFile
import org.javacs.kt.position.range
import org.javacs.kt.util.partitionAroundLast
import com.intellij.openapi.util.TextRange

fun resolveMain(file: CompiledFile): Map<String,Any> {
    val parsedFile = file.parse.copy() as KtFile

    findTopLevelMainFunction(parsedFile)?.let { mainFunction ->
        // the KtFiles name is weird. Full path. This causes the class to have full path in name as well. Correcting to top level only
        parsedFile.name = parsedFile.name.partitionAroundLast("/").second.substring(1)

        return mapOf(
            "mainClass" to JvmFileClassUtil.getFileClassInfoNoResolve(parsedFile).facadeClassFqName.asString(),
            "range" to range(file.content, mainFunction.second)
        )
    }

    findCompanionObjectMain(parsedFile)?.let { companionMain ->
        return mapOf(
            "mainClass" to (companionMain.first ?: ""),
            "range" to range(file.content, companionMain.second)
        )
    }

    return emptyMap()
}

// only one main method allowed top level in a file (so invalid syntax files will not show any main methods)
private fun findTopLevelMainFunction(file: KtFile): Pair<String?, TextRange>? = file.declarations.find {
    it is KtNamedFunction && "main" == it.name
}?.let {
    Pair(it.name, it.textRangeInParent)
}

// finds a top level class that contains a companion object with a main function inside
private fun findCompanionObjectMain(file: KtFile): Pair<String?, TextRange>? = file.declarations
    .flatMap { topLevelDeclaration ->
        if (topLevelDeclaration is KtClass) {
            topLevelDeclaration.companionObjects
        } else {
            emptyList<KtObjectDeclaration>()
        }
    }
    .flatMap { companionObject ->
        companionObject.body?.children?.toList() ?: emptyList()
    }
    .mapNotNull { companionObjectInternal ->
        companionObjectInternal.takeIf {
            companionObjectInternal is KtNamedFunction
            && "main" == companionObjectInternal.name
            && companionObjectInternal.text.startsWith("@JvmStatic")
        }
    }
    .firstOrNull()?.let {
        // a little ugly, but because of success of the above, we know that "it" has 4 layers of parent objects (child of companion object body, companion object body, companion object, outer class)
        Pair((it.parent.parent.parent.parent as KtClass).fqName?.toString(), it.textRange)
    }
