/*
 * Source: https://github.com/JetBrains/kotlin-web-demo/blob/e6e1541b7235e54d84008124879ea2ae9118f0b3/kotlin.web.demo.backend/compilers/src/main/java/org/jetbrains/webdemo/kotlin/datastructures/CompletionVariant.java
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.javacs.kt.completion

import org.jetbrains.kotlin.descriptors.DeclarationDescriptor

data class CompletionVariant(
	val text: String,
	val displayText: String,
	val tail: String,
	val descriptor: DeclarationDescriptor?
)
