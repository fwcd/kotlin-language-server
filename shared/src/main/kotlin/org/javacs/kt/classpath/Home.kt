package org.javacs.kt.classpath

import org.javacs.kt.util.userHome

internal val gradleHome = userHome.resolve(".gradle")
internal val mavenHome = userHome.resolve(".m2")
