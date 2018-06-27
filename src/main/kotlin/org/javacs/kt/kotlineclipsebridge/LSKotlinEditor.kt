package org.javacs.kt.kotlineclipsebridge

import org.jetbrains.kotlin.ui.editors.KotlinEditor
import org.eclipse.jdt.internal.ui.javaeditor.JavaEditor
import org.jetbrains.kotlin.psi.KtFile
import org.eclipse.jdt.core.IJavaProject
import org.eclipse.jface.text.IDocument
import org.eclipse.core.resources.IFile

class LSKotlinEditor: KotlinEditor {
    override val javaEditor: JavaEditor
    override val parsedFile: KtFile?
    override val javaProject: IJavaProject?
    override val document: IDocument
    override val eclipseFile: IFile?
    override val isScript: Boolean
    override fun isEditable(): Boolean
}
