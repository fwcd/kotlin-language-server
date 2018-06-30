/*
 * Source: https://github.com/JetBrains/kotlin-web-demo/blob/master/versions/1.2.50/src/main/java/org/jetbrains/webdemo/kotlin/impl/JetPsiFactoryUtil.kt
 * Copyright 2000-2018 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.javacs.kt.util

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.CharsetToolkit
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.impl.PsiFileFactoryImpl
import com.intellij.testFramework.LightVirtualFile
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.psi.KtFile

object JetPsiFactoryUtil {
	fun createFile(project: Project, rawName: String, text: String): KtFile {
		var name = rawName
		if (!name.endsWith(".kt")) {
			name += ".kt"
		}
		val virtualFile = LightVirtualFile(name, KotlinLanguage.INSTANCE, text)
		virtualFile.charset = CharsetToolkit.UTF8_CHARSET
		return (PsiFileFactory.getInstance(project) as PsiFileFactoryImpl).trySetupPsiForFile(virtualFile, KotlinLanguage.INSTANCE, true, false) as KtFile
	}
}
